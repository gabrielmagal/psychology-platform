package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Registro do diário de humor do paciente (1 = muito mal, 5 = muito bem). */
@Entity
@Table(name = "mood_entry")
@Getter
@Setter
@NoArgsConstructor
public class MoodEntryEntity extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "mood", nullable = false)
    private int mood;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Horas dormidas na noite anterior. */
    @Column(name = "sleep_hours")
    private java.math.BigDecimal sleepHours;

    /** Qualidade do sono, de 1 (péssimo) a 5 (ótimo). */
    @Column(name = "sleep_quality")
    private Integer sleepQuality;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
