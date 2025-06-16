package br.com.psicologia.repository.model;

import br.com.psicologia.interceptor.AuditListener;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "annotation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditListener.class)
public class AnnotationEntity extends BaseEntity {

    @Column(name = "main_feeling", nullable = false)
    private String mainFeeling;

    @Column(name = "significant_events", nullable = false)
    private String significantEvents;

    @Column(name = "current_phase", nullable = false)
    private String currentPhase;

    @Column(name = "dominant_thought", nullable = false, columnDefinition = "TEXT")
    private String dominantThought;

    @Column(name = "intervention", nullable = false)
    private String intervention;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private SessionEntity session;
}