import React from "react";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import styles from "../dynamic/EntityCrudPage.module.css";
import dayjs from 'dayjs'

const paymentMethodLabels = {
    CARD: "Cartão",
    CASH: "Dinheiro",
    PIX: "Pix",
    BOLETO: "Boleto",
    TRANSFER: "Transferência",
};

export default function SessionPaidDetailDialog({
                                                    open,
                                                    onClose,
                                                    loading,
                                                    error,
                                                    data, // { totalPaid, sessionPackageId, payments: [...] }
                                                }) {
    if (!open) return null;

    const formatBRL = (val) =>
        Number(val).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

    return (
        <div
            className={styles.modalOverlay}
            style={{
                position: "fixed",
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                zIndex: 2000,
                backgroundColor: "rgba(0, 0, 0, 0.6)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
            }}
        >
            <div
                className={styles.modalContent}
                style={{
                    background: "white",
                    padding: "2rem",
                    borderRadius: "8px",
                    maxWidth: "540px",
                    width: "100%",
                    minHeight: "140px",
                }}
            >
                <h3 style={{ marginTop: 0, marginBottom: "1rem" }}>
                    Pagamentos do Pacote
                </h3>
                <div style={{ minHeight: 70 }}>
                    {loading && (
                        <div style={{ display: "flex", justifyContent: "center" }}>
                            <CircularProgress />
                        </div>
                    )}
                    {!loading && error && (
                        <div style={{ color: "red", padding: "1rem 0" }}>{error}</div>
                    )}
                    {!loading && !error && data && (
                        <>
                            {(!data.payments || data.payments.length === 0) ? (
                                <div style={{ padding: "1.5rem", textAlign: "center", color: "#666" }}>
                                    Nenhum pagamento registrado para este pacote.
                                </div>
                            ) : (
                                <div style={{ overflowX: "auto" }}>
                                    <table
                                        style={{
                                            width: "100%",
                                            borderCollapse: "collapse",
                                            marginBottom: "1rem",
                                            background: "#fff",
                                        }}
                                    >
                                        <thead>
                                        <tr>
                                            <th style={{ padding: "0.5rem", borderBottom: "1px solid #dee2e6", textAlign: "left" }}>Forma de Pagamento</th>
                                            <th style={{ padding: "0.5rem", borderBottom: "1px solid #dee2e6", textAlign: "left" }}>Data</th>
                                            <th style={{ padding: "0.5rem", borderBottom: "1px solid #dee2e6", textAlign: "right" }}>Valor</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {data.payments.map((p, idx) => (
                                            <tr key={p.id || idx}>
                                                <td style={{ padding: "0.5rem", borderBottom: "1px solid #f1f3f5" }}>
                                                    {paymentMethodLabels[p.paymentMethod] || p.paymentMethod || "-"}
                                                </td>
                                                <td style={{ padding: "0.5rem", borderBottom: "1px solid #f1f3f5" }}>
                                                    {p.paymentDate ? dayjs(p.paymentDate).format("DD/MM/YYYY") : "-"}
                                                </td>
                                                <td style={{ padding: "0.5rem", borderBottom: "1px solid #f1f3f5", textAlign: "right" }}>
                                                    {formatBRL(p.amount)}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                        <tfoot>
                                        <tr>
                                            <td style={{ padding: "0.5rem", fontWeight: "bold", color: "#444" }} colSpan={2}>Total</td>
                                            <td style={{ padding: "0.5rem", fontWeight: "bold", textAlign: "right", color: "#388e3c" }}>
                                                {formatBRL(data.totalPaid)}
                                            </td>
                                        </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            )}
                        </>
                    )}
                </div>
                <div style={{ marginTop: "2rem", display: "flex", justifyContent: "flex-end" }}>
                    <Button
                        onClick={onClose}
                        variant="contained"
                        sx={{
                            backgroundColor: "#dc3545",
                            color: "white",
                            fontWeight: "bold",
                            borderRadius: "5px",
                            padding: "0.5rem 2rem",
                            "&:hover": { backgroundColor: "#af2b39" },
                        }}
                    >
                        Fechar
                    </Button>
                </div>
            </div>
        </div>
    );
}
