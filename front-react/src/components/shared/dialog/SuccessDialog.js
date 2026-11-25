import React from "react";
import { FiCheckCircle } from "react-icons/fi";

export default function SuccessDialog({ open, title = "Tudo certo", message, onClose }) {
    if (!open) return null;
    return (
        <div className="ui-overlay" style={{ zIndex: 3000 }}>
            <div className="ui-modal" role="dialog" aria-modal="true" aria-labelledby="success-dialog-title">
                <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div className="ui-state-icon"
                         style={{ background: "var(--success-100)", color: "var(--success-600)", flex: "none", margin: 0 }}>
                        <FiCheckCircle aria-hidden="true" />
                    </div>
                    <div>
                        <h2 id="success-dialog-title" className="ui-modal-title" style={{ marginBottom: 6 }}>{title}</h2>
                        <p style={{ color: "var(--ink-700)" }}>{message}</p>
                    </div>
                </div>
                <div className="ui-modal-actions">
                    <button type="button" className="ui-btn ui-btn--primary" onClick={onClose} autoFocus>OK</button>
                </div>
            </div>
        </div>
    );
}
