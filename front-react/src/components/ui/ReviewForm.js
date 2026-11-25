import React, { useState } from "react";
import { RatingInput } from "./Rating";
import { AutoTextarea } from "./SectionedNote";

/**
 * Formulário de avaliação: nota, "você recomendaria?", comentário e a autorização para
 * exibir o comentário. Devolve `{ rating, recommend, comment, allowPublicComment }` em
 * `onSubmit`. Quem usa decide o que perguntar pelos textos.
 */
export default function ReviewForm({
    title = "Como foi?",
    recommendQuestion = "Você recomendaria?",
    commentLabel = "Comentário (opcional)",
    commentPlaceholder = "",
    consentLabel = "Autorizo a exibição deste comentário, sem o meu nome",
    consentHint,
    privacyNote,
    submitLabel = "Enviar avaliação",
    maxLength = 1000,
    busy = false,
    onSubmit,
}) {
    const [rating, setRating] = useState(0);
    const [recommend, setRecommend] = useState(null);
    const [comment, setComment] = useState("");
    const [allowPublicComment, setAllowPublicComment] = useState(false);
    const hasComment = comment.trim().length > 0;

    const submit = (event) => {
        event.preventDefault();
        if (!rating) return;
        onSubmit({ rating, recommend, comment: comment.trim(), allowPublicComment: hasComment && allowPublicComment });
    };

    return (
        <form className="ui-form" onSubmit={submit}>
            <div>
                <strong style={{ display: "block", marginBottom: 6 }}>{title}</strong>
                <RatingInput value={rating} onChange={setRating} disabled={busy} />
            </div>

            <div>
                <span className="ui-note-label" style={{ display: "block", marginBottom: 6 }}>{recommendQuestion}</span>
                <span className="ui-choice">
                    <button type="button" aria-pressed={recommend === true}
                            onClick={() => setRecommend(recommend === true ? null : true)}>Sim</button>
                    <button type="button" aria-pressed={recommend === false}
                            onClick={() => setRecommend(recommend === false ? null : false)}>Não</button>
                </span>
            </div>

            <div className="ui-note-section">
                <label className="ui-note-label" htmlFor="review-comment">{commentLabel}</label>
                <AutoTextarea id="review-comment" value={comment} onChange={setComment} maxLength={maxLength}
                              placeholder={commentPlaceholder} />
                {privacyNote && <span className="ui-note-hint">{privacyNote}</span>}
            </div>

            {hasComment && (
                <label className="ui-check">
                    <input type="checkbox" checked={allowPublicComment}
                           onChange={(e) => setAllowPublicComment(e.target.checked)} />
                    <span>{consentLabel}{consentHint && <small>{consentHint}</small>}</span>
                </label>
            )}

            <div>
                <button type="submit" className="ui-btn ui-btn--primary" disabled={busy || !rating}>
                    {busy ? "Enviando…" : submitLabel}
                </button>
                {!rating && <span className="ui-muted" style={{ marginLeft: 12 }}>Escolha de 1 a 5 estrelas.</span>}
            </div>
        </form>
    );
}
