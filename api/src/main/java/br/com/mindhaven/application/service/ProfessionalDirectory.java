package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.profile.PublicProfileEntity;
import br.com.mindhaven.profile.PublicProfileService;
import br.com.mindhaven.review.ReviewService;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Guia de profissionais da clínica: junta o cadastro (nome, foto, registro), o perfil público
 * e as avaliações. É a parte que conhece o sistema; os módulos {@code profile} e
 * {@code review} não conhecem.
 *
 * <p>Regras:
 * <ul>
 *   <li>todo usuário logado vê os perfis <b>publicados</b>; o dono e o administrador veem também
 *       o rascunho;</li>
 *   <li>nota e comentários só aparecem para os outros se o profissional escolheu mostrá-los —
 *       o Conselho Federal de Psicologia não recomenda o uso de depoimentos, então o padrão é
 *       a avaliação servir de retorno interno;</li>
 *   <li>a lista sai em ordem alfabética, nunca por nota: não há ranking entre colegas.</li>
 * </ul>
 */
@ApplicationScoped
@Transactional
public class ProfessionalDirectory {
    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PublicProfileService profiles;

    @Inject
    ReviewService reviews;

    @Inject
    PatientAccess access;

    public List<Map<String, Object>> list(String tenant, UserEntity viewer, String search) {
        dao.defineSchema(tenant);
        List<UserEntity> professionals = em.createQuery("select u from UserEntity u where u.userType = :type "
                        + "order by lower(u.firstName), lower(u.lastName)", UserEntity.class)
                .setParameter("type", UserType.PROFISSIONAL).getResultList();
        List<UUID> ids = professionals.stream().map(UserEntity::getId).toList();
        Map<UUID, PublicProfileEntity> found = profiles.findAll(tenant, ids);
        Map<UUID, Map<String, Object>> summaries = reviews.summaries(tenant, ids);
        Map<UUID, String> photos = access.photos(tenant, ids);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> cards = new ArrayList<>();
        for (UserEntity professional : professionals) {
            PublicProfileEntity profile = found.get(professional.getId());
            if (!canSee(viewer, professional, profile)) continue;
            Map<String, Object> card = card(professional, profile, photos.get(professional.getId()));
            if (!term.isEmpty() && !matches(card, term)) continue;
            if (showsReviews(viewer, professional, profile)) card.put("reviews", summaries.get(professional.getId()));
            cards.add(card);
        }
        return cards;
    }

    public Map<String, Object> view(String tenant, UserEntity viewer, UUID professionalId) {
        dao.defineSchema(tenant);
        UserEntity professional = em.find(UserEntity.class, professionalId);
        PublicProfileEntity profile = professional == null ? null : profiles.find(tenant, professionalId);
        if (professional == null || professional.getUserType() != UserType.PROFISSIONAL
                || !canSee(viewer, professional, profile)) {
            // A mesma resposta para "não existe" e "não publicado": não revela rascunhos.
            throw new BusinessException(404, "Perfil não encontrado.");
        }
        Map<String, Object> result = card(professional, profile, access.photo(tenant, professionalId));
        result.putAll(PublicProfileService.describe(profile));
        result.put("mine", professional.getId().equals(viewer.getId()));
        if (showsReviews(viewer, professional, profile)) {
            result.put("reviews", reviews.summary(tenant, professionalId));
            result.put("comments", reviews.publishedComments(tenant, professionalId, 30));
        }
        return result;
    }

    /** O perfil do próprio profissional, para edição. */
    public Map<String, Object> mine(String tenant, UserEntity viewer) {
        requireProfessional(viewer);
        Map<String, Object> result = card(viewer, profiles.find(tenant, viewer.getId()), access.photo(tenant, viewer.getId()));
        result.putAll(PublicProfileService.describe(profiles.find(tenant, viewer.getId())));
        return result;
    }

    public Map<String, Object> saveMine(String tenant, UserEntity viewer, Map<String, Object> body) {
        requireProfessional(viewer);
        profiles.save(tenant, viewer.getId(), body == null ? Map.of() : body);
        return mine(tenant, viewer);
    }

    /**
     * Avaliações recebidas. O profissional vê as suas sem saber de quem são: o retorno precisa
     * ser franco. O administrador vê as de qualquer um, com o autor.
     */
    public Map<String, Object> received(String tenant, UserEntity viewer, UUID professionalId) {
        boolean admin = viewer.getUserType() == UserType.ADMINISTRADOR;
        UUID subject = professionalId == null ? viewer.getId() : professionalId;
        if (!admin && (viewer.getUserType() != UserType.PROFISSIONAL || !subject.equals(viewer.getId()))) {
            throw new SecurityException("Você só pode ver as avaliações que recebeu.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", reviews.summary(tenant, subject));
        result.put("items", withNames(reviews.received(tenant, subject, admin, 100)));
        return result;
    }

    public List<Map<String, Object>> moderationQueue(String tenant, UserEntity viewer) {
        requireAdmin(viewer);
        return withNames(reviews.pendingModeration(tenant));
    }

    public void moderate(String tenant, UserEntity viewer, UUID reviewId, boolean publish) {
        requireAdmin(viewer);
        reviews.moderate(tenant, reviewId, publish);
    }

    // --------------------------------------------------------------- auxiliares

    private List<Map<String, Object>> withNames(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            row.put("subjectName", name(em.find(UserEntity.class, (UUID) row.get("subjectId"))));
            if (row.containsKey("authorId")) {
                row.put("authorName", name(em.find(UserEntity.class, (UUID) row.get("authorId"))));
            }
        }
        return rows;
    }

    private static Map<String, Object> card(UserEntity professional, PublicProfileEntity profile, String photo) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", professional.getId());
        card.put("name", name(professional));
        card.put("registration", professional.getCrp());
        card.put("photo", photo == null || photo.isEmpty() ? null : photo);
        card.put("headline", profile == null ? null : profile.getHeadline());
        card.put("specialties", PublicProfileService.split(profile == null ? null : profile.getSpecialties()));
        card.put("approaches", PublicProfileService.split(profile == null ? null : profile.getApproaches()));
        card.put("attendsOnline", profile != null && profile.isAttendsOnline());
        card.put("attendsInPerson", profile != null && profile.isAttendsInPerson());
        card.put("city", profile == null ? null : profile.getCity());
        card.put("acceptingClients", profile == null || profile.isAcceptingClients());
        card.put("published", profile != null && profile.isPublished());
        return card;
    }

    private static boolean matches(Map<String, Object> card, String term) {
        StringBuilder text = new StringBuilder();
        for (String key : List.of("name", "headline", "specialties", "approaches", "city")) {
            text.append(card.get(key)).append(' ');
        }
        return text.toString().toLowerCase(Locale.ROOT).contains(term);
    }

    private static boolean canSee(UserEntity viewer, UserEntity professional, PublicProfileEntity profile) {
        return (profile != null && profile.isPublished()) || isOwnerOrAdmin(viewer, professional);
    }

    private static boolean showsReviews(UserEntity viewer, UserEntity professional, PublicProfileEntity profile) {
        return (profile != null && profile.isShowReviews()) || isOwnerOrAdmin(viewer, professional);
    }

    private static boolean isOwnerOrAdmin(UserEntity viewer, UserEntity professional) {
        return viewer.getUserType() == UserType.ADMINISTRADOR || professional.getId().equals(viewer.getId());
    }

    private static String name(UserEntity user) {
        if (user == null) return null;
        return ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }

    private static void requireProfessional(UserEntity user) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("O perfil público é do profissional que atende.");
        }
    }

    private static void requireAdmin(UserEntity user) {
        if (user.getUserType() != UserType.ADMINISTRADOR) {
            throw new SecurityException("A moderação dos comentários é feita pelo administrador.");
        }
    }
}
