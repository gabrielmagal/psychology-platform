import React from "react";
import { Button } from "@mui/material";

export default function AlertDialog({ open, message, onClose }) {
    if (!open) return null;
    return (
        <div
            style={{
                position: "fixed", top: 0, left: 0, right: 0, bottom: 0,
                zIndex: 3000, backgroundColor: "rgba(0,0,0,0.6)",
                display: "flex", alignItems: "center", justifyContent: "center"
            }}
        >
            <div
                style={{
                    background: "white", padding: "2rem", borderRadius: "8px",
                    maxWidth: "500px", width: "100%", textAlign: "center"
                }}
            >
                <p>{message}</p>
                <Button
                    onClick={onClose}
                    variant="contained"
                    sx={{ mt: 2, backgroundColor: "#2196f3" }}
                >
                    OK
                </Button>
            </div>
        </div>
    );
}