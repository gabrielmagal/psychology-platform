import React from "react";
import styles from "../dynamic/EntityCrudPage.module.css";
import { Button } from "@mui/material";

export default function ConfirmDialog({ message, onConfirm, onCancel }) {
  return (
    <div
      className={styles.modalOverlay}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 2000, // acima do MUI Dialog (1300+)
        backgroundColor: "rgba(0, 0, 0, 0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      }}
    >
      <div
        className={styles.modalContent}
        style={{
          background: "white",
          padding: "2rem",
          borderRadius: "8px",
          zIndex: 2001,
          maxWidth: "500px",
          width: "100%"
        }}
      >
        <h3>Tem certeza?</h3>
        <p>{message}</p>
        <div className={styles.modalButtons} style={{ marginTop: "1rem", display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
          <Button
            onClick={onConfirm}
            variant="contained"
            sx={{
              backgroundColor: '#28a745',
              color: 'white',
              fontWeight: 'bold',
              padding: '0.5rem 1.2rem',
              borderRadius: '5px',
              '&:hover': {
                backgroundColor: '#218838',
              },
            }}
          >
            Sim
          </Button>
          <Button
            onClick={onCancel}
            variant="outlined"
            sx={{
              backgroundColor: '#dc3545',
              color: 'white',
              fontWeight: 'bold',
              padding: '0.5rem 1.2rem',
              borderRadius: '5px',
              border: 'none',
              '&:hover': {
                backgroundColor: '#c82333',
              },
            }}
          >
            Cancelar
          </Button>
        </div>
      </div>
    </div>
  );
}
