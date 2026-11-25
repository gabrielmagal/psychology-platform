package br.com.mindhaven.review;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Avaliação de alguém por alguém, a respeito de um contexto (uma sessão, um pedido, uma
 * entrega...). O módulo só conhece identificadores: quem pode avaliar o quê é decidido por
 * quem o chama.
 */
@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
public class ReviewEntity extends BaseEntity {
    /** Quem foi avaliado. */
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    /** Quem avaliou. Nunca é exibido publicamente. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** A que a avaliação se refere, por exemplo {@code SESSION}. */
    @Column(name = "context_type", nullable = false, length = 40)
    private String contextType;

    @Column(name = "context_id", nullable = false)
    private UUID contextId;

    @Column(name = "rating", nullable = false)
    private int rating;

    /** "Você recomendaria?" — pode ficar sem resposta. */
    @Column(name = "recommend")
    private Boolean recommend;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_status", nullable = false, length = 20)
    private CommentStatus commentStatus = CommentStatus.PRIVATE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * O comentário só aparece em público depois de duas decisões: o autor autoriza e a
     * moderação aprova. A nota conta para a média em qualquer situação.
     */
    public enum CommentStatus {
        /** O autor não autorizou a exibição: só o avaliado e a administração leem. */
        PRIVATE,
        /** O autor autorizou; aguarda a moderação. */
        PENDING,
        PUBLISHED,
        REJECTED
    }
}
