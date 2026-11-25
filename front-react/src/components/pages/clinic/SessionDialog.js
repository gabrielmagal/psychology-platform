import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { Alert } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { formatDate, whatsappLink, STATUS_LABEL, MODALITY_LABEL } from "./clinicApi";
import SessionRecordPanel from "./SessionRecordPanel";
import ReviewForm from "../../ui/ReviewForm";

/** Detalhes de uma sessão da agenda, com as ações de cada perfil. */
export default function SessionDialog({ item, request, isPatient, patientChangeHours, onClose, onChanged, onRecordSaved }) {
    const isStaff = !isPatient;
    const [mode, setMode] = useState("details"); // details | reschedule | meeting
    const [modality, setModality] = useState(item.modality);
    const [meetingLink, setMeetingLink] = useState(item.meetingLink || "");
    const [wholeSeries, setWholeSeries] = useState(false);
    const [guide, setGuide] = useState(item.insuranceGuide || "");
    const [newDate, setNewDate] = useState(item.date);
    const [newTime, setNewTime] = useState(item.startTime || "09:00");
    const [slots, setSlots] = useState(null);
    const [busy, setBusy] = useState(false);

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
    // O link vira um botão: só endereço http(s) é aberto.
    const roomLink = item.modality === "ONLINE" && /^https?:\/\//i.test(item.meetingLink || "") ? item.meetingLink : null;

    const copyLink = async () => {
        try {
            await navigator.clipboard.writeText(roomLink);
            toast.success("Link copiado.");
        } catch {
            toast.error("Não foi possível copiar. Selecione o link e copie manualmente.");
        }
    };

    const cancelSeries = async () => {
        setBusy(true);
        try {
            const result = await request(`/clinic/agenda/${item.id}/cancel-series`, { method: "PUT" });
            toast.success(`${result?.cancelled || 0} sessão(ões) cancelada(s).`);
            onChanged();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const saveGuide = () => act(`/clinic/insurance-sessions/${item.id}`, "Guia registrada.", { body: { guide } });
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
                                : <><dt>Profissional</dt><dd>
                                    {item.professionalId
                                        ? <Link to={`/profissionais/${item.professionalId}`} onClick={onClose}>{item.professionalName}</Link>
                                        : item.professionalName}
                                </dd></>}
                            <dt>{item.singleSession ? "Tipo" : "Pacote"}</dt>
                            <dd>{item.singleSession ? "Sessão avulsa" : item.packageTitle || "—"}</dd>
                            <dt>Duração</dt><dd>{item.durationMinutes} min</dd>
                            <dt>Modalidade</dt><dd>{MODALITY_LABEL[item.modality]}</dd>
                            {roomLink && <><dt>Sala de vídeo</dt><dd style={{ wordBreak: "break-all" }}>{roomLink}</dd></>}
                            {item.insuranceName && <><dt>Convênio</dt><dd>{item.insuranceName}</dd></>}
                            {item.seriesId && <><dt>Repetição</dt><dd>Faz parte de uma série de sessões</dd></>}
                            <dt>Presença</dt><dd>{item.confirmedAt ? "Confirmada pelo paciente" : "Ainda não confirmada"}</dd>
                            {isStaff && <><dt>Lembrete</dt><dd>{item.remindedAt ? "Enviado" : "Não enviado"}</dd></>}
                        </dl>

                        <div className={styles.actions}>
                            {roomLink && scheduled && (
                                <>
                                    <a className={styles.btnPrimary} href={roomLink} target="_blank" rel="noopener noreferrer">Entrar na sessão</a>
                                    <button className={styles.btnGhost} onClick={copyLink}>Copiar link</button>
                                </>
                            )}
                            {isStaff && scheduled && (
                                <>
                                    <button className={styles.btnPrimary} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/REALIZADA`, "Sessão marcada como realizada.")}>Realizada</button>
                                    <button className={styles.btnDanger} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/FALTA`, "Falta registrada.")}>Falta</button>
                                    <button className={styles.btnGhost} disabled={busy}
                                            onClick={() => act(`/clinic/agenda/${item.id}/status/CANCELADA`, "Sessão cancelada.")}>Cancelar sessão</button>
                                    {item.seriesId && (
                                        <button className={styles.btnDanger} disabled={busy} onClick={cancelSeries}>
                                            Cancelar esta e as próximas
                                        </button>
                                    )}
                                    <button className={styles.btnGhost} onClick={() => setMode("reschedule")}>Remarcar</button>
                                    <button className={styles.btnGhost} onClick={() => setMode("meeting")}>Presencial ou online</button>
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

                        {isStaff && item.insuranceName && item.status !== "CANCELADA" && (
                            <div className={styles.row} style={{ marginTop: 18, alignItems: "end" }}>
                                <label className={styles.field}>
                                    Número da guia do convênio
                                    <input value={guide} onChange={(e) => setGuide(e.target.value)} placeholder="Guia autorizada" />
                                </label>
                                <div>
                                    <button className={styles.btnGhost} disabled={busy || guide === (item.insuranceGuide || "")}
                                            onClick={saveGuide}>Salvar guia</button>
                                </div>
                            </div>
                        )}

                        {isStaff && item.status !== "CANCELADA" && (
                            <section style={{ marginTop: 22 }}>
                                <h3 className="ui-card-title">Registro da sessão</h3>
                                <SessionRecordPanel sessionId={item.id} request={request} onSaved={onRecordSaved} />
                            </section>
                        )}

                        {isPatient && item.status === "REALIZADA" && !item.rated && (
                            <section style={{ marginTop: 22 }}>
                                <ReviewForm
                                    title="Como foi esta sessão?"
                                    recommendQuestion={`Você recomendaria ${item.professionalName || "este profissional"}?`}
                                    commentPlaceholder="O que funcionou bem e o que poderia ser diferente."
                                    privacyNote="O profissional lê a sua avaliação sem saber que é sua."
                                    consentLabel="Autorizo a exibição deste comentário no perfil do profissional, sem o meu nome"
                                    consentHint="Só aparece se o profissional exibir avaliações e depois de a clínica aprovar. Evite detalhes que identifiquem você."
                                    busy={busy}
                                    onSubmit={(review) => act(`/clinic/agenda/${item.id}/rating`, "Obrigado pela avaliação!", { body: review })}
                                />
                            </section>
                        )}
                    </>
                )}

                {mode === "meeting" && (
                    <div className={styles.form}>
                        <label className={styles.field}>
                            Modalidade
                            <select value={modality} onChange={(e) => setModality(e.target.value)}>
                                <option value="PRESENCIAL">Presencial</option>
                                <option value="ONLINE">Online</option>
                            </select>
                        </label>
                        {modality === "ONLINE" && (
                            <label className={styles.field}>
                                Link do atendimento
                                <input type="url" placeholder="https://…" value={meetingLink}
                                       onChange={(e) => setMeetingLink(e.target.value)} />
                                <span className={styles.muted}>
                                    Em branco, o sistema cria uma sala de vídeo. Para usar outra ferramenta, cole o link dela.
                                </span>
                            </label>
                        )}
                        {item.seriesId && (
                            <label className={styles.check}>
                                <input type="checkbox" checked={wholeSeries} onChange={(e) => setWholeSeries(e.target.checked)} />
                                Aplicar também às próximas sessões desta repetição
                            </label>
                        )}
                        <div className={styles.actions}>
                            <button className={styles.btnPrimary} disabled={busy}
                                    onClick={() => act(`/clinic/agenda/${item.id}/meeting`, "Atendimento atualizado.",
                                        { body: { modality, meetingLink, wholeSeries } })}>
                                Salvar
                            </button>
                            {modality === "ONLINE" && item.meetingLink && (
                                <button className={styles.btnGhost} disabled={busy}
                                        onClick={() => act(`/clinic/agenda/${item.id}/meeting`, "Sala nova criada.",
                                            { body: { modality: "ONLINE", newRoom: true, wholeSeries } })}>
                                    Gerar sala nova
                                </button>
                            )}
                            <button className={styles.btnGhost} onClick={() => setMode("details")}>Voltar</button>
                        </div>
                    </div>
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
