import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiCheckCircle, FiExternalLink, FiRefreshCw } from "react-icons/fi";
import { Alert, ErrorState, Skeleton } from "../../ui/Feedback";
import { money } from "../clinic/clinicApi";

/**
 * Pagamento online de um pacote.
 *
 * Abre (ou recupera) a cobrança do saldo em aberto e leva o usuário à fatura do provedor,
 * em outra aba. Na volta, "Já paguei" consulta o provedor e dá baixa — a confirmação nunca
 * depende do que o navegador diz.
 */
export default function OnlinePaymentDialog({ pkg, isPatient, request, onClose }) {
    const [charge, setCharge] = useState(null);
    const [error, setError] = useState(null);
    const [checking, setChecking] = useState(false);
    const [result, setResult] = useState(null);

    const open = async () => {
        setError(null);
        setCharge(null);
        try {
            setCharge(await request(`/online-payment/charge/${pkg.id}`, { method: "POST" }));
        } catch (err) {
            setError(err);
        }
    };

    useEffect(() => {
        open();
        // eslint-disable-next-line
    }, [pkg.id]);

    const check = async () => {
        setChecking(true);
        setError(null);
        try {
            setResult(await request(`/online-payment/sync/${pkg.id}`, { method: "POST" }));
        } catch (err) {
            setError(err);
        } finally {
            setChecking(false);
        }
    };

    const settled = result && Number(result.balance) === 0;
    // Erros de regra (sem valor, sem conta conectada, já quitado) não se resolvem tentando de novo.
    const retryable = error && (error.network || error.status >= 500);
    const expected = error && (error.status === 400 || error.status === 409) && error.message;

    return (
        <div className="ui-overlay">
            <div className="ui-modal" role="dialog" aria-modal="true" aria-labelledby="pay-title">
                <h2 id="pay-title" className="ui-modal-title">
                    {isPatient ? "Pagar online" : "Cobrança online"} — {pkg.packageTitle || "pacote de sessões"}
                </h2>

                {/* 400/409 aqui não são falha: são situações esperadas (sem valor, sem conta, já quitado). */}
                {expected && (
                    <Alert tone="warning" title="Pagamento online indisponível para este pacote">
                        {error.message}
                    </Alert>
                )}
                {error && !expected && <ErrorState error={error} onRetry={retryable ? open : undefined} compact />}

                {error?.status === 409 && !isPatient && (
                    <p className="ui-muted" style={{ marginTop: 12 }}>
                        Para receber online, conecte sua conta em{" "}
                        <Link to="/recebimento-online">Recebimento online</Link>.
                    </p>
                )}

                {!error && !charge && <Skeleton rows={3} />}

                {charge && !settled && (
                    <div className="ui-stack" style={{ gap: 14 }}>
                        <div className="ui-stat">
                            <div className="ui-stat-label">Valor em aberto</div>
                            <div className="ui-stat-value">{money(charge.amount)}</div>
                        </div>
                        {charge.sandbox && (
                            <Alert tone="warning" title="Ambiente de testes">
                                Esta cobrança foi criada no ambiente de testes do Asaas: nenhum dinheiro de verdade é movimentado.
                            </Alert>
                        )}
                        <p>
                            {isPatient
                                ? "A fatura abre em outra aba. Lá você escolhe Pix, boleto ou cartão."
                                : "Envie o link da fatura ao paciente ou abra para conferir. Ele escolhe Pix, boleto ou cartão."}
                        </p>
                        <div className="ui-toolbar">
                            <a className="ui-btn ui-btn--primary" href={charge.invoiceUrl} target="_blank" rel="noopener noreferrer">
                                <FiExternalLink aria-hidden="true" /> Abrir fatura
                            </a>
                            <button className="ui-btn" onClick={check} disabled={checking}>
                                <FiRefreshCw aria-hidden="true" /> {checking ? "Verificando…" : isPatient ? "Já paguei, verificar" : "Verificar pagamento"}
                            </button>
                        </div>
                        {result && !settled && (
                            <Alert tone="info" title="Pagamento ainda não confirmado">
                                O Asaas ainda não registrou o pagamento. Pix costuma confirmar em instantes; boleto pode
                                levar até 3 dias úteis. Você pode fechar esta janela e verificar depois.
                            </Alert>
                        )}
                    </div>
                )}

                {settled && (
                    <Alert tone="success" title="Pagamento confirmado" icon={FiCheckCircle}>
                        O pacote está quitado. O recibo fica disponível em "{isPatient ? "Meus pagamentos" : "Recibos"}".
                    </Alert>
                )}

                <div className="ui-modal-actions">
                    <button className="ui-btn" onClick={onClose}>Fechar</button>
                </div>
            </div>
        </div>
    );
}
