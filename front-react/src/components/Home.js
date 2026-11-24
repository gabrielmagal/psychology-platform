import { Link, useNavigate } from "react-router-dom";
import styles from "./Home.module.css";
import clinic from "./pages/clinic/Clinic.module.css";
import { useRoles } from "./shared/UseRoles";
import React, { useEffect, useState } from "react";
import { useApi, money, formatDate, STATUS_LABEL } from "./pages/clinic/clinicApi";

function SessionLine({ item, isStaff, showDate }) {
    return (
        <li className={clinic.listItem}>
            <span>
                <strong>
                    {showDate ? `${formatDate(item.date)} ` : ""}{item.startTime || "Sem horário"}
                </strong>{" — "}
                {isStaff ? item.patientName : item.psychologistName || item.title}
            </span>
            <span className={`${clinic.badge} ${clinic[`badge${item.status}`] || ""}`}>
                {item.status === "AGENDADA" && item.confirmedAt ? "Confirmada" : STATUS_LABEL[item.status]}
            </span>
        </li>
    );
}

export default function Home({ keycloak, realm }) {
    const navigate = useNavigate();
    const { isAdmin, isPsychologist, isPatient } = useRoles(keycloak);
    const isStaff = isAdmin || isPsychologist;
    const request = useApi({ keycloak, realm });
    const [dashboard, setDashboard] = useState(null);

    useEffect(() => {
        let cancelled = false;
        request(`/clinic/dashboard`)
            .then((data) => { if (!cancelled) setDashboard(data); })
            // O painel é um resumo: se falhar, a tela inicial continua utilizável pelos atalhos.
            .catch((err) => console.error("Erro ao carregar o painel", err));
        return () => { cancelled = true; };
    }, [request]);

    const firstName = keycloak?.tokenParsed?.given_name || keycloak?.tokenParsed?.preferred_username || "";

    return (
        <div className={styles.container}>
            <div className={styles.welcomeMessage}>
                <h2>Olá{firstName ? `, ${firstName}` : ""}!</h2>
                {isAdmin && <p>Você tem acesso total à plataforma: usuários, agenda, financeiro e auditoria.</p>}
                {isPsychologist && <p>Veja abaixo o seu dia e o que está pendente no consultório.</p>}
                {isPatient && <p>Acompanhe suas sessões, confirme presença e registre como você está.</p>}
            </div>

            {dashboard && (
                <div className={clinic.stack} style={{ marginBottom: "2rem" }}>
                    {isStaff && (
                        <div className={clinic.grid}>
                            <div className={clinic.stat}>
                                <div className={clinic.statLabel}>Sessões hoje</div>
                                <div className={clinic.statValue}>{dashboard.today.length}</div>
                            </div>
                            <div className={clinic.stat}>
                                <div className={clinic.statLabel}>Sem confirmação (próximos 7 dias)</div>
                                <div className={clinic.statValue}>{dashboard.unconfirmed}</div>
                            </div>
                            <div className={clinic.stat}>
                                <div className={clinic.statLabel}>Recebido no mês</div>
                                <div className={clinic.statValue}>{money(dashboard.receivedThisMonth)}</div>
                            </div>
                            <div className={clinic.stat}>
                                <div className={clinic.statLabel}>A receber ({dashboard.debtors} pacotes)</div>
                                <div className={clinic.statValue}>{money(dashboard.outstanding)}</div>
                            </div>
                        </div>
                    )}

                    <div className={clinic.card}>
                        <h3>Hoje</h3>
                        {dashboard.today.length === 0 ? (
                            <p className={clinic.muted}>Nenhuma sessão hoje.</p>
                        ) : (
                            <ul className={clinic.list}>
                                {dashboard.today.map((item) => (
                                    <SessionLine key={item.id} item={item} isStaff={isStaff} />
                                ))}
                            </ul>
                        )}
                        {dashboard.upcoming.length > 0 && (
                            <>
                                <h3 style={{ marginTop: "1.25rem" }}>Próximas sessões</h3>
                                <ul className={clinic.list}>
                                    {dashboard.upcoming.map((item) => (
                                        <SessionLine key={item.id} item={item} isStaff={isStaff} showDate />
                                    ))}
                                </ul>
                            </>
                        )}
                        <div className={clinic.actions}>
                            <Link className={clinic.btn} to="/agenda">Abrir agenda</Link>
                        </div>
                    </div>

                    {isStaff && dashboard.toClose?.length > 0 && (
                        <div className={clinic.card}>
                            <h3>Sessões passadas sem registro ({dashboard.toClose.length})</h3>
                            <p className={clinic.muted}>
                                Marque na agenda se foram realizadas, se houve falta ou se foram canceladas.
                            </p>
                            <ul className={clinic.list}>
                                {dashboard.toClose.slice(0, 5).map((item) => (
                                    <SessionLine key={item.id} item={item} isStaff showDate />
                                ))}
                            </ul>
                        </div>
                    )}

                    {isPatient && dashboard.pendingRatings?.length > 0 && (
                        <div className={clinic.card}>
                            <h3>Conte como foi</h3>
                            <p className={clinic.muted}>
                                Você tem {dashboard.pendingRatings.length} sessão(ões) realizada(s) sem avaliação.
                                Abra a sessão na agenda para avaliar.
                            </p>
                        </div>
                    )}
                </div>
            )}

            <div className={styles.cardGrid}>
                <div className={styles.card} onClick={() => navigate("/agenda")}>
                    <h2>🗓️ Agenda</h2>
                    {isStaff
                        ? <p>Agende sessões, registre presença e envie lembretes.</p>
                        : <p>Veja suas sessões, confirme presença e entre no atendimento online.</p>}
                </div>
                {isStaff && (
                    <div className={styles.card} onClick={() => navigate("/usuarios")}>
                        <h2>👥 Pacientes e usuários</h2>
                        <p>Cadastro, prontuário e contrato de cada paciente.</p>
                    </div>
                )}
                {isStaff && (
                    <div className={styles.card} onClick={() => navigate("/sessions-package")}>
                        <h2>🧠 Pacotes de Sessões</h2>
                        <p>Pacotes contratados, sessões, anotações e pagamentos.</p>
                    </div>
                )}
                {isStaff && (
                    <div className={styles.card} onClick={() => navigate("/financeiro")}>
                        <h2>💰 Financeiro</h2>
                        <p>Recebido no mês, saldo a receber e pacotes em aberto.</p>
                    </div>
                )}
                {isPatient && (
                    <div className={styles.card} onClick={() => navigate("/sessions-package")}>
                        <h2>🧠 Meus pacotes</h2>
                        <p>Veja seus pacotes, o que já foi pago e pague online.</p>
                    </div>
                )}
                {isPatient && (
                    <div className={styles.card} onClick={() => navigate("/diario")}>
                        <h2>🌤️ Diário de humor</h2>
                        <p>Registre como você está entre uma sessão e outra.</p>
                    </div>
                )}
                {isStaff && (
                    <div className={styles.card} onClick={() => navigate("/mercado-pago-info")}>
                        <h2>💳 Mercado Pago</h2>
                        <p>Conecte sua conta para receber pagamentos online dos pacientes.</p>
                    </div>
                )}
                {isAdmin && (
                    <div className={styles.card} onClick={() => navigate("/audit-log")}>
                        <h2>🔎 Auditoria</h2>
                        <p>Histórico de quem criou, alterou ou removeu cada registro.</p>
                    </div>
                )}
            </div>
        </div>
    );
}
