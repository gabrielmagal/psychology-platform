import React from "react";
import { FiStar } from "react-icons/fi";

const FIVE = [1, 2, 3, 4, 5];

function decimal(value) {
    return Number(value).toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** Estrelas só para leitura. Aceita nota quebrada (4,3 preenche 86% da largura). */
export function RatingStars({ value = 0, size = 18, label }) {
    const safe = Math.max(0, Math.min(5, Number(value) || 0));
    return (
        <span className="ui-stars" style={{ "--star-size": `${size}px` }} role="img"
              aria-label={label || `${decimal(safe)} de 5`}>
            {FIVE.map((n) => <FiStar key={n} aria-hidden="true" />)}
            <span className="ui-stars-fill" style={{ width: `${(safe / 5) * 100}%` }} aria-hidden="true">
                {FIVE.map((n) => <FiStar key={n} />)}
            </span>
        </span>
    );
}

/** Estrelas para dar nota. Funciona com teclado: cada estrela é um botão. */
export function RatingInput({ value, onChange, disabled = false, labels }) {
    const names = labels || ["Muito ruim", "Ruim", "Regular", "Boa", "Excelente"];
    return (
        <span className="ui-stars-input" role="radiogroup" aria-label="Nota de 1 a 5">
            {FIVE.map((n) => (
                <button key={n} type="button" role="radio" aria-checked={value === n} disabled={disabled}
                        aria-label={`${n} de 5: ${names[n - 1]}`} title={names[n - 1]}
                        data-on={value >= n} onClick={() => onChange(n)}>
                    <FiStar aria-hidden="true" />
                </button>
            ))}
        </span>
    );
}

/** Nota em uma linha, para cartões e listas: ★ 4,8 (12). Sem avaliações, não mostra nada. */
export function RatingInline({ summary }) {
    if (!summary || !summary.count) return null;
    return (
        <span className="ui-rating-inline">
            <RatingStars value={summary.average} size={15} />
            <strong>{decimal(summary.average)}</strong>
            <span>({summary.count})</span>
        </span>
    );
}

/**
 * Resumo das avaliações: média, total, quantos recomendam e a distribuição das notas.
 * Espera `{ count, average, distribution: [n1..n5], recommendPercent, recommendAnswers }`.
 */
export function RatingSummary({ summary, emptyText = "Ainda não há avaliações." }) {
    if (!summary || !summary.count) return <p className="ui-muted">{emptyText}</p>;
    const distribution = summary.distribution || [0, 0, 0, 0, 0];
    return (
        <div className="ui-rating-summary">
            <div className="ui-rating-score">
                <strong>{decimal(summary.average)}</strong>
                <RatingStars value={summary.average} />
                <span className="ui-muted">{summary.count} {summary.count === 1 ? "avaliação" : "avaliações"}</span>
            </div>
            <div className="ui-rating-bars">
                {[5, 4, 3, 2, 1].map((n) => {
                    const amount = distribution[n - 1] || 0;
                    return (
                        <div key={n} className="ui-rating-bar">
                            <span>{n}</span>
                            <span className="ui-rating-track"><span style={{ width: `${(amount / summary.count) * 100}%` }} /></span>
                            <span>{amount}</span>
                        </div>
                    );
                })}
                {summary.recommendPercent != null && (
                    <span className="ui-muted" style={{ marginTop: 6 }}>
                        <strong style={{ color: "var(--ink-800)" }}>{summary.recommendPercent}%</strong> recomendam
                        {" "}({summary.recommendAnswers} {summary.recommendAnswers === 1 ? "resposta" : "respostas"})
                    </span>
                )}
            </div>
        </div>
    );
}
