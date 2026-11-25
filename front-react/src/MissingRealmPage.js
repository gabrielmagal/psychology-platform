import React from "react";
import { brand, BrandMark } from "./theme/brand";

/** Acesso sem o identificador da clínica na URL: explica como entrar. */
const MissingRealmPage = () => {
  const baseUrl = window.location.origin;

  return (
    <div style={{
      minHeight: "100vh",
      display: "grid",
      placeItems: "center",
      padding: 24,
      background: "var(--surface-sidebar)",
    }}>
      <div className="ui-modal" style={{ maxWidth: 520, textAlign: "center", padding: 36 }}>
        <div style={{ color: "var(--brand-600)", display: "inline-flex", marginBottom: 14 }}>
          <BrandMark size={48} />
        </div>
        <h1 style={{ fontSize: "1.5rem", marginBottom: 8 }}>{brand.name}</h1>
        <p className="ui-muted" style={{ marginBottom: 22 }}>
          Para entrar, use o endereço da sua clínica: o identificador dela vai logo depois da barra.
        </p>
        <code style={{
          display: "block",
          padding: "12px 16px",
          borderRadius: "var(--radius-sm)",
          background: "var(--ink-100)",
          color: "var(--ink-800)",
          fontSize: "0.95rem",
          wordBreak: "break-all",
        }}>
          {baseUrl}/<strong>sua-clinica</strong>
        </code>
        <p className="ui-muted" style={{ marginTop: 22 }}>
          Não sabe o endereço? Peça ao responsável pela clínica o link de acesso.
        </p>
      </div>
    </div>
  );
};

export default MissingRealmPage;
