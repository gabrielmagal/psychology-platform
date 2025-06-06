// src/MissingRealmPage.js
import React from "react";
import PsychologyIcon from "@mui/icons-material/Psychology"; // ícone de cérebro do MUI

const MissingRealmPage = () => {
  const baseUrl = window.location.origin;

  return (
    <div style={{
      height: "100vh",
      background: "#f7f9fc",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      fontFamily: "'Segoe UI', sans-serif",
      color: "#333",
      padding: "2rem",
      textAlign: "center"
    }}>
      <PsychologyIcon style={{ fontSize: "4rem", color: "#6c63ff", marginBottom: "1rem" }} />
      <h1 style={{ fontSize: "2.5rem", marginBottom: "1rem" }}>
        Plataforma de Psicologia
      </h1>
      <p style={{ fontSize: "1.2rem", maxWidth: "700px", marginBottom: "1.5rem" }}>
        Para acessar sua área personalizada, inclua o identificador da clínica na URL.
      </p>
      <code style={{
        background: "#eef1f7",
        padding: "1rem 1.5rem",
        borderRadius: "8px",
        fontSize: "1rem",
        fontWeight: "bold",
        color: "#444",
        marginBottom: "1.5rem"
      }}>
        {baseUrl}/<b>sua-clinica</b>
      </code>
      <p style={{ fontSize: "1rem", color: "#666" }}>
        Caso tenha dúvidas, entre em contato com a equipe de suporte.
      </p>
    </div>
  );
};

export default MissingRealmPage;
