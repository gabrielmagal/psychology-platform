import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { FiCheck, FiCopy, FiExternalLink } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { money, formatDate, formatCpf, PAYMENT_METHOD_LABEL } from "./clinicApi";

const CARNE_LEAO_URL = "https://www.gov.br/receitafederal/pt-br/assuntos/meu-imposto-de-renda/pagamento/carne-leao";

/**
 * Controle do Receita Saúde: o que o profissional recebeu no ano e ainda precisa lançar no
 * programa da Receita Federal. O recibo oficial sai de lá; aqui ficam os dados prontos para
 * copiar e a marca do que já foi lançado.
 */
export default function TaxReceiptsPanel({ request, year, readOnly }) {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [onlyPending, setOnlyPending] = useState(true);
    const [busy, setBusy] = useState(null);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/tax-receipts?year=${year}`).then(setData).catch(setError);
    }, [request, year]);

    useEffect(() => { load(); }, [load]);

    const mark = async (row, launched) => {
        setBusy(row.paymentId);
        try {
            await request(`/clinic/tax-receipts/${row.paymentId}`, { method: "PUT", body: { launched } });
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(null);
        }
    };

    const copy = async (row) => {
        const text = [
            `CPF do pagador: ${formatCpf(row.patientCpf)}`,
            `CPF do beneficiário: ${formatCpf(row.patientCpf)}`,
            `Data do pagamento: ${formatDate(row.date)}`,
            `Valor: ${money(row.amount)}`,
            `Descrição: Atendimento psicológico`,
        ].join("\n");
        try {
            await navigator.clipboard.writeText(text);
            toast.success("Dados copiados.");
        } catch {
            toast.error("Não foi possível copiar.");
        }
    };

    if (error) return <ErrorState error={error} onRetry={load} />;
    if (!data) return <div className={styles.card}><Skeleton rows={5} /></div>;

    const rows = data.rows.filter((row) => !onlyPending || !row.launched);

    return (
        <div className={styles.card}>
            <h2>Receita Saúde em {data.year}</h2>
            <Alert tone="info" title="Como funciona">
                Desde 2025, todo recebimento de pessoa física precisa de um recibo emitido no Receita Saúde (dentro do
                Carnê-Leão Web ou do aplicativo da Receita Federal). A Receita não permite que outro sistema emita por
                você: aqui ficam os dados prontos para copiar e o controle do que já foi lançado.{" "}
                <a href={CARNE_LEAO_URL} target="_blank" rel="noopener noreferrer">
                    Abrir o Carnê-Leão <FiExternalLink aria-hidden="true" />
                </a>
            </Alert>

            <div className={styles.grid} style={{ margin: "16px 0" }}>
                <div className={styles.stat}><div className={styles.statLabel}>Falta lançar</div><div className={styles.statValue}>{data.pending}</div></div>
                <div className={styles.stat}><div className={styles.statLabel}>Valor a lançar</div><div className={styles.statValue}>{money(data.pendingAmount)}</div></div>
                <div className={styles.stat}><div className={styles.statLabel}>Já lançados</div><div className={styles.statValue}>{data.launched}</div></div>
            </div>

            <label className={styles.check}>
                <input type="checkbox" checked={onlyPending} onChange={(e) => setOnlyPending(e.target.checked)} />
                Mostrar só o que falta lançar
            </label>

            {rows.length === 0 ? (
                <EmptyState title={onlyPending ? "Tudo lançado" : "Nenhum recebimento neste ano"}>
                    {onlyPending ? "Não há recebimento pendente de recibo no Receita Saúde." : "Os pagamentos confirmados aparecem aqui."}
                </EmptyState>
            ) : (
                <div style={{ overflowX: "auto" }}>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>Data</th><th>Paciente</th><th>CPF</th>{readOnly && <th>Profissional</th>}
                            <th>Forma</th><th className={styles.numeric}>Valor</th><th>Situação</th><th></th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((row) => (
                            <tr key={row.paymentId}>
                                <td>{formatDate(row.date)}</td>
                                <td>
                                    {row.patientName}
                                    {row.guardianName && <div className={styles.muted}>Responsável: {row.guardianName}</div>}
                                </td>
                                <td>{formatCpf(row.patientCpf)}</td>
                                {readOnly && <td>{row.professionalName || "—"}</td>}
                                <td>{PAYMENT_METHOD_LABEL[row.method] || "—"}</td>
                                <td className={styles.numeric}>{money(row.amount)}</td>
                                <td>
                                    <span className={`ui-badge ui-badge--${row.launched ? "success" : "warning"}`}>
                                        {row.launched ? `Lançado em ${formatDate(row.launchedAt)}` : "Falta lançar"}
                                    </span>
                                </td>
                                <td className={styles.numeric} style={{ whiteSpace: "nowrap" }}>
                                    <button className={styles.btnGhost} onClick={() => copy(row)} aria-label={`Copiar dados de ${row.patientName}`}>
                                        <FiCopy aria-hidden="true" /> Copiar
                                    </button>{" "}
                                    <Link className={styles.btnGhost} to={`/documento/recibo/${row.paymentId}`}>Recibo</Link>{" "}
                                    {!readOnly && (row.launched ? (
                                        <button className={styles.btnGhost} disabled={busy === row.paymentId} onClick={() => mark(row, false)}>Desfazer</button>
                                    ) : (
                                        <button className={styles.btnPrimary} disabled={busy === row.paymentId} onClick={() => mark(row, true)}>
                                            <FiCheck aria-hidden="true" /> Já lancei
                                        </button>
                                    ))}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            {rows.some((row) => row.guardianName) && (
                <p className={styles.muted} style={{ marginTop: 12 }}>
                    Paciente com responsável legal: se quem paga é o responsável, informe o CPF dele como pagador no Receita Saúde.
                </p>
            )}
        </div>
    );
}
