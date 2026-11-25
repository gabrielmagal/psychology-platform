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

/** Período em que o profissional não atende (férias, compromisso, feriado). */
@Entity
@Table(name = "schedule_block")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleBlockEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "reason")
    private String reason;
}
