import React, { useCallback, useEffect, useState } from "react";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { useApi, formatDate } from "./clinicApi";

/** Para o administrador: até quando cada prontuário precisa ser guardado. */
export default function RetentionPage(props) {
    const request = useApi(props);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/retention`).then(setData).catch(setError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const patients = data?.patients || [];
    const disposable = patients.filter((p) => p.disposable).length;

    return (
        <div className={styles.page}>
            <PageHeader
                title="Guarda de prontuários"
                subtitle={data ? `Prazo de guarda: ${data.retentionYears} anos após o último atendimento.` : undefined}
            />
            {error && <ErrorState error={error} onRetry={load} />}
            {!data && !error && <div className={styles.card}><Skeleton rows={6} /></div>}

            {data && (
                <div className={styles.stack}>
                    <Alert tone="info" title="Como funciona">
                        Enquanto o prazo não vence, o sistema não deixa excluir o cadastro do paciente. Vencido o prazo,
                        o registro aparece como "pode ser descartado" — o descarte continua sendo uma decisão sua,
                        feita pela exclusão do paciente em Pacientes e usuários.
                        {disposable > 0 && ` Hoje há ${disposable} prontuário(s) com o prazo vencido.`}
                    </Alert>
                    <div className={styles.card}>
                        {patients.length === 0 ? (
                            <EmptyState title="Nenhum prontuário ainda">Pacientes com sessões ou anamnese passam a ser listados aqui.</EmptyState>
                        ) : (
                            <div style={{ overflowX: "auto" }}>
                                <table className={styles.table}>
                                    <thead><tr><th>Paciente</th><th>Último atendimento</th><th>Guardar até</th><th>Situação</th></tr></thead>
                                    <tbody>
                                    {patients.map((p) => (
                                        <tr key={p.patientId}>
                                            <td>{p.patientName}</td>
                                            <td>{formatDate(p.lastActivity)}</td>
                                            <td>{formatDate(p.keepUntil)}</td>
                                            <td>
                                                <span className={`ui-badge ${p.disposable ? "ui-badge--warning" : "ui-badge--success"}`}>
                                                    {p.disposable ? "Pode ser descartado" : "Em guarda obrigatória"}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
