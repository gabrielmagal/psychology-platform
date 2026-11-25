import React from "react";
import { FiWifiOff, FiXCircle } from "react-icons/fi";
import { describeError } from "../../../utils/errors";

/**
 * Janela de erro. `message` pode ser o erro bruto (ApiError) ou um texto: em qualquer caso
 * o usuário vê o que aconteceu, o que fazer e — nos erros internos — o código para o suporte.
 */
export default function ErrorDialog({ open, title, message, onClose }) {
    if (!open) return null;
    const info = describeError(message);
    // Cancelamento, ou sessão expirada (que o aplicativo já avisa em um lugar só).
    if (!info || info.status === 401) return null;

    const Icon = message?.network ? FiWifiOff : FiXCircle;
    return (
        <div className="ui-overlay" style={{ zIndex: 3000 }}>
            <div className="ui-modal" role="alertdialog" aria-modal="true" aria-labelledby="error-dialog-title">
                <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div className="ui-state-icon"
                         style={{ background: "var(--danger-100)", color: "var(--danger-600)", flex: "none", margin: 0 }}>
                        <Icon aria-hidden="true" />
                    </div>
                    <div style={{ minWidth: 0 }}>
                        <h2 id="error-dialog-title" className="ui-modal-title" style={{ marginBottom: 6 }}>
                            {title || info.title}
                        </h2>
                        <p style={{ color: "var(--ink-700)" }}>{info.message}</p>
                        {info.hint && <p className="ui-muted" style={{ marginTop: 8 }}>{info.hint}</p>}
                        {info.code && <p className="ui-alert-code">Código para o suporte: {info.code}</p>}
                    </div>
                </div>
                <div className="ui-modal-actions">
                    <button type="button" className="ui-btn ui-btn--primary" onClick={onClose} autoFocus>Entendi</button>
                </div>
            </div>
        </div>
    );
}
