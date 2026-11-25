import React from "react";
import { Link } from "react-router-dom";
import { FiFileText } from "react-icons/fi";
import { EmptyState, ErrorState, Skeleton } from "../../ui/Feedback";
import { formatDate, money } from "../clinic/clinicApi";

const paymentMethodLabels = {
    CARD: "Cartão",
    CASH: "Dinheiro",
    PIX: "Pix",
    BOLETO: "Boleto",
    TRANSFER: "Transferência",
    OTHER: "Outro",
};

/** Pagamentos confirmados de um pacote, com o recibo de cada um. */
export default function SessionPaidDetailDialog({
    open,
    onClose,
    loading,
    error,
    data, // { totalPaid, sessionPackageId, payments: [...] }
}) {
    if (!open) return null;
    const payments = data?.payments || [];

    return (
        <div className="ui-overlay">
            <div className="ui-modal ui-modal--wide" role="dialog" aria-modal="true" aria-labelledby="paid-title">
                <h2 id="paid-title" className="ui-modal-title">Pagamentos do pacote</h2>

                {loading && <Skeleton rows={3} />}
                {!loading && error && <ErrorState error={error} compact />}

                {!loading && !error && data && payments.length === 0 && (
                    <EmptyState title="Nenhum pagamento confirmado">
                        Os pagamentos aparecem aqui assim que forem registrados ou confirmados pelo Asaas.
                    </EmptyState>
                )}

                {!loading && !error && payments.length > 0 && (
                    <div className="ui-table-wrap" style={{ boxShadow: "none" }}>
                        <table className="ui-table">
                            <thead>
                            <tr>
                                <th>Data</th>
                                <th>Forma</th>
                                <th className="ui-num">Valor</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            {payments.map((p, idx) => (
                                <tr key={p.id || idx}>
                                    <td>{p.paymentDate ? formatDate(p.paymentDate) : "—"}</td>
                                    <td>{paymentMethodLabels[p.paymentMethod] || p.paymentMethod || "—"}</td>
                                    <td className="ui-num">{money(p.amount)}</td>
                                    <td className="ui-num">
                                        {p.id && (
                                            <Link className="ui-btn ui-btn--sm" to={`/documento/recibo/${p.id}`}>
                                                <FiFileText aria-hidden="true" /> Recibo
                                            </Link>
                                        )}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                            <tfoot>
                            <tr>
                                <td colSpan={2} style={{ fontWeight: 650 }}>Total pago</td>
                                <td className="ui-num" style={{ fontWeight: 700, color: "var(--success-600)" }}>
                                    {money(data.totalPaid)}
                                </td>
                                <td></td>
                            </tr>
                            </tfoot>
                        </table>
                    </div>
                )}

                <div className="ui-modal-actions">
                    <button className="ui-btn" onClick={onClose}>Fechar</button>
                </div>
            </div>
        </div>
    );
}
