package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Termo que o paciente lê e aceita pelo aplicativo. O texto aceito fica guardado junto com o
 * aceite: se o modelo mudar depois, o que a pessoa assinou continua igual.
 */
@Entity
@Table(name = "patient_term")
@Getter
@Setter
@NoArgsConstructor
public class PatientTermEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** Nome completo digitado por quem aceitou (o paciente ou o responsável legal). */
    @Column(name = "signed_name", length = 200)
    private String signedName;
}
