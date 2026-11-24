import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import styles from "./Clinic.module.css";
import { useApi, errorMessage, money, whatsappLink, STATUS_LABEL } from "./clinicApi";

export default function FinancePage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const [month, setMonth] = useState(() => dayjs().startOf("month"));
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    const load = useCallback(async () => {
        setError(null);
        try {
            const from = month.format("YYYY-MM-DD");
            const to = month.endOf("month").format("YYYY-MM-DD");
            setData(await request(`/clinic/finance?from=${from}&to=${to}`));
        } catch (err) {
            setError(errorMessage(err));
        }
    }, [request, month]);

    useEffect(() => { load(); }, [load]);

    const sessions = data?.sessionsByStatus || {};
    const debtors = data?.debtors || [];

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>💰 Financeiro</h1>
                    <p className={styles.subtitle} style={{ textTransform: "capitalize" }}>
                        {month.format("MMMM [de] YYYY")}
                    </p>
                </div>
                <div className={styles.toolbar}>
                    <button className={styles.btnGhost} onClick={() => setMonth((m) => m.subtract(1, "month"))}>
                        ◀ Mês anterior
                    </button>
                    <button className={styles.btnGhost} onClick={() => setMonth(dayjs().startOf("month"))}>
                        Mês atual
                    </button>
                    <button className={styles.btnGhost} onClick={() => setMonth((m) => m.add(1, "month"))}>
                        Próximo mês ▶
                    </button>
                </div>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            {data && (
                <div className={styles.stack}>
                    <div className={styles.grid}>
                        <div className={styles.stat}>
                            <div className={styles.statLabel}>Recebido no mês</div>
                            <div className={styles.statValue}>{money(data.received)}</div>
                        </div>
                        <div className={styles.stat}>
                            <div className={styles.statLabel}>A receber (todos os pacotes)</div>
                            <div className={styles.statValue}>{money(data.outstanding)}</div>
                        </div>
                        {Object.keys(STATUS_LABEL).map((status) => (
                            <div key={status} className={styles.stat}>
                                <div className={styles.statLabel}>Sessões — {STATUS_LABEL[status].toLowerCase()}</div>
                                <div className={styles.statValue}>{sessions[status] || 0}</div>
                            </div>
                        ))}
                    </div>

                    <div className={styles.card}>
                        <h2>Pacotes com saldo em aberto</h2>
                        {debtors.length === 0 ? (
                            <p className={styles.muted}>
                                Nenhum saldo em aberto. Só entram aqui os pacotes com o campo "Valor do Pacote" preenchido.
                            </p>
                        ) : (
                            <div style={{ overflowX: "auto" }}>
                                <table className={styles.table}>
                                    <thead>
                                    <tr>
                                        <th>Paciente</th>
                                        <th>Pacote</th>
                                        <th className={styles.numeric}>Valor</th>
                                        <th className={styles.numeric}>Pago</th>
                                        <th className={styles.numeric}>Saldo</th>
                                        <th></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {debtors.map((d) => {
                                        const link = whatsappLink(
                                            d.patientPhone,
                                            `Olá, ${d.patientName.split(" ")[0]}! Consta um saldo em aberto de ${money(d.balance)} referente a "${d.packageTitle || "pacote de sessões"}". Qualquer dúvida, estou à disposição.`
                                        );
                                        return (
                                            <tr key={d.packageId}>
                                                <td>{d.patientName}</td>
                                                <td>{d.packageTitle || "—"}</td>
                                                <td className={styles.numeric}>{money(d.price)}</td>
                                                <td className={styles.numeric}>{money(d.paid)}</td>
                                                <td className={styles.numeric}><strong>{money(d.balance)}</strong></td>
                                                <td>
                                                    {link && (
                                                        <a className={styles.btnGhost} href={link} target="_blank"
                                                           rel="noopener noreferrer">Cobrar por WhatsApp</a>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
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
