import React from "react";

/**
 * Questionário a partir de uma definição: escala, sim/não e texto livre. Serve para qualquer
 * formulário respondido pergunta a pergunta (escala clínica, pesquisa, checklist).
 *
 * `definition` é `{ instructions, options: [{value, label}], items: [{id, text, type}] }`, com
 * `type` SCALE, YESNO ou TEXT; `answers`, um objeto por id de pergunta. Com `readOnly`, mostra
 * só a resposta dada.
 */
export default function Questionnaire({ definition, answers, onChange, readOnly = false, name = "q" }) {
    const values = answers || {};
    const options = definition?.options || [];
    const set = (id, value) => onChange?.({ ...values, [id]: value });
    const labelOf = (item) => {
        const value = values[item.id];
        if (value === undefined || value === null || value === "") return "Sem resposta";
        if (item.type === "YESNO") return value === true || value === "true" ? "Sim" : "Não";
        if (item.type === "TEXT") return value;
        const option = options.find((o) => String(o.value) === String(value));
        return option ? `${option.label} (${option.value})` : String(value);
    };

    return (
        <div className="ui-questionnaire">
            {definition?.instructions && <p className="ui-questionnaire-intro">{definition.instructions}</p>}
            <ol className="ui-questionnaire-items">
                {(definition?.items || []).map((item) => (
                    <li key={item.id} className="ui-question">
                        <span className="ui-question-text" id={`${name}-${item.id}`}>{item.text}</span>
                        {readOnly ? (
                            <span className="ui-question-answer">{labelOf(item)}</span>
                        ) : item.type === "TEXT" ? (
                            <textarea rows={2} aria-labelledby={`${name}-${item.id}`} value={values[item.id] || ""}
                                      onChange={(e) => set(item.id, e.target.value)} />
                        ) : (
                            <div className="ui-question-options" role="radiogroup" aria-labelledby={`${name}-${item.id}`}>
                                {(item.type === "YESNO" ? [{ value: true, label: "Sim" }, { value: false, label: "Não" }] : options).map((option) => (
                                    <label key={String(option.value)}
                                           className={`ui-question-option ${String(values[item.id]) === String(option.value) ? "ui-question-option--on" : ""}`}>
                                        <input type="radio" name={`${name}-${item.id}`}
                                               checked={String(values[item.id]) === String(option.value)}
                                               onChange={() => set(item.id, option.value)} />
                                        {option.label}
                                    </label>
                                ))}
                            </div>
                        )}
                    </li>
                ))}
            </ol>
        </div>
    );
}

/** Todas as perguntas de escala e de sim/não foram respondidas? Texto livre é opcional. */
export function isComplete(definition, answers) {
    return (definition?.items || []).every((item) => item.type === "TEXT"
        || (answers?.[item.id] !== undefined && answers?.[item.id] !== null && answers?.[item.id] !== ""));
}
