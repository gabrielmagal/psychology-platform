import React, { useState } from "react";
import { FiX } from "react-icons/fi";

/** Etiquetas só para leitura. Lista vazia não ocupa espaço. */
export function TagList({ items, tone, max }) {
    const list = items || [];
    if (list.length === 0) return null;
    const shown = max ? list.slice(0, max) : list;
    const hidden = list.length - shown.length;
    const className = `ui-tag ${tone === "neutral" ? "ui-tag--neutral" : ""}`;
    return (
        <span className="ui-tags">
            {shown.map((item) => <span key={item} className={className}>{item}</span>)}
            {hidden > 0 && <span className="ui-tag ui-tag--neutral">+{hidden}</span>}
        </span>
    );
}

/**
 * Campo de etiquetas: digita e confirma com Enter ou vírgula; Backspace no campo vazio remove
 * a última. `suggestions` vira uma fileira de atalhos de um clique.
 */
export function TagInput({ id, value, onChange, placeholder = "Digite e pressione Enter", suggestions = [], max = 12,
                             maxLength = 60 }) {
    const [draft, setDraft] = useState("");
    const items = value || [];
    const full = items.length >= max;

    const add = (raw) => {
        const text = String(raw).trim().replace(/\s+/g, " ");
        if (!text || full) return;
        if (items.some((item) => item.toLowerCase() === text.toLowerCase())) {
            setDraft("");
            return;
        }
        onChange([...items, text.slice(0, maxLength)]);
        setDraft("");
    };

    const onKeyDown = (event) => {
        if (event.key === "Enter" || event.key === ",") {
            event.preventDefault();
            add(draft);
        } else if (event.key === "Backspace" && !draft && items.length > 0) {
            onChange(items.slice(0, -1));
        }
    };

    const open = suggestions.filter((s) => !items.some((item) => item.toLowerCase() === s.toLowerCase()));

    return (
        <div>
            <div className="ui-tag-input">
                {items.map((item) => (
                    <span key={item} className="ui-tag">
                        {item}
                        <button type="button" aria-label={`Remover ${item}`}
                                onClick={() => onChange(items.filter((other) => other !== item))}>
                            <FiX aria-hidden="true" />
                        </button>
                    </span>
                ))}
                <input id={id} value={draft} disabled={full} maxLength={maxLength}
                       placeholder={full ? `Limite de ${max} itens` : items.length === 0 ? placeholder : ""}
                       onChange={(e) => setDraft(e.target.value)} onKeyDown={onKeyDown} onBlur={() => add(draft)} />
            </div>
            {open.length > 0 && !full && (
                <div className="ui-tag-suggestions" style={{ marginTop: 8 }}>
                    {open.map((s) => <button key={s} type="button" onClick={() => add(s)}>+ {s}</button>)}
                </div>
            )}
        </div>
    );
}
