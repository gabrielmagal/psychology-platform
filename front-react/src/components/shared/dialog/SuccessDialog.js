import React from "react";
import { Button } from "@mui/material";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";

export default function SuccessDialog({ open, title = "Sucesso", message, onClose }) {
    if (!open) return null;

    return (
        <div
            style={{
                position: "fixed",
                top: 0, left: 0, right: 0, bottom: 0,
                zIndex: 3000,
                backgroundColor: "rgba(0,0,0,0.6)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
            }}
        >
            <div
                style={{
                    background: "white",
                    padding: "2rem",
                    borderRadius: "8px",
                    maxWidth: "500px",
                    width: "100%",
                    textAlign: "center",
                    borderTop: "5px solid #28a745",
                    boxShadow: "0 0 10px rgba(0,0,0,0.3)"
                }}
            >
                <CheckCircleOutlineIcon style={{ fontSize: 48, color: "#28a745" }} />
                <h2 style={{ color: "#28a745", margin: "1rem 0 0.5rem" }}>{title}</h2>
                <p style={{ marginBottom: "1.5rem", color: "#333" }}>{message}</p>
                <Button
                    onClick={onClose}
                    variant="contained"
                    sx={{
                        backgroundColor: "#28a745",
                        fontWeight: "bold",
                        "&:hover": {
                            backgroundColor: "#218838",
                        }
                    }}
                >
                    OK
                </Button>
            </div>
        </div>
    );
}
