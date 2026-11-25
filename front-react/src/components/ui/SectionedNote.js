import React, { useLayoutEffect, useRef } from "react";

/** Área de texto que cresce com o conteúdo, sem barra de rolagem própria. */
export function AutoTextarea({ value, onChange, minRows = 2, ...rest }) {
    const ref = useRef(null);
    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;
        el.style.height = "auto";
        el.style.height = `${el.scrollHeight + 2}px`;
    }, [value]);
    return <textarea ref={ref} rows={minRows} value={value || ""} onChange={(e) => onChange(e.target.value)} {...rest} />;
}

/**
 * Nota dividida em seções com título e orientação: prontuário, laudo, ata, checklist de
 * visita... `sections` é `[{ key, label, hint, placeholder, private }]`; `values`, um objeto
 * por chave. Com `readOnly`, mostra só as seções preenchidas.
 */
export default function SectionedNote({ sections, values, onChange, readOnly = false, emptyText = "Nada registrado." }) {
    const data = values || {};
    if (readOnly) {
        const filled = sections.filter((section) => data[section.key]);
        if (filled.length === 0) return <p className="ui-muted">{emptyText}</p>;
        return (
            <div className="ui-note">
                {filled.map((section) => (
                    <div key={section.key} className={`ui-note-section ${section.private ? "ui-note-section--private" : ""}`}>
                        <span className="ui-note-label">{section.label}</span>
                        <div className="ui-note-read">{data[section.key]}</div>
                    </div>
                ))}
            </div>
        );
    }
    return (
        <div className="ui-note">
            {sections.map((section) => {
                const id = `note-${section.key}`;
                return (
                    <div key={section.key} className={`ui-note-section ${section.private ? "ui-note-section--private" : ""}`}>
                        <label className="ui-note-label" htmlFor={id}>{section.label}</label>
                        {section.hint && <span className="ui-note-hint" id={`${id}-hint`}>{section.hint}</span>}
                        <AutoTextarea id={id} name={section.key} value={data[section.key]}
                                      placeholder={section.placeholder}
                                      aria-describedby={section.hint ? `${id}-hint` : undefined}
                                      onChange={(text) => onChange({ ...data, [section.key]: text })} />
                    </div>
                );
            })}
        </div>
    );
}
