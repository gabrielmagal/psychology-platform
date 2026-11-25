package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.PatientAttachmentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda do prontuário: os anexos (laudos, encaminhamentos) e o prazo de retenção.
 *
 * <p>O Conselho Federal de Psicologia exige a guarda do registro por no mínimo 5 anos. O
 * sistema impede a exclusão antes do prazo e lista, para o administrador, o que já pode ser
 * descartado — o descarte em si é sempre uma decisão de uma pessoa.
 */
@ApplicationScoped
@Transactional
public class RecordsService {
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain");

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    ClinicService clinic;

    @ConfigProperty(name = "clinic.record-retention-years", defaultValue = "5")
    int retentionYears;

    @ConfigProperty(name = "clinic.attachment-max-mb", defaultValue = "10")
    int maxMegabytes;

    // ------------------------------------------------------------------ anexos

    public List<Map<String, Object>> attachments(String tenant, UserEntity user, UUID patientId) {
        clinic.patientRecord(tenant, user, patientId); // confere que o profissional atende o paciente
        var query = em.createQuery("select a from PatientAttachmentEntity a where a.patientId = :p"
                        + (user.getUserType() == UserType.PROFISSIONAL ? " and a.professionalId = :me" : "")
                        + " order by a.uploadedAt desc", PatientAttachmentEntity.class)
                .setParameter("p", patientId);
        if (user.getUserType() == UserType.PROFISSIONAL) query.setParameter("me", user.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PatientAttachmentEntity a : query.getResultList()) rows.add(row(a));
        return rows;
    }

    public Map<String, Object> attach(String tenant, UserEntity user, UUID patientId, String fileName,
                                      String contentType, String base64, String description) {
        clinic.patientRecord(tenant, user, patientId);
        if (fileName == null || fileName.isBlank() || base64 == null || base64.isBlank()) {
            throw BusinessException.invalid("Escolha um arquivo para anexar.");
        }
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        if (!ALLOWED_TYPES.contains(type)) {
            throw BusinessException.invalid("Tipo de arquivo não aceito. Use PDF, imagem (PNG ou JPG), Word ou texto.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("O arquivo chegou corrompido. Tente anexar de novo.");
        }
        if (bytes.length > maxMegabytes * 1024L * 1024L) {
            throw BusinessException.invalid("O arquivo passa do limite de " + maxMegabytes + " MB.");
        }

        PatientAttachmentEntity attachment = new PatientAttachmentEntity();
        attachment.setPatientId(patientId);
        attachment.setProfessionalId(user.getId());
        // Só o nome, sem caminho: o que o navegador mandar não vira parte da chave no armazenamento.
        attachment.setFileName(fileName.replaceAll(".*[/\\\\]", "").trim());
        attachment.setContentType(type);
        attachment.setSizeBytes((long) bytes.length);
        attachment.setDescription(description == null || description.isBlank() ? null : description.trim());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setContent(bytes);
        em.persist(attachment);
        return row(attachment);
    }

    public record Download(String fileName, String contentType, byte[] content) {
    }

    public Download download(String tenant, UserEntity user, UUID attachmentId) {
        PatientAttachmentEntity attachment = owned(tenant, user, attachmentId);
        return new Download(attachment.getFileName(), attachment.getContentType(), attachment.getContent());
    }

    public void removeAttachment(String tenant, UserEntity user, UUID attachmentId) {
        PatientAttachmentEntity attachment = owned(tenant, user, attachmentId);
        em.remove(attachment);
    }

    private PatientAttachmentEntity owned(String tenant, UserEntity user, UUID attachmentId) {
        dao.defineSchema(tenant);
        PatientAttachmentEntity attachment = em.find(PatientAttachmentEntity.class, attachmentId);
        if (attachment == null) throw BusinessException.invalid("Anexo não encontrado.");
        boolean allowed = user.getUserType() == UserType.ADMINISTRADOR
                || (user.getUserType() == UserType.PROFISSIONAL && user.getId().equals(attachment.getProfessionalId()));
        if (!allowed) throw new SecurityException("Você não tem acesso a este anexo.");
        return attachment;
    }

    private static Map<String, Object> row(PatientAttachmentEntity a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("fileName", a.getFileName());
        row.put("contentType", a.getContentType());
        row.put("sizeBytes", a.getSizeBytes());
        row.put("description", a.getDescription());
        row.put("uploadedAt", a.getUploadedAt().toString());
        return row;
    }

    // ---------------------------------------------------------------- retenção

    /** Data a partir da qual o prontuário do paciente pode ser descartado; null se não há registro clínico. */
    public LocalDate disposableFrom(String tenant, UUID patientId) {
        dao.defineSchema(tenant);
        LocalDate lastSession = em.createQuery("select max(s.dateSession) from SessionEntity s "
                        + "where s.sessionPackage.patient.id = :p", LocalDate.class)
                .setParameter("p", patientId).getSingleResult();
        LocalDateTime lastRecord = em.createQuery("select max(r.updatedAt) from PatientRecordEntity r "
                        + "where r.patient.id = :p", LocalDateTime.class)
                .setParameter("p", patientId).getSingleResult();
        LocalDate last = lastSession;
        if (lastRecord != null && (last == null || lastRecord.toLocalDate().isAfter(last))) last = lastRecord.toLocalDate();
        return last == null ? null : last.plusYears(retentionYears);
    }

    /** Impede apagar um paciente cujo prontuário ainda está no prazo de guarda. */
    public void requireDisposable(String tenant, UUID patientId) {
        LocalDate from = disposableFrom(tenant, patientId);
        if (from != null && from.isAfter(LocalDate.now())) {
            throw BusinessException.conflict("Este paciente tem prontuário e ele precisa ser guardado até "
                    + from.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (" + retentionYears
                    + " anos após o último atendimento). Até lá o cadastro não pode ser excluído.");
        }
    }

    /** Para o administrador: pacientes com registro clínico e a situação do prazo de guarda. */
    public Map<String, Object> retentionReport(String tenant, UserEntity user) {
        if (user.getUserType() != UserType.ADMINISTRADOR) {
            throw new SecurityException("Relatório restrito ao administrador.");
        }
        dao.defineSchema(tenant);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserEntity patient : em.createQuery("select u from UserEntity u where u.userType = :t order by u.firstName",
                UserEntity.class).setParameter("t", UserType.PACIENTE).getResultList()) {
            LocalDate from = disposableFrom(tenant, patient.getId());
            if (from == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patientId", patient.getId());
            row.put("patientName", (patient.getFirstName() + " " + patient.getLastName()).trim());
            row.put("lastActivity", from.minusYears(retentionYears).toString());
            row.put("keepUntil", from.toString());
            row.put("disposable", !from.isAfter(LocalDate.now()));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retentionYears", retentionYears);
        result.put("patients", rows);
        return result;
    }
}
