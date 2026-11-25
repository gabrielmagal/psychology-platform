package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.ClinicalDocumentEntity;
import br.com.mindhaven.domain.entity.DocumentTemplateEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Documentos que o psicólogo escreve, com a estrutura da Resolução CFP 06/2019: atestado,
 * relatório, laudo e parecer (mais o encaminhamento, de uso corrente). O texto fica como
 * rascunho até a emissão; emitido, vira um PDF numerado e não muda mais.
 *
 * <p>Só o autor lê e escreve o rascunho. O paciente não vê estes documentos no aplicativo:
 * a entrega é feita pelo profissional, na devolutiva.
 */
@ApplicationScoped
@Transactional
public class ClinicalDocumentService {
    /** Uma seção do documento: a chave gravada, o título impresso e a orientação da tela. */
    public record Section(String key, String label, String hint) {
    }

    public record Kind(String code, String label, String pdfTitle, String description, List<Section> sections, boolean closingNote) {
    }

    private static final Section IDENTIFICATION = new Section("identification", "Identificação",
            "Quem solicitou o documento e para qual finalidade. Autor e pessoa atendida entram sozinhos no cabeçalho.");
    private static final Section DEMAND = new Section("demand", "Descrição da demanda",
            "O que motivou o atendimento ou a avaliação e quem a solicitou.");
    private static final Section PROCEDURE = new Section("procedure", "Procedimento",
            "Recursos técnicos usados: entrevistas, observações, instrumentos, número de encontros e período.");
    private static final Section ANALYSIS = new Section("analysis", "Análise",
            "Exposição descritiva e fundamentada do que foi observado, sem afirmações que os dados não sustentem.");
    private static final Section CONCLUSION = new Section("conclusion", "Conclusão",
            "Síntese, encaminhamentos e orientações. No laudo, o diagnóstico ou a hipótese, quando couber.");
    private static final Section REFERENCES = new Section("references", "Referências",
            "Fontes científicas e instrumentos citados.");

    public static final List<Kind> KINDS = List.of(
            new Kind("ATESTADO", "Atestado psicológico", "ATESTADO PSICOLÓGICO",
                    "Certifica uma situação, um estado ou um funcionamento psicológico, para justificar faltas, aptidão ou afastamento.",
                    List.of(new Section("body", "Texto do atestado",
                            "A finalidade, o que se atesta e, se for o caso, o período de afastamento. O CID só com autorização do paciente.")), false),
            new Kind("RELATORIO", "Relatório psicológico", "RELATÓRIO PSICOLÓGICO",
                    "Comunica o atendimento realizado, sem finalidade diagnóstica.",
                    List.of(IDENTIFICATION, DEMAND, PROCEDURE, ANALYSIS, CONCLUSION), true),
            new Kind("LAUDO", "Laudo psicológico", "LAUDO PSICOLÓGICO",
                    "Resultado de uma avaliação psicológica, com finalidade de subsidiar decisões.",
                    List.of(IDENTIFICATION, DEMAND, PROCEDURE, ANALYSIS, CONCLUSION, REFERENCES), true),
            new Kind("PARECER", "Parecer psicológico", "PARECER PSICOLÓGICO",
                    "Resposta fundamentada a uma questão específica do campo da psicologia.",
                    List.of(IDENTIFICATION, DEMAND, ANALYSIS, CONCLUSION, REFERENCES), true),
            new Kind("ENCAMINHAMENTO", "Encaminhamento", "ENCAMINHAMENTO",
                    "Indica o paciente a outro profissional ou serviço.",
                    List.of(new Section("body", "Texto do encaminhamento",
                            "A quem se destina, o motivo e o que se espera do atendimento indicado.")), false));

    private static final String CLOSING = "Este documento não pode ser utilizado para fins diferentes do apontado no item de "
            + "identificação, possui caráter sigiloso e trata-se de documento extrajudicial. O(a) psicólogo(a) não se "
            + "responsabiliza pelo uso dado a ele após a sua entrega em entrevista devolutiva.";
    private static final int MAX_SECTION = 30_000;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PatientAccess access;

    @Inject
    DocumentService documents;

    /** Os tipos de documento, com as seções de cada um, e os modelos de texto do profissional. */
    public Map<String, Object> catalog(String tenant, UserEntity user) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        List<Map<String, Object>> kinds = new ArrayList<>();
        for (Kind kind : KINDS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", kind.code());
            row.put("label", kind.label());
            row.put("description", kind.description());
            List<Map<String, Object>> sections = new ArrayList<>();
            for (Section section : kind.sections()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("key", section.key());
                s.put("label", section.label());
                s.put("hint", section.hint());
                sections.add(s);
            }
            row.put("sections", sections);
            kinds.add(row);
        }
        List<Map<String, Object>> templates = new ArrayList<>();
        for (DocumentTemplateEntity t : em.createQuery("select t from DocumentTemplateEntity t where t.ownerId = :me "
                + "order by lower(t.title)", DocumentTemplateEntity.class).setParameter("me", user.getId()).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("kind", t.getKind());
            row.put("title", t.getTitle());
            row.put("sections", sectionsOf(t.getSections()));
            templates.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kinds", kinds);
        result.put("templates", templates);
        return result;
    }

    /** Rascunhos e documentos emitidos do profissional para o paciente. */
    public List<Map<String, Object>> list(String tenant, UserEntity user, UUID patientId) {
        requireProfessional(user);
        patient(tenant, user, patientId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClinicalDocumentEntity d : em.createQuery("select d from ClinicalDocumentEntity d where d.patientId = :p "
                        + "and d.professionalId = :me order by d.updatedAt desc", ClinicalDocumentEntity.class)
                .setParameter("p", patientId).setParameter("me", user.getId()).getResultList()) {
            rows.add(describe(d));
        }
        return rows;
    }

    /** Cria ou altera um rascunho. Documento emitido não é mais alterado. */
    public Map<String, Object> save(String tenant, UserEntity user, UUID id, UUID patientId, String kindCode, String title,
                                    Map<String, Object> sections) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        ClinicalDocumentEntity document;
        if (id == null) {
            patient(tenant, user, patientId);
            document = new ClinicalDocumentEntity();
            document.setPatientId(patientId);
            document.setProfessionalId(user.getId());
            document.setKind(kind(kindCode).code());
            document.setCreatedAt(LocalDateTime.now());
        } else {
            document = owned(user, id);
            if (document.getIssuedDocumentId() != null) {
                throw BusinessException.conflict("Este documento já foi emitido e não pode ser alterado. Escreva um novo.");
            }
        }
        Kind kind = kind(document.getKind());
        document.setTitle(title == null || title.isBlank() ? kind.label() : limit(title.trim(), 200));
        document.setSections(clean(kind, sections).encode());
        document.setUpdatedAt(LocalDateTime.now());
        return describe(em.merge(document));
    }

    /** Emite o PDF numerado. A partir daqui o texto fica travado. */
    public Map<String, Object> issue(String tenant, UserEntity user, UUID id) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        ClinicalDocumentEntity document = owned(user, id);
        if (document.getIssuedDocumentId() != null) return describe(document);
        Kind kind = kind(document.getKind());
        Map<String, Object> sections = sectionsOf(document.getSections());
        List<String> paragraphs = new ArrayList<>();
        boolean single = kind.sections().size() == 1;
        for (Section section : kind.sections()) {
            String text = sections.get(section.key()) == null ? "" : sections.get(section.key()).toString().trim();
            if (text.isEmpty()) {
                if (single || !"references".equals(section.key())) {
                    throw BusinessException.invalid("Preencha \"" + section.label() + "\" antes de emitir.");
                }
                continue;
            }
            if (!single) paragraphs.add(DocumentService.HEADING + section.label());
            for (String block : text.split("\\n\\s*\\n")) {
                if (!block.isBlank()) paragraphs.add(block.trim());
            }
        }
        if (kind.closingNote()) paragraphs.add(CLOSING);
        UserEntity patient = em.find(UserEntity.class, document.getPatientId());
        UUID issued = documents.issueWritten(tenant, user, patient, kind.code(), kind.pdfTitle(), document.getId(), paragraphs);
        document.setIssuedDocumentId(issued);
        document.setUpdatedAt(LocalDateTime.now());
        return describe(document);
    }

    public void remove(String tenant, UserEntity user, UUID id) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        ClinicalDocumentEntity document = owned(user, id);
        if (document.getIssuedDocumentId() != null) {
            throw BusinessException.conflict("Documento emitido faz parte do prontuário e não é apagado.");
        }
        em.remove(document);
    }

    // ------------------------------------------------------------------ modelos

    public Map<String, Object> saveTemplate(String tenant, UserEntity user, String kindCode, String title, Map<String, Object> sections) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        Kind kind = kind(kindCode);
        if (title == null || title.isBlank()) throw BusinessException.invalid("Dê um nome ao modelo.");
        DocumentTemplateEntity template = new DocumentTemplateEntity();
        template.setOwnerId(user.getId());
        template.setKind(kind.code());
        template.setTitle(limit(title.trim(), 160));
        template.setSections(clean(kind, sections).encode());
        template.setCreatedAt(LocalDateTime.now());
        em.persist(template);
        return Map.of("id", template.getId());
    }

    public void removeTemplate(String tenant, UserEntity user, UUID id) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        DocumentTemplateEntity template = id == null ? null : em.find(DocumentTemplateEntity.class, id);
        if (template == null) return;
        if (!template.getOwnerId().equals(user.getId())) throw new SecurityException("Este modelo é de outro profissional.");
        em.remove(template);
    }

    // --------------------------------------------------------------- auxiliares

    private Map<String, Object> describe(ClinicalDocumentEntity d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("kind", d.getKind());
        row.put("kindLabel", kind(d.getKind()).label());
        row.put("title", d.getTitle());
        row.put("sections", sectionsOf(d.getSections()));
        row.put("issued", d.getIssuedDocumentId() != null);
        row.put("issuedDocumentId", d.getIssuedDocumentId());
        row.put("updatedAt", d.getUpdatedAt().toString());
        return row;
    }

    private static Map<String, Object> sectionsOf(String json) {
        return json == null || json.isBlank() ? new LinkedHashMap<>() : new JsonObject(json).getMap();
    }

    /** Só as seções que o tipo de documento tem, com limite de tamanho. */
    private static JsonObject clean(Kind kind, Map<String, Object> sections) {
        JsonObject result = new JsonObject();
        for (Section section : kind.sections()) {
            Object value = sections == null ? null : sections.get(section.key());
            String text = value == null ? "" : value.toString().trim();
            if (text.length() > MAX_SECTION) {
                throw BusinessException.invalid("\"" + section.label() + "\" pode ter até " + MAX_SECTION + " caracteres.");
            }
            if (!text.isEmpty()) result.put(section.key(), text);
        }
        return result;
    }

    private static Kind kind(String code) {
        return KINDS.stream().filter(k -> k.code().equals(code)).findFirst()
                .orElseThrow(() -> BusinessException.invalid("Tipo de documento desconhecido."));
    }

    private ClinicalDocumentEntity owned(UserEntity user, UUID id) {
        ClinicalDocumentEntity document = id == null ? null : em.find(ClinicalDocumentEntity.class, id);
        if (document == null) throw BusinessException.invalid("Documento não encontrado.");
        if (!document.getProfessionalId().equals(user.getId())) throw new SecurityException("Este documento é de outro profissional.");
        return document;
    }

    private UserEntity patient(String tenant, UserEntity user, UUID patientId) {
        dao.defineSchema(tenant);
        UserEntity patient = patientId == null ? null : em.find(UserEntity.class, patientId);
        if (patient == null || patient.getUserType() != UserType.PACIENTE) throw BusinessException.invalid("Paciente não encontrado.");
        access.requireAssociated(tenant, user, patient);
        return patient;
    }

    private static void requireProfessional(UserEntity user) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("Atestados, relatórios, laudos e pareceres são escritos pelo psicólogo responsável.");
        }
    }

    private static String limit(String text, int max) {
        if (text.length() > max) throw BusinessException.invalid("O título pode ter até " + max + " caracteres.");
        return text;
    }
}
