package br.com.mindhaven.domain.entity;

import core.crypto.EncryptedString;
import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plano de segurança para momentos de crise, no modelo de Stanley e Brown: seis passos que
 * o paciente segue em ordem, do que ele faz sozinho até acionar ajuda profissional. É
 * construído com o paciente e fica com ele; por isso o paciente o lê pelo aplicativo.
 */
@Entity
@Table(name = "safety_plan")
@Getter
@Setter
@NoArgsConstructor
public class SafetyPlanEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    /** 1. Sinais de alerta de que uma crise pode estar começando. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "warning_signs", columnDefinition = "TEXT")
    private String warningSigns;

    /** 2. O que a pessoa pode fazer sozinha para se acalmar. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "coping", columnDefinition = "TEXT")
    private String coping;

    /** 3. Pessoas e lugares que ajudam a distrair. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "distractions", columnDefinition = "TEXT")
    private String distractions;

    /** 4. Pessoas a quem pedir ajuda. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "support_people", columnDefinition = "TEXT")
    private String supportPeople;

    /** 5. Profissionais e serviços para acionar na crise. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "professionals", columnDefinition = "TEXT")
    private String professionals;

    /** 6. Como deixar o ambiente seguro. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "safe_environment", columnDefinition = "TEXT")
    private String safeEnvironment;

    /** O que vale a pena para a pessoa: razões para seguir. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "reasons_to_live", columnDefinition = "TEXT")
    private String reasonsToLive;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
