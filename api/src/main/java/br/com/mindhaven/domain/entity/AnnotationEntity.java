package br.com.mindhaven.domain.entity;

import core.audit.AuditListener;
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
    // Formato antigo, só de leitura: o registro da sessão hoje é o de SessionRecord. Esta
    // entidade existe para que anotações gravadas antes continuem aparecendo e sejam
    // convertidas no primeiro salvamento. Não há mais rota nem tela que crie uma.

    // O conteúdo desta entidade é registro clínico do paciente por inteiro. A auditoria
    // registra que houve alteração, não o que foi escrito.
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "main_feeling", columnDefinition = "TEXT")
    private String mainFeeling;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "significant_events", columnDefinition = "TEXT")
    private String significantEvents;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "current_phase", columnDefinition = "TEXT")
    private String currentPhase;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "dominant_thought", columnDefinition = "TEXT")
    private String dominantThought;

    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "intervention", columnDefinition = "TEXT")
    private String intervention;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "session_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private SessionEntity session;
}