import React from "react";
import { FiAlertTriangle } from "react-icons/fi";

/** Confirmação de ação que não tem volta. O botão diz o que vai acontecer, não "Sim". */
export default function ConfirmDialog({
    title = "Excluir este registro?",
    message,
    confirmLabel = "Excluir",
    onConfirm,
    onCancel,
}) {
    return (
        <div className="ui-overlay" style={{ zIndex: 2500 }}>
            <div className="ui-modal" role="alertdialog" aria-modal="true" aria-labelledby="confirm-dialog-title">
                <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div className="ui-state-icon"
                         style={{ background: "var(--danger-100)", color: "var(--danger-600)", flex: "none", margin: 0 }}>
                        <FiAlertTriangle aria-hidden="true" />
                    </div>
                    <div>
                        <h2 id="confirm-dialog-title" className="ui-modal-title" style={{ marginBottom: 6 }}>{title}</h2>
                        <p style={{ color: "var(--ink-700)" }}>{message}</p>
                        <p className="ui-muted" style={{ marginTop: 8 }}>Esta ação não pode ser desfeita.</p>
                    </div>
                </div>
                <div className="ui-modal-actions">
                    <button type="button" className="ui-btn" onClick={onCancel} autoFocus>Cancelar</button>
                    <button type="button" className="ui-btn ui-btn--danger" onClick={onConfirm}>{confirmLabel}</button>
                </div>
            </div>
        </div>
    );
}
