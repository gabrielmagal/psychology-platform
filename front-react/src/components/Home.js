import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
    FiArrowRight, FiCalendar, FiCreditCard, FiDollarSign, FiPackage, FiShield, FiSun, FiUsers,
} from "react-icons/fi";
import styles from "./Home.module.css";
import { useRoles } from "./shared/UseRoles";
import { EmptyState, ErrorState, Skeleton, Alert } from "./ui/Feedback";
import { useApi, money, formatDate, STATUS_LABEL } from "./pages/clinic/clinicApi";

const STATUS_TONE = { REALIZADA: "ui-badge--success", FALTA: "ui-badge--danger", CANCELADA: "", AGENDADA: "ui-badge--info" };

function SessionLine({ item, isStaff, showDate }) {
    const confirmed = item.status === "AGENDADA" && item.confirmedAt;
    return (
        <li className="ui-list-item">
            <span className={styles.sessionLine}>
                <span className={styles.sessionTime}>
                    {showDate && <small>{formatDate(item.date).slice(0, 5)}</small>}
                    {item.startTime || "--:--"}
                </span>
                <span>
                    <strong>{isStaff ? item.patientName : item.professionalName || item.title}</strong>
                    <small>{item.modality === "ONLINE" ? "Online" : "Presencial"} · {item.durationMinutes} min</small>
                </span>
            </span>
            <span className={`ui-badge ${confirmed ? "ui-badge--success" : STATUS_TONE[item.status] || ""}`}>
                {confirmed ? "Confirmada" : STATUS_LABEL[item.status]}
            </span>
        </li>
    );
}

function Shortcut({ to, icon: Icon, title, children }) {
    return (
        <Link to={to} className={styles.shortcut}>
            <span className={styles.shortcutIcon}><Icon aria-hidden="true" /></span>
            <span className={styles.shortcutText}>
                <strong>{title}</strong>
                <small>{children}</small>
            </span>
            <FiArrowRight className={styles.shortcutArrow} aria-hidden="true" />
        </Link>
    );
}

export default function Home({ keycloak, realm }) {
    const { isAdmin, isProfessional, isPatient } = useRoles(keycloak);
    const isStaff = isAdmin || isProfessional;
    const request = useApi({ keycloak, realm });
    const [dashboard, setDashboard] = useState(null);
    const [error, setError] = useState(null);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/dashboard`).then(setDashboard).catch(setError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const firstName = keycloak?.tokenParsed?.given_name || keycloak?.tokenParsed?.preferred_username || "";
    const hour = new Date().getHours();
    const greeting = hour < 12 ? "Bom dia" : hour < 18 ? "Boa tarde" : "Boa noite";
    const today = new Date().toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" });

    return (
        <div className="ui-page">
            <div className="ui-page-header">
                <div>
                    <h1 className="ui-page-title">{greeting}{firstName ? `, ${firstName}` : ""}</h1>
                    <p className="ui-page-subtitle">{today.charAt(0).toUpperCase() + today.slice(1)}</p>
                </div>
                <Link className="ui-btn ui-btn--primary" to="/agenda"><FiCalendar aria-hidden="true" /> Abrir agenda</Link>
            </div>

            {error && <ErrorState error={error} onRetry={load} />}
            {!dashboard && !error && <div className="ui-card"><Skeleton rows={5} /></div>}

            {dashboard && (
                <div className="ui-stack">
                    {isStaff && (
                        <div className="ui-grid">
                            <div className="ui-stat">
                                <div className="ui-stat-label">Sessões hoje</div>
                                <div className="ui-stat-value">{dashboard.today.length}</div>
                            </div>
                            <div className="ui-stat">
                                <div className="ui-stat-label">Sem confirmação nos próximos 7 dias</div>
                                <div className="ui-stat-value">{dashboard.unconfirmed}</div>
                            </div>
                            <div className="ui-stat">
                                <div className="ui-stat-label">Recebido no mês</div>
                                <div className="ui-stat-value">{money(dashboard.receivedThisMonth)}</div>
                            </div>
                            <div className="ui-stat">
                                <div className="ui-stat-label">A receber · {dashboard.debtors} pacote(s)</div>
                                <div className="ui-stat-value">{money(dashboard.outstanding)}</div>
                            </div>
                        </div>
                    )}

                    {isStaff && dashboard.toClose?.length > 0 && (
                        <Alert
                            tone="warning"
                            title={`${dashboard.toClose.length} sessão(ões) passada(s) sem registro`}
                            actions={<Link className="ui-btn ui-btn--sm" to="/agenda">Registrar na agenda</Link>}
                        >
                            Marque se foram realizadas, se houve falta ou se foram canceladas — isso alimenta o
                            prontuário e o financeiro.
                        </Alert>
                    )}

                    {isPatient && dashboard.pendingTerms > 0 && (
                        <Alert tone="warning" title="Há um termo esperando a sua leitura"
                               actions={<Link className="ui-btn ui-btn--sm" to="/termos">Ler e aceitar</Link>}>
                            Seu psicólogo enviou um termo para você ler e aceitar antes do atendimento.
                        </Alert>
                    )}

                    {isStaff && dashboard.formAlerts?.length > 0 && (
                        <Alert tone="danger" title="Resposta com item de atenção">
                            {dashboard.formAlerts.map((alert, index) => (
                                <span key={index}>
                                    <Link to={`/prontuario/${alert.patientId}`}>{alert.patientName}</Link> respondeu "{alert.title}" marcando
                                    um item que pede acompanhamento de perto.{" "}
                                </span>
                            ))}
                        </Alert>
                    )}

                    {isPatient && dashboard.pendingForms > 0 && (
                        <Alert tone="info" title={`Você tem ${dashboard.pendingForms} questionário(s) para responder`}
                               actions={<Link className="ui-btn ui-btn--sm" to="/questionarios">Responder</Link>}>
                            Seu psicólogo pediu que você respondesse antes da próxima sessão.
                        </Alert>
                    )}

                    {isPatient && dashboard.openTasks > 0 && (
                        <Alert tone="info" title={`Você tem ${dashboard.openTasks} tarefa(s) para fazer`}
                               actions={<Link className="ui-btn ui-btn--sm" to="/tarefas">Ver tarefas</Link>}>
                            O que você combinou de fazer até a próxima sessão.
                        </Alert>
                    )}

                    {isPatient && dashboard.pendingRatings?.length > 0 && (
                        <Alert tone="info" title="Como foi a sua última sessão?"
                               actions={<Link className="ui-btn ui-btn--sm" to="/agenda">Avaliar na agenda</Link>}>
                            Você tem {dashboard.pendingRatings.length} sessão(ões) realizada(s) sem avaliação.
                        </Alert>
                    )}

                    <div className={styles.columns}>
                        <section className="ui-card">
                            <h2 className="ui-card-title">Hoje</h2>
                            {dashboard.today.length === 0 ? (
                                <EmptyState icon={FiCalendar} title="Nenhuma sessão hoje">
                                    {isStaff ? "Aproveite para organizar a semana na agenda." : "Sua próxima sessão aparece ao lado."}
                                </EmptyState>
                            ) : (
                                <ul className="ui-list">
                                    {dashboard.today.map((item) => <SessionLine key={item.id} item={item} isStaff={isStaff} />)}
                                </ul>
                            )}
                        </section>

                        <section className="ui-card">
                            <h2 className="ui-card-title">Próximos dias</h2>
                            {dashboard.upcoming.length === 0 ? (
                                <EmptyState icon={FiCalendar} title="Nada agendado para os próximos 7 dias" />
                            ) : (
                                <ul className="ui-list">
                                    {dashboard.upcoming.map((item) => (
                                        <SessionLine key={item.id} item={item} isStaff={isStaff} showDate />
                                    ))}
                                </ul>
                            )}
                        </section>
                    </div>
                </div>
            )}

            <h2 className={styles.sectionTitle}>Atalhos</h2>
            <div className={styles.shortcuts}>
                <Shortcut to="/agenda" icon={FiCalendar} title="Agenda">
                    {isStaff ? "Agendar, registrar presença e enviar lembretes" : "Ver sessões, confirmar presença e entrar no atendimento"}
                </Shortcut>
                {isStaff && (
                    <Shortcut to="/usuarios" icon={FiUsers} title="Pacientes e usuários">
                        Cadastro, prontuário e contrato
                    </Shortcut>
                )}
                <Shortcut to="/sessions-package" icon={FiPackage} title={isPatient ? "Meus pacotes" : "Pacotes de sessões"}>
                    {isPatient ? "O que foi contratado, o que foi pago e pagamento online" : "Sessões, anotações e pagamentos por paciente"}
                </Shortcut>
                {isStaff && (
                    <Shortcut to="/financeiro" icon={FiDollarSign} title="Resumo financeiro">
                        Recebido no mês e saldo em aberto
                    </Shortcut>
                )}
                {isStaff && (
                    <Shortcut to="/recebimento-online" icon={FiCreditCard} title="Recebimento online">
                        Conectar a conta do Asaas
                    </Shortcut>
                )}
                {isPatient && (
                    <Shortcut to="/diario" icon={FiSun} title="Diário de humor e sono">
                        Registrar como você está entre as sessões
                    </Shortcut>
                )}
                {isPatient && (
                    <Shortcut to="/plano-de-crise" icon={FiShield} title="Plano de crise">
                        O que fazer nos momentos difíceis, com os telefones de ajuda
                    </Shortcut>
                )}
                {isAdmin && (
                    <Shortcut to="/audit-log" icon={FiShield} title="Auditoria">
                        Quem alterou o quê, e quando
                    </Shortcut>
                )}
            </div>
        </div>
    );
}
