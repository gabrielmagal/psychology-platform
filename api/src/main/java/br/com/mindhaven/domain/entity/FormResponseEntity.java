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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Aplicação de um instrumento a um paciente, com a cópia do modelo e as respostas. */
@Entity
@Table(name = "form_response")
@Getter
@Setter
@NoArgsConstructor
public class FormResponseEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "template_code", nullable = false, length = 60)
    private String templateCode;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "repeat_days")
    private Integer repeatDays;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "answered_by", length = 20)
    private String answeredBy;

    @Convert(converter = EncryptedString.class)
    @Column(name = "answers", columnDefinition = "TEXT")
    private String answers;

    @Column(name = "score")
    private Integer score;

    @Column(name = "alert", nullable = false)
    private boolean alert;
}
