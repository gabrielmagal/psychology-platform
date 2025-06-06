package br.com.psicologia.repository.model;

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
public class AnnotationEntity  extends BaseEntity {
    private String mainFeeling;
    private String significantEvents;
    private String currentPhase;

    @Lob
    private String dominantThought;
    private String intervention;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private SessionEntity session;
}