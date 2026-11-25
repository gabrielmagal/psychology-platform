package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import jakarta.persistence.Convert;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anamnese do paciente — uma por paciente. Todo o conteúdo é registro clínico: a auditoria
 * registra que houve alteração, não o texto.
 */
@Entity
@Table(name = "patient_record")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditListener.class)
public class PatientRecordEntity extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    @Column(name = "professional_id")
    private UUID professionalId;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "complaint_history", columnDefinition = "TEXT")
    private String complaintHistory;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "family_history", columnDefinition = "TEXT")
    private String familyHistory;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "health_history", columnDefinition = "TEXT")
    private String healthHistory;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "diagnostic_hypothesis", columnDefinition = "TEXT")
    private String diagnosticHypothesis;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "therapeutic_goals", columnDefinition = "TEXT")
    private String therapeuticGoals;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
