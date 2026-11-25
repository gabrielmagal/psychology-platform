package br.com.mindhaven.profile;

import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil público de apresentação.
 *
 * <p>Módulo reaproveitável: guarda e valida o perfil de um identificador de usuário, sem
 * conhecer o cadastro nem os papéis do sistema. Quem pode ter perfil e quem pode vê-lo é
 * decidido por quem o chama.
 */
@ApplicationScoped
@Transactional
public class PublicProfileService {
    private static final int MAX_ITEMS = 12;
    private static final int MAX_ITEM_LENGTH = 60;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    /** O perfil de alguém, ou {@code null} se nunca foi preenchido. */
    public PublicProfileEntity find(String tenant, UUID userId) {
        dao.defineSchema(tenant);
        return em.find(PublicProfileEntity.class, userId);
    }

    public Map<UUID, PublicProfileEntity> findAll(String tenant, Collection<UUID> userIds) {
        Map<UUID, PublicProfileEntity> result = new HashMap<>();
        if (userIds.isEmpty()) return result;
        dao.defineSchema(tenant);
        for (PublicProfileEntity p : em.createQuery("select p from PublicProfileEntity p where p.userId in :ids",
                PublicProfileEntity.class).setParameter("ids", userIds).getResultList()) {
            result.put(p.getUserId(), p);
        }
        return result;
    }

    public PublicProfileEntity save(String tenant, UUID userId, Map<String, Object> body) {
        dao.defineSchema(tenant);
        PublicProfileEntity profile = em.find(PublicProfileEntity.class, userId);
        if (profile == null) {
            profile = new PublicProfileEntity();
            profile.setUserId(userId);
        }
        profile.setHeadline(text(body.get("headline"), 160, "A apresentação curta"));
        profile.setBio(text(body.get("bio"), 4000, "O texto sobre você"));
        profile.setApproaches(items(body.get("approaches")));
        profile.setSpecialties(items(body.get("specialties")));
        profile.setAudiences(items(body.get("audiences")));
        profile.setLanguages(items(body.get("languages")));
        profile.setEducation(text(body.get("education"), 2000, "A formação"));
        profile.setCity(text(body.get("city"), 120, "A cidade"));

        Integer since = integer(body.get("practicingSince"));
        if (since != null && (since < 1950 || since > Year.now().getValue())) {
            throw BusinessException.invalid("Informe um ano de início de atuação válido.");
        }
        profile.setPracticingSince(since);

        BigDecimal price = decimal(body.get("sessionPrice"));
        if (price != null && price.signum() < 0) throw BusinessException.invalid("O valor da sessão não pode ser negativo.");
        profile.setSessionPrice(price);

        Integer minutes = integer(body.get("sessionMinutes"));
        if (minutes != null && (minutes < 10 || minutes > 240)) {
            throw BusinessException.invalid("A duração da sessão fica entre 10 e 240 minutos.");
        }
        profile.setSessionMinutes(minutes);

        profile.setAttendsOnline(Boolean.TRUE.equals(body.get("attendsOnline")));
        profile.setAttendsInPerson(Boolean.TRUE.equals(body.get("attendsInPerson")));
        profile.setAcceptingClients(!Boolean.FALSE.equals(body.get("acceptingClients")));
        profile.setShowReviews(Boolean.TRUE.equals(body.get("showReviews")));

        boolean publish = Boolean.TRUE.equals(body.get("published"));
        if (publish && (profile.getBio() == null || profile.getBio().length() < 40)) {
            throw BusinessException.invalid("Para publicar o perfil, escreva um texto sobre você com pelo menos 40 caracteres.");
        }
        profile.setPublished(publish);
        profile.setUpdatedAt(LocalDateTime.now());
        return em.merge(profile);
    }

    /** O perfil como mapa, com as listas já separadas. Aceita {@code null} (perfil nunca preenchido). */
    public static Map<String, Object> describe(PublicProfileEntity p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("headline", p == null ? null : p.getHeadline());
        map.put("bio", p == null ? null : p.getBio());
        map.put("approaches", split(p == null ? null : p.getApproaches()));
        map.put("specialties", split(p == null ? null : p.getSpecialties()));
        map.put("audiences", split(p == null ? null : p.getAudiences()));
        map.put("languages", split(p == null ? null : p.getLanguages()));
        map.put("education", p == null ? null : p.getEducation());
        map.put("practicingSince", p == null ? null : p.getPracticingSince());
        map.put("sessionPrice", p == null ? null : p.getSessionPrice());
        map.put("sessionMinutes", p == null ? null : p.getSessionMinutes());
        map.put("attendsOnline", p != null && p.isAttendsOnline());
        map.put("attendsInPerson", p != null && p.isAttendsInPerson());
        map.put("city", p == null ? null : p.getCity());
        map.put("acceptingClients", p == null || p.isAcceptingClients());
        map.put("showReviews", p != null && p.isShowReviews());
        map.put("published", p != null && p.isPublished());
        return map;
    }

    public static List<String> split(String stored) {
        List<String> result = new ArrayList<>();
        if (stored == null) return result;
        for (String line : stored.split("\n")) {
            if (!line.isBlank()) result.add(line.trim());
        }
        return result;
    }

    private static String items(Object value) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (value instanceof Collection<?> list) {
            for (Object item : list) {
                if (item == null) continue;
                String text = item.toString().replace('\n', ' ').trim();
                if (text.isEmpty()) continue;
                if (text.length() > MAX_ITEM_LENGTH) {
                    throw BusinessException.invalid("Cada item das listas pode ter até " + MAX_ITEM_LENGTH + " caracteres.");
                }
                unique.add(text);
            }
        }
        if (unique.size() > MAX_ITEMS) throw BusinessException.invalid("Cada lista pode ter até " + MAX_ITEMS + " itens.");
        return unique.isEmpty() ? null : String.join("\n", unique);
    }

    private static String text(Object value, int max, String what) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        if (text.length() > max) throw BusinessException.invalid(what + " pode ter até " + max + " caracteres.");
        return text;
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        return number == null ? null : number.intValue();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return new BigDecimal(value.toString().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw BusinessException.invalid("Número inválido: " + value);
        }
    }
}
