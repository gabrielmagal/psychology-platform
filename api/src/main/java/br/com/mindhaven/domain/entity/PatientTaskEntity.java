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

/** Tarefa combinada com o paciente para fazer entre as sessões. */
@Entity
@Table(name = "patient_task")
@Getter
@Setter
@NoArgsConstructor
public class PatientTaskEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Convert(converter = EncryptedString.class)
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    /** O que o paciente quis contar ao concluir: como foi, o que foi difícil. */
    @Convert(converter = EncryptedString.class)
    @Column(name = "patient_note", columnDefinition = "TEXT")
    private String patientNote;
}
