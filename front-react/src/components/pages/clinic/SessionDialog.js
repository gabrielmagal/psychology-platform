import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { Alert } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { formatDate, whatsappLink, STATUS_LABEL, MODALITY_LABEL } from "./clinicApi";

/** Detalhes de uma sessão da agenda, com as ações de cada perfil. */
export default function SessionDialog({ item, request, isPatient, patientChangeHours, onClose, onChanged }) {
    const isStaff = !isPatient;
    const [mode, setMode] = useState("details"); // details | reschedule
    const [evolution, setEvolution] = useState(null);
    const [savingEvolution, setSavingEvolution] = useState(false);
    const [rating, setRating] = useState({ value: 5, comment: "" });
    const [newDate, setNewDate] = useState(item.date);
    const [newTime, setNewTime] = useState(item.startTime || "09:00");
    const [slots, setSlots] = useState(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        if (!isStaff) return;
        let cancelled = false;
        request(`/clinic/agenda/${item.id}/evolution`)
            .then((data) => { if (!cancelled) setEvolution(data?.evolution || ""); })
            .catch(() => { if (!cancelled) setEvolution(""); });
        return () => { cancelled = true; };
    }, [isStaff, item.id, request]);

    // O paciente escolhe entre os horários livres do profissional naquele dia.
    useEffect(() => {
        if (mode !== "reschedule" || !isPatient || !newDate) return;
        let cancelled = false;
        setSlots(null);
        request(`/clinic/agenda/${item.id}/free-slots?date=${newDate}`)
            .then((data) => { if (!cancelled) setSlots(data || []); })
            .catch(() => { if (!cancelled) setSlots([]); });
        return () => { cancelled = true; };
    }, [mode, isPatient, newDate, item.id, request]);

    const act = async (path, message, options = {}) => {
        setBusy(true);
        try {
            await request(path, { method: "PUT", ...options });
            toast.success(message);
            onChanged();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const saveEvolution = async () => {
        setSavingEvolution(true);
        try {
            await request(`/clinic/agenda/${item.id}/evolution`, { method: "PUT", body: { evolution } });
            toast.success("Evolução salva.");
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setSavingEvolution(false);
        }
    };

    const remind = () => {
        const when = `${formatDate(item.date)}${item.startTime ? ` às ${item.startTime}` : ""}`;
        const where = item.modality === "ONLINE" && item.meetingLink ? ` Link: ${item.meetingLink}` : "";
        const link = whatsappLink(item.patientPhone,
            `Olá, ${item.patientName.split(" ")[0]}! Lembrete da sua sessão em ${when}.${where} Pode confirmar sua presença?`);
        if (!link) {
            toast.error("O paciente não tem um telefone válido cadastrado.");
            return;
        }
        window.open(link, "_blank", "noopener");
        act(`/clinic/agenda/${item.id}/reminded`, "Lembrete registrado.");
    };

    const scheduled = item.status === "AGENDADA";
    const startsAt = item.startTime ? dayjs(`${item.date}T${item.startTime}`) : null;
    const patientCanChange = isPatient && scheduled && startsAt && startsAt.diff(dayjs(), "hour", true) >= patientChangeHours;

    return (
        <div className={styles.overlay} onClick={onClose}>
            <div className={styles.modal} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <h2>
                    {formatDate(item.date)} {item.startTime && `às ${item.startTime}`}{" "}
                    <span className={`${styles.badge} ${styles[`badge${item.status}`] || ""}`}>{STATUS_LABEL[item.status]}</span>
                </h2>

                {mode === "details" && (
                    <>
                        <dl className={styles.details}>
                            {isStaff
                                ? <><dt>Paciente</dt><dd>{item.patientName}</dd></>
                                : <><dt>Profissional</dt><dd>{item.professionalName}</dd></>}
                            <dt>{item.singleSession ? "Tipo" : "Pacote"}</dt>
                            <dd>{item.singleSession ? "Sessão avulsa" : item.packageTitle || "—"}</dd>
                            <dt>Duração</dt><dd>{item.durationMinutes} min</dd>
                            <dt>Modalidade</dt><dd>{MODALITY_LABEL[item.modality]}</dd>
                            <dt>Presença</dt><dd>{item.confirmedAt ? "Confirmada pelo paciente" : "Ainda não confirmada"}</dd>
                            {isStaff && <><dt>Lembrete</dt><dd>{item.remindedAt ? "Enviado" : "Não enviado"}</dd></>}
                        </dl>

                        <div className={styles.actions}>
                            {item.modality === "ONLINE" && item.meetingLink && scheduled && (
                                <a className={styles.btn} href={item.meetingLink} target="_blank" rel="noopener noreferrer">Entrar na sessão</a>
                            )}
                            {isStaff && scheduled && (
                                <>
                                    <button className={styles.btnPrimary} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/REALIZADA`, "Sessão marcada como realizada.")}>Realizada</button>
                                    <button className={styles.btnDanger} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/FALTA`, "Falta registrada.")}>Falta</button>
                                    <button className={styles.btnGhost} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/CANCELADA`, "Sessão cancelada.")}>Cancelar sessão</button>
                                    <button className={styles.btnGhost} onClick={() => setMode("reschedule")}>Remarcar</button>
                                    <button className={styles.btnGhost} onClick={remind}>Lembrar por WhatsApp</button>
                                </>
                            )}
                            {isStaff && !scheduled && (
                                <button className={styles.btnGhost} disabled={busy}
                                        onClick={() => act(`/clinic/agenda/${item.id}/status/AGENDADA`, "Sessão reaberta.")}>Voltar para agendada</button>
                            )}
                            {isStaff && <Link className={styles.btnGhost} to={`/prontuario/${item.patientId}`}>Prontuário</Link>}

                            {isPatient && scheduled && !item.confirmedAt && (
                                <button className={styles.btnPrimary} disabled={busy}
                                        onClick={() => act(`/clinic/agenda/${item.id}/confirm`, "Presença confirmada.")}>Confirmar presença</button>
                            )}
                            {patientCanChange && (
                                <>
                                    <button className={styles.btnGhost} onClick={() => setMode("reschedule")}>Remarcar</button>
                                    <button className={styles.btnDanger} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/patient-cancel`, "Sessão cancelada.")}>Cancelar sessão</button>
                                </>
                            )}
                            {item.status === "REALIZADA" && (
                                <Link className={styles.btnGhost} to={`/documento/comparecimento/${item.id}`}>Declaração de comparecimento</Link>
                            )}
                        </div>

                        {isPatient && scheduled && startsAt && !patientCanChange && (
                            <Alert tone="info" title="Precisa remarcar ou cancelar?">
                                Faltam menos de {patientChangeHours} horas para a sessão. Fale diretamente com o profissional.
                            </Alert>
                        )}

                        {isStaff && evolution !== null && (
                            <div className={styles.form} style={{ marginTop: 18 }}>
                                <label className={styles.field}>
                                    Evolução da sessão
                                    <textarea rows={5} value={evolution} onChange={(e) => setEvolution(e.target.value)}
                                              placeholder="Registro do atendimento. Fica cifrado e só você tem acesso." />
                                </label>
                                <div>
                                    <button className={styles.btnGhost} onClick={saveEvolution} disabled={savingEvolution}>
                                        {savingEvolution ? "Salvando…" : "Salvar evolução"}
                                    </button>
                                </div>
                            </div>
                        )}

                        {isPatient && item.status === "REALIZADA" && !item.rated && (
                            <div className={styles.form} style={{ marginTop: 18 }}>
                                <strong>Como foi esta sessão?</strong>
                                <div className={styles.moods}>
                                    {[1, 2, 3, 4, 5].map((n) => (
                                        <button key={n} type="button" aria-label={`${n} de 5`}
                                                className={`${styles.mood} ${rating.value >= n ? styles.moodSelected : ""}`}
                                                onClick={() => setRating((r) => ({ ...r, value: n }))}>★</button>
                                    ))}
                                </div>
                                <label className={styles.field}>
                                    Comentário (opcional)
                                    <textarea value={rating.comment} onChange={(e) => setRating((r) => ({ ...r, comment: e.target.value }))} />
                                </label>
                                <div>
                                    <button className={styles.btn} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/rating`, "Obrigado pela avaliação!",
                                                { body: { rating: rating.value, comment: rating.comment } })}>Enviar avaliação</button>
                                </div>
                            </div>
                        )}
                    </>
                )}

                {mode === "reschedule" && (
                    <div className={styles.form}>
                        <label className={styles.field}>
                            Nova data
                            <input type="date" value={newDate} min={dayjs().format("YYYY-MM-DD")}
                                   onChange={(e) => setNewDate(e.target.value)} />
                        </label>
                        {isStaff ? (
                            <label className={styles.field}>
                                Novo horário
                                <input type="time" value={newTime} onChange={(e) => setNewTime(e.target.value)} />
                            </label>
                        ) : (
                            <div className={styles.field}>
                                Horários livres
                                {slots === null && <span className={styles.muted}>Procurando horários…</span>}
                                {slots && slots.length === 0 && (
                                    <span className={styles.muted}>Nenhum horário livre neste dia. Tente outra data.</span>
                                )}
                                <div className={styles.toolbar}>
                                    {(slots || []).map((slot) => (
                                        <button key={slot} type="button"
                                                className={newTime === slot ? styles.btnPrimary : styles.btnGhost}
                                                onClick={() => setNewTime(slot)}>{slot}</button>
                                    ))}
                                </div>
                            </div>
                        )}
                        <div className={styles.actions}>
                            <button className={styles.btnPrimary}
                                    disabled={busy || !newDate || !newTime || (isPatient && !(slots || []).includes(newTime))}
                                    onClick={() => act(`/clinic/agenda/${item.id}/reschedule`, "Sessão remarcada.",
                                        { body: { date: newDate, startTime: newTime } })}>
                                Confirmar remarcação
                            </button>
                            <button className={styles.btnGhost} onClick={() => setMode("details")}>Voltar</button>
                        </div>
                    </div>
                )}

                <div className={styles.actions}>
                    <button className={styles.btnGhost} onClick={onClose}>Fechar</button>
                </div>
            </div>
        </div>
    );
}
