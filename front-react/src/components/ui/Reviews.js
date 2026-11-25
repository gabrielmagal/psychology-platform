import React from "react";
import { FiThumbsUp } from "react-icons/fi";
import { RatingStars } from "./Rating";

const MONTHS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro",
    "outubro", "novembro", "dezembro"];

/** "2026-11-01" ou "2026-11-24T10:00" → "novembro de 2026". Só o mês: dia exato ajuda a identificar o autor. */
function monthLabel(value) {
    const match = /^(\d{4})-(\d{2})/.exec(value || "");
    return match ? `${MONTHS[Number(match[2]) - 1]} de ${match[1]}` : "";
}

/**
 * Lista de avaliações. Cada item: `{ rating, recommend, comment, month | createdAt }`, mais o
 * que quem usa quiser. `renderMeta` acrescenta informação ao cabeçalho (autor, situação) e
 * `renderActions`, botões (aprovar, recusar). Sem eles a lista é anônima e só de leitura.
 */
export default function ReviewList({ items, emptyText = "Nenhum comentário por aqui ainda.", author = "Paciente",
                                       renderMeta, renderActions }) {
    if (!items || items.length === 0) return <p className="ui-muted">{emptyText}</p>;
    return (
        <div>
            {items.map((item, index) => (
                <article key={item.id || index} className="ui-review">
                    <div className="ui-review-head">
                        <RatingStars value={item.rating} size={15} />
                        <span>{author} · {monthLabel(item.month || item.createdAt)}</span>
                        {item.recommend === true && (
                            <span className="ui-badge ui-badge--success"><FiThumbsUp aria-hidden="true" /> Recomenda</span>
                        )}
                        {renderMeta && renderMeta(item)}
                    </div>
                    {item.comment
                        ? <p className="ui-review-text">{item.comment}</p>
                        : <p className="ui-muted">Sem comentário.</p>}
                    {renderActions && <div className="ui-review-actions">{renderActions(item)}</div>}
                </article>
            ))}
        </div>
    );
}
