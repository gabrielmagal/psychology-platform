import React from "react";
import { FiInfo } from "react-icons/fi";

export default function AlertDialog({ open, title = "Atenção", message, onClose }) {
    if (!open) return null;
    return (
        <div className="ui-overlay" style={{ zIndex: 3100 }}>
            <div className="ui-modal" role="alertdialog" aria-modal="true" aria-labelledby="alert-dialog-title">
                <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div className="ui-state-icon"
                         style={{ background: "var(--warning-100)", color: "var(--warning-600)", flex: "none", margin: 0 }}>
                        <FiInfo aria-hidden="true" />
                    </div>
                    <div>
                        <h2 id="alert-dialog-title" className="ui-modal-title" style={{ marginBottom: 6 }}>{title}</h2>
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
