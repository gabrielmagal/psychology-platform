package br.com.psicologia.repository.model;

import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_rating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionRatingEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private SessionEntity session;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    @Min(1)
    @Max(5)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}