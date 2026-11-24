package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "annotation")
@BatchSize(size = 32)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
public class AnnotationEntity extends BaseEntity {

    // O conteúdo desta entidade é registro clínico do paciente por inteiro. A auditoria
    // registra que houve alteração, não o que foi escrito.
    @ISensitive
    @Column(name = "main_feeling", nullable = false)
    private String mainFeeling;

    @ISensitive
    @Column(name = "significant_events", nullable = false)
    private String significantEvents;

    @ISensitive
    @Column(name = "current_phase", nullable = false)
    private String currentPhase;

    @ISensitive
    @Column(name = "dominant_thought", nullable = false, columnDefinition = "TEXT")
    private String dominantThought;

    @ISensitive
    @Column(name = "intervention", nullable = false)
    private String intervention;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "session_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private SessionEntity session;
}