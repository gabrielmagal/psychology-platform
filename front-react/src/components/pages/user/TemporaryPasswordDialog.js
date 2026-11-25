import React, { useState } from "react";
import { FiCheck, FiCopy, FiKey } from "react-icons/fi";

/**
 * Mostra a senha provisória uma única vez. Ela não fica guardada em lugar nenhum: quem
 * cadastrou precisa entregá-la à pessoa, que é obrigada a trocá-la no primeiro acesso.
 */
export default function TemporaryPasswordDialog({ data, onClose }) {
    const [copied, setCopied] = useState(false);
    if (!data) return null;

    const copy = async () => {
        try {
            await navigator.clipboard.writeText(data.password);
            setCopied(true);
        } catch {
            // Sem permissão de área de transferência: a senha continua visível para copiar à mão.
        }
    };

    return (
        <div className="ui-overlay" style={{ zIndex: 3000 }}>
            <div className="ui-modal" role="dialog" aria-modal="true" aria-labelledby="temp-password-title">
                <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div className="ui-state-icon"
                         style={{ background: "var(--success-100)", color: "var(--success-600)", flex: "none", margin: 0 }}>
                        <FiKey aria-hidden="true" />
                    </div>
                    <div style={{ minWidth: 0 }}>
                        <h2 id="temp-password-title" className="ui-modal-title" style={{ marginBottom: 6 }}>
                            {data.created ? "Usuário cadastrado" : "Senha provisória gerada"}
                        </h2>
                        <p style={{ color: "var(--ink-700)" }}>
                            Entregue estes dados a <strong>{data.name}</strong>. No primeiro acesso o sistema pede
                            a criação de uma senha própria.
                        </p>
                    </div>
                </div>

                <dl style={{ margin: "18px 0 6px", display: "grid", gridTemplateColumns: "auto 1fr", gap: "10px 16px", alignItems: "center" }}>
                    <dt style={{ color: "var(--ink-500)" }}>Login (CPF)</dt>
                    <dd style={{ margin: 0, fontWeight: 600 }}>{data.login}</dd>
                    <dt style={{ color: "var(--ink-500)" }}>Senha provisória</dt>
                    <dd style={{ margin: 0, display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                        <code data-testid="temporary-password"
                              style={{ fontSize: 18, fontWeight: 700, letterSpacing: 1, userSelect: "all" }}>
                            {data.password}
                        </code>
                        <button type="button" className="ui-btn ui-btn--ghost ui-btn--sm" onClick={copy}>
                            {copied ? <FiCheck aria-hidden="true" /> : <FiCopy aria-hidden="true" />}
                            {copied ? "Copiada" : "Copiar"}
                        </button>
                    </dd>
                </dl>

                <p style={{ color: "var(--ink-500)", fontSize: 13 }}>
                    Anote agora: por segurança a senha não é guardada e não aparece de novo. Se ela se perder,
                    use “Nova senha” na lista de usuários.
                </p>

                <div className="ui-modal-actions">
                    <button type="button" className="ui-btn ui-btn--primary" onClick={onClose} autoFocus>Já anotei</button>
                </div>
            </div>
        </div>
    );
}
