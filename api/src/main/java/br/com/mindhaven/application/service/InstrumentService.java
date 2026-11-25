package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.FormResponseEntity;
import br.com.mindhaven.domain.entity.FormTemplateEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Instrumentos clínicos: escalas prontas e formulários do profissional, a aplicação ao
 * paciente (respondida por ele no aplicativo ou pelo profissional na sessão) e a pontuação.
 *
 * <p>O paciente responde, mas não vê pontuação nem faixa: resultado de rastreio é lido com o
 * profissional, não sozinho numa tela. Item marcado como de alerta (o 9 do PHQ-9, sobre
 * pensar em se ferir) com resposta acima de zero sinaliza a aplicação para o profissional.
 */
@ApplicationScoped
@Transactional
public class InstrumentService {
    private static final String CUSTOM = "custom:";
    private static final int MAX_ITEMS = 60;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PatientAccess access;

    // ------------------------------------------------------------------ modelos

    /** Os instrumentos prontos e os que o profissional criou. */
    public List<Map<String, Object>> templates(String tenant, UserEntity user) {
        requireStaff(user);
        dao.defineSchema(tenant);
        List<Map<String, Object>> rows = new ArrayList<>();
        Instruments.builtIn().forEach((code, definition) -> rows.add(describeTemplate(code, definition, true, null)));
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select t from FormTemplateEntity t" + (onlyMine ? " where t.ownerId = :me" : "")
                + " order by lower(t.title)", FormTemplateEntity.class);
        if (onlyMine) query.setParameter("me", user.getId());
        for (FormTemplateEntity t : query.getResultList()) {
            rows.add(describeTemplate(CUSTOM + t.getId(), new JsonObject(t.getDefinition()), false, t.getId()));
        }
        return rows;
    }

    /** Cria ou altera um instrumento do profissional. Aplicações já feitas não mudam. */
    public Map<String, Object> saveTemplate(String tenant, UserEntity user, UUID id, Map<String, Object> body) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("O instrumento é criado pelo profissional que vai aplicá-lo.");
        }
        dao.defineSchema(tenant);
        JsonObject definition = normalize(new JsonObject(body == null ? Map.of() : body));
        FormTemplateEntity template = id == null ? new FormTemplateEntity() : ownedTemplate(user, id);
        template.setOwnerId(user.getId());
        template.setTitle(definition.getString("title"));
        template.setDefinition(definition.encode());
        if (template.getCreatedAt() == null) template.setCreatedAt(LocalDateTime.now());
        template = em.merge(template);
        return describeTemplate(CUSTOM + template.getId(), definition, false, template.getId());
    }

    public void removeTemplate(String tenant, UserEntity user, UUID id) {
        dao.defineSchema(tenant);
        em.remove(ownedTemplate(user, id));
    }

    // ---------------------------------------------------------------- aplicação

    /** Envia o instrumento ao paciente. Com {@code repeatDays}, cada resposta agenda a próxima. */
    public Map<String, Object> apply(String tenant, UserEntity user, UUID patientId, String templateCode, Integer repeatDays) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("O instrumento é aplicado pelo profissional que atende o paciente.");
        }
        dao.defineSchema(tenant);
        UserEntity patient = patientId == null ? null : em.find(UserEntity.class, patientId);
        if (patient == null || patient.getUserType() != UserType.PACIENTE) throw BusinessException.invalid("Paciente não encontrado.");
        access.requireAssociated(tenant, user, patient);
        if (repeatDays != null && (repeatDays < 1 || repeatDays > 365)) {
            throw BusinessException.invalid("A repetição vai de 1 a 365 dias.");
        }
        JsonObject definition = definitionOf(user, templateCode);
        long pending = em.createQuery("select count(r) from FormResponseEntity r where r.patientId = :p "
                        + "and r.professionalId = :me and r.templateCode = :c and r.answeredAt is null", Long.class)
                .setParameter("p", patientId).setParameter("me", user.getId()).setParameter("c", templateCode).getSingleResult();
        if (pending > 0) throw BusinessException.conflict("Este instrumento já está aguardando resposta deste paciente.");
        FormResponseEntity response = newResponse(patientId, user.getId(), templateCode, definition, null, repeatDays);
        em.persist(response);
        return describe(response, true, new HashMap<>());
    }

    /**
     * Grava as respostas. Responde o próprio paciente ou o profissional que aplicou (quando
     * o instrumento é preenchido na sessão). Soma os itens de escala e marca o alerta.
     */
    public Map<String, Object> answer(String tenant, UserEntity user, UUID responseId, Map<String, Object> answers) {
        dao.defineSchema(tenant);
        FormResponseEntity response = responseId == null ? null : em.find(FormResponseEntity.class, responseId);
        if (response == null) throw BusinessException.invalid("Aplicação não encontrada.");
        boolean patient = user.getUserType() == UserType.PACIENTE && response.getPatientId().equals(user.getId());
        boolean professional = user.getUserType() == UserType.PROFISSIONAL && response.getProfessionalId().equals(user.getId());
        if (!patient && !professional) throw new SecurityException("Você não pode responder este instrumento.");
        if (response.getAnsweredAt() != null) throw BusinessException.conflict("Este instrumento já foi respondido.");

        JsonObject definition = new JsonObject(response.getDefinition());
        Map<String, Object> given = answers == null ? Map.of() : answers;
        int maxValue = definition.getJsonArray("options", new JsonArray()).size() - 1;
        JsonArray alertItems = definition.getJsonArray("alertItems", new JsonArray());
        JsonObject stored = new JsonObject();
        int score = 0;
        boolean scored = false;
        boolean alert = false;
        JsonArray items = definition.getJsonArray("items");
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.getJsonObject(i);
            String id = item.getString("id");
            Object raw = given.get(id);
            String type = item.getString("type", Instruments.SCALE);
            if (Instruments.SCALE.equals(type)) {
                int value;
                try {
                    value = Integer.parseInt(String.valueOf(raw));
                } catch (NumberFormatException e) {
                    throw BusinessException.invalid("Responda todas as perguntas. Falta a " + (i + 1) + ".");
                }
                if (value < 0 || value > maxValue) throw BusinessException.invalid("Resposta inválida na pergunta " + (i + 1) + ".");
                stored.put(id, value);
                score += value;
                scored = true;
                if (value > 0 && alertItems.contains(id)) alert = true;
            } else if (Instruments.YESNO.equals(type)) {
                if (!(raw instanceof Boolean) && !"true".equals(String.valueOf(raw)) && !"false".equals(String.valueOf(raw))) {
                    throw BusinessException.invalid("Responda todas as perguntas. Falta a " + (i + 1) + ".");
                }
                stored.put(id, Boolean.parseBoolean(String.valueOf(raw)));
            } else {
                String text = raw == null ? "" : raw.toString().trim();
                if (text.length() > 4000) throw BusinessException.invalid("A resposta " + (i + 1) + " pode ter até 4000 caracteres.");
                stored.put(id, text);
            }
        }
        response.setAnswers(stored.encode());
        response.setScore(scored ? score : null);
        response.setAlert(alert);
        response.setAnsweredAt(LocalDateTime.now());
        response.setAnsweredBy(patient ? "PACIENTE" : "PROFISSIONAL");

        if (response.getRepeatDays() != null) {
            em.persist(newResponse(response.getPatientId(), response.getProfessionalId(), response.getTemplateCode(),
                    definition, LocalDate.now().plusDays(response.getRepeatDays()), response.getRepeatDays()));
        }
        return describe(response, !patient, new HashMap<>());
    }

    /**
     * As aplicações do paciente, da mais recente para a mais antiga. A equipe recebe
     * respostas, pontuação e faixa; o paciente, só o que tem a responder e o que já respondeu.
     */
    public List<Map<String, Object>> responses(String tenant, UserEntity user, UUID patientId) {
        dao.defineSchema(tenant);
        UUID target;
        if (user.getUserType() == UserType.PACIENTE) {
            target = user.getId();
        } else {
            if (patientId == null) throw BusinessException.invalid("Informe o paciente.");
            UserEntity patient = em.find(UserEntity.class, patientId);
            if (patient == null) throw BusinessException.invalid("Paciente não encontrado.");
            access.requireAssociated(tenant, user, patient);
            target = patientId;
        }
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select r from FormResponseEntity r where r.patientId = :p"
                + (onlyMine ? " and r.professionalId = :me" : "") + " order by r.requestedAt desc", FormResponseEntity.class)
                .setParameter("p", target);
        if (onlyMine) query.setParameter("me", user.getId());
        boolean staff = user.getUserType() != UserType.PACIENTE;
        Map<UUID, String> names = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FormResponseEntity r : query.getResultList()) rows.add(describe(r, staff, names));
        return rows;
    }

    /** O profissional desiste de uma aplicação ainda não respondida (e, com isso, da repetição). */
    public void cancel(String tenant, UserEntity user, UUID responseId) {
        dao.defineSchema(tenant);
        FormResponseEntity response = responseId == null ? null : em.find(FormResponseEntity.class, responseId);
        if (response == null) return;
        if (user.getUserType() != UserType.PROFISSIONAL || !response.getProfessionalId().equals(user.getId())) {
            throw new SecurityException("A aplicação é cancelada pelo profissional que a enviou.");
        }
        if (response.getAnsweredAt() != null) {
            throw BusinessException.conflict("Instrumento já respondido faz parte do prontuário e não é apagado.");
        }
        em.remove(response);
    }

    public long pendingFor(String tenant, UserEntity patient) {
        dao.defineSchema(tenant);
        return em.createQuery("select count(r) from FormResponseEntity r where r.patientId = :p and r.answeredAt is null "
                        + "and (r.dueDate is null or r.dueDate <= :today)", Long.class)
                .setParameter("p", patient.getId()).setParameter("today", LocalDate.now()).getSingleResult();
    }

    /** Respostas com item de alerta nos últimos 14 dias, para a tela inicial do profissional. */
    public List<Map<String, Object>> recentAlerts(String tenant, UserEntity user) {
        dao.defineSchema(tenant);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select r from FormResponseEntity r where r.alert = true and r.answeredAt >= :since"
                + (onlyMine ? " and r.professionalId = :me" : "") + " order by r.answeredAt desc", FormResponseEntity.class)
                .setParameter("since", LocalDateTime.now().minusDays(14));
        if (onlyMine) query.setParameter("me", user.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FormResponseEntity r : query.getResultList()) {
            UserEntity patient = em.find(UserEntity.class, r.getPatientId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patientId", r.getPatientId());
            row.put("patientName", fullName(patient));
            row.put("title", r.getTitle());
            row.put("answeredAt", r.getAnsweredAt().toString());
            rows.add(row);
        }
        return rows;
    }

    // --------------------------------------------------------------- auxiliares

    private static FormResponseEntity newResponse(UUID patientId, UUID professionalId, String code, JsonObject definition,
                                                  LocalDate dueDate, Integer repeatDays) {
        FormResponseEntity response = new FormResponseEntity();
        response.setPatientId(patientId);
        response.setProfessionalId(professionalId);
        response.setTemplateCode(code);
        response.setTitle(definition.getString("title"));
        response.setDefinition(definition.encode());
        response.setRequestedAt(LocalDateTime.now());
        response.setDueDate(dueDate);
        response.setRepeatDays(repeatDays);
        return response;
    }

    private JsonObject definitionOf(UserEntity user, String code) {
        if (code == null) throw BusinessException.invalid("Escolha o instrumento.");
        JsonObject builtIn = Instruments.builtIn().get(code);
        if (builtIn != null) return builtIn;
        if (code.startsWith(CUSTOM)) {
            try {
                return new JsonObject(ownedTemplate(user, UUID.fromString(code.substring(CUSTOM.length()))).getDefinition());
            } catch (IllegalArgumentException e) {
                throw BusinessException.invalid("Instrumento não encontrado.");
            }
        }
        throw BusinessException.invalid("Instrumento não encontrado.");
    }

    private FormTemplateEntity ownedTemplate(UserEntity user, UUID id) {
        FormTemplateEntity template = id == null ? null : em.find(FormTemplateEntity.class, id);
        if (template == null) throw BusinessException.invalid("Instrumento não encontrado.");
        if (!template.getOwnerId().equals(user.getId())) throw new SecurityException("Este instrumento é de outro profissional.");
        return template;
    }

    /** Confere e limpa a definição enviada pela tela; o que não é conhecido fica de fora. */
    private static JsonObject normalize(JsonObject raw) {
        String title = text(raw.getValue("title"));
        if (title == null) throw BusinessException.invalid("Dê um nome ao instrumento.");
        if (title.length() > 160) throw BusinessException.invalid("O nome pode ter até 160 caracteres.");
        JsonArray rawItems = raw.getValue("items") instanceof JsonArray array ? array : new JsonArray();
        if (rawItems.isEmpty()) throw BusinessException.invalid("Inclua pelo menos uma pergunta.");
        if (rawItems.size() > MAX_ITEMS) throw BusinessException.invalid("O instrumento pode ter até " + MAX_ITEMS + " perguntas.");
        JsonArray items = new JsonArray();
        boolean hasScale = false;
        for (int i = 0; i < rawItems.size(); i++) {
            JsonObject item = rawItems.getJsonObject(i);
            String question = text(item.getValue("text"));
            if (question == null) throw BusinessException.invalid("A pergunta " + (i + 1) + " está em branco.");
            String type = item.getString("type", Instruments.SCALE);
            if (!Instruments.TYPES.contains(type)) throw BusinessException.invalid("Tipo de pergunta inválido.");
            hasScale |= Instruments.SCALE.equals(type);
            items.add(new JsonObject().put("id", String.valueOf(i + 1)).put("text", question).put("type", type));
        }
        JsonArray options = new JsonArray();
        if (hasScale) {
            JsonArray rawOptions = raw.getValue("options") instanceof JsonArray array ? array : new JsonArray();
            for (int i = 0; i < rawOptions.size(); i++) {
                Object option = rawOptions.getValue(i);
                String label = text(option instanceof JsonObject object ? object.getValue("label") : option);
                if (label != null) options.add(new JsonObject().put("value", options.size()).put("label", label));
            }
            if (options.size() < 2 || options.size() > 11) {
                throw BusinessException.invalid("As perguntas de escala precisam de 2 a 11 opções de resposta.");
            }
        }
        long scaleItems = items.stream().filter(i -> Instruments.SCALE.equals(((JsonObject) i).getString("type"))).count();
        return new JsonObject()
                .put("title", title)
                .put("description", text(raw.getValue("description")))
                .put("instructions", text(raw.getValue("instructions")))
                .put("options", options)
                .put("items", items)
                .put("bands", new JsonArray())
                .put("alertItems", new JsonArray())
                .put("maxScore", hasScale ? (int) scaleItems * (options.size() - 1) : null);
    }

    private static Map<String, Object> describeTemplate(String code, JsonObject definition, boolean builtIn, UUID id) {
        Map<String, Object> row = new LinkedHashMap<>(plain(definition));
        row.put("code", code);
        row.put("id", id);
        row.put("builtIn", builtIn);
        return row;
    }

    private Map<String, Object> describe(FormResponseEntity r, boolean staff, Map<UUID, String> names) {
        JsonObject definition = new JsonObject(r.getDefinition());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("templateCode", r.getTemplateCode());
        row.put("title", r.getTitle());
        row.put("requestedAt", r.getRequestedAt().toString());
        row.put("dueDate", r.getDueDate() == null ? null : r.getDueDate().toString());
        row.put("repeatDays", r.getRepeatDays());
        row.put("answered", r.getAnsweredAt() != null);
        row.put("answeredAt", r.getAnsweredAt() == null ? null : r.getAnsweredAt().toString());
        row.put("available", r.getAnsweredAt() == null && (r.getDueDate() == null || !r.getDueDate().isAfter(LocalDate.now())));
        row.put("professionalName", names.computeIfAbsent(r.getProfessionalId(),
                id -> fullName(em.find(UserEntity.class, id))));
        // Quem ainda vai responder precisa das perguntas; a equipe, sempre.
        if (staff || r.getAnsweredAt() == null) row.put("definition", plain(definition));
        if (staff) {
            row.put("answeredBy", r.getAnsweredBy());
            row.put("answers", r.getAnswers() == null ? null : new JsonObject(r.getAnswers()).getMap());
            row.put("score", r.getScore());
            row.put("maxScore", definition.getValue("maxScore"));
            row.put("band", Instruments.band(definition, r.getScore()));
            row.put("alert", r.isAlert());
        }
        return row;
    }

    /** Mapas e listas comuns, sem os tipos do Vert.x: é o que vai para a resposta. */
    private static Map<String, Object> plain(JsonObject json) {
        return new JsonObject(json.encode()).getMap();
    }

    private static void requireStaff(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita ao profissional.");
    }

    private static String fullName(UserEntity user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
