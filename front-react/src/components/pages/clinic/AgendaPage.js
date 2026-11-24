import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { useRoles } from "../../shared/UseRoles";
import {
    useApi, errorMessage, formatDate, whatsappLink, STATUS_LABEL, MODALITY_LABEL,
} from "./clinicApi";

const mondayOf = (date) => date.subtract((date.day() + 6) % 7, "day").startOf("day");

const EMPTY_FORM = {
    sessionPackageId: "",
    date: dayjs().format("YYYY-MM-DD"),
    startTime: "09:00",
    durationMinutes: 50,
    modality: "PRESENCIAL",
    meetingLink: "",
    title: "",
    repeatWeeks: 1,
};

export default function AgendaPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const { isPatient } = useRoles(keycloak);
    const isStaff = !isPatient;

    const [weekStart, setWeekStart] = useState(() => mondayOf(dayjs()));
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [selected, setSelected] = useState(null);
    const [scheduling, setScheduling] = useState(false);
    const [packages, setPackages] = useState([]);
    const [form, setForm] = useState(EMPTY_FORM);
    const [saving, setSaving] = useState(false);
    const [rating, setRating] = useState({ value: 5, comment: "" });

    const days = useMemo(
        () => Array.from({ length: 7 }, (_, i) => weekStart.add(i, "day")),
        [weekStart]
    );

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const from = weekStart.format("YYYY-MM-DD");
            const to = weekStart.add(6, "day").format("YYYY-MM-DD");
            setItems((await request(`/clinic/agenda?from=${from}&to=${to}`)) || []);
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [request, weekStart]);

    useEffect(() => { load(); }, [load]);

    const openScheduling = async (date) => {
        setForm({ ...EMPTY_FORM, date: (date || dayjs()).format("YYYY-MM-DD") });
        setScheduling(true);
        try {
            const data = await request(`/session-package?page=0&size=200`);
            setPackages(data?.content || []);
        } catch (err) {
            setError(errorMessage(err));
        }
    };

    const submitSchedule = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            const result = await request(`/clinic/agenda`, { method: "POST", body: form });
            const count = result?.created?.length || 1;
            toast.success(count > 1 ? `${count} sessões agendadas.` : "Sessão agendada.");
            setScheduling(false);
            load();
        } catch (err) {
            toast.error(errorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    /** Executa uma ação sobre a sessão aberta e recarrega a semana. */
    const act = async (path, successMessage, options = {}) => {
        try {
            await request(path, { method: "PUT", ...options });
            toast.success(successMessage);
            setSelected(null);
            load();
        } catch (err) {
            toast.error(errorMessage(err));
        }
    };

    const remind = (item) => {
        const when = `${formatDate(item.date)}${item.startTime ? ` às ${item.startTime}` : ""}`;
        const where = item.modality === "ONLINE" && item.meetingLink ? ` Link: ${item.meetingLink}` : "";
        const link = whatsappLink(
            item.patientPhone,
            `Olá, ${item.patientName.split(" ")[0]}! Lembrete da sua sessão em ${when}.${where} Pode confirmar sua presença?`
        );
        if (!link) {
            toast.error("O paciente não tem um telefone válido cadastrado.");
            return;
        }
        window.open(link, "_blank", "noopener");
        act(`/clinic/agenda/${item.id}/reminded`, "Lembrete registrado.");
    };

    const today = dayjs().format("YYYY-MM-DD");
    const byDate = useMemo(() => {
        const map = {};
        for (const item of items) (map[item.date] = map[item.date] || []).push(item);
        return map;
    }, [items]);

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>🗓️ Agenda</h1>
                    <p className={styles.subtitle}>
                        {weekStart.format("DD/MM")} a {weekStart.add(6, "day").format("DD/MM/YYYY")}
                        {loading ? " · carregando…" : ""}
                    </p>
                </div>
                <div className={styles.toolbar}>
                    <button className={styles.btnGhost} onClick={() => setWeekStart((w) => w.subtract(7, "day"))}>
                        ◀ Semana anterior
                    </button>
                    <button className={styles.btnGhost} onClick={() => setWeekStart(mondayOf(dayjs()))}>Hoje</button>
                    <button className={styles.btnGhost} onClick={() => setWeekStart((w) => w.add(7, "day"))}>
                        Próxima semana ▶
                    </button>
                    {isStaff && (
                        <button className={styles.btnPrimary} onClick={() => openScheduling()}>+ Agendar sessão</button>
                    )}
                </div>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            <div className={styles.week}>
                {days.map((day) => {
                    const key = day.format("YYYY-MM-DD");
                    const list = byDate[key] || [];
                    return (
                        <div key={key} className={`${styles.day} ${key === today ? styles.dayToday : ""}`}>
                            <div className={styles.dayHeader}>
                                <span>{day.format("ddd")}</span>
                                <span>{day.format("DD/MM")}</span>
                            </div>
                            <div className={styles.dayBody}>
                                {list.length === 0 && <span className={styles.muted}>Sem sessões</span>}
                                {list.map((item) => (
                                    <button
                                        key={item.id}
                                        className={`${styles.slot} ${styles[`status${item.status}`] || ""}`}
                                        onClick={() => { setSelected(item); setRating({ value: 5, comment: "" }); }}
                                    >
                                        <strong>{item.startTime || "Sem horário"}</strong>
                                        <span className={styles.slotName}>
                                            {isStaff ? item.patientName : item.psychologistName || item.title}
                                        </span>
                                        <span className={styles.slotMeta}>
                                            {MODALITY_LABEL[item.modality]}
                                            {item.status !== "AGENDADA" ? ` · ${STATUS_LABEL[item.status]}` : ""}
                                            {item.status === "AGENDADA" && item.confirmedAt ? " · ✔ confirmada" : ""}
                                        </span>
                                    </button>
                                ))}
                                {isStaff && (
                                    <button className={styles.btnGhost} onClick={() => openScheduling(day)}>+</button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {selected && (
                <div className={styles.overlay} onClick={() => setSelected(null)}>
                    <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <h2>
                            {formatDate(selected.date)} {selected.startTime && `às ${selected.startTime}`}{" "}
                            <span className={`${styles.badge} ${styles[`badge${selected.status}`] || ""}`}>
                                {STATUS_LABEL[selected.status]}
                            </span>
                        </h2>
                        <dl className={styles.details}>
                            {isStaff ? (
                                <><dt>Paciente</dt><dd>{selected.patientName}</dd></>
                            ) : (
                                <><dt>Psicólogo(a)</dt><dd>{selected.psychologistName}</dd></>
                            )}
                            <dt>Pacote</dt><dd>{selected.packageTitle || "—"}</dd>
                            <dt>Duração</dt><dd>{selected.durationMinutes} min</dd>
                            <dt>Modalidade</dt><dd>{MODALITY_LABEL[selected.modality]}</dd>
                            <dt>Presença</dt>
                            <dd>{selected.confirmedAt ? "Confirmada pelo paciente" : "Ainda não confirmada"}</dd>
                            {isStaff && (
                                <><dt>Lembrete</dt><dd>{selected.remindedAt ? "Enviado" : "Não enviado"}</dd></>
                            )}
                        </dl>

                        <div className={styles.actions}>
                            {selected.modality === "ONLINE" && selected.meetingLink && selected.status === "AGENDADA" && (
                                <a className={styles.btn} href={selected.meetingLink} target="_blank" rel="noopener noreferrer">
                                    Entrar na sessão
                                </a>
                            )}

                            {isStaff && selected.status === "AGENDADA" && (
                                <>
                                    <button className={styles.btnPrimary}
                                            onClick={() => act(`/clinic/agenda/${selected.id}/status/REALIZADA`, "Sessão marcada como realizada.")}>
                                        Realizada
                                    </button>
                                    <button className={styles.btnDanger}
                                            onClick={() => act(`/clinic/agenda/${selected.id}/status/FALTA`, "Falta registrada.")}>
                                        Falta
                                    </button>
                                    <button className={styles.btnGhost}
                                            onClick={() => act(`/clinic/agenda/${selected.id}/status/CANCELADA`, "Sessão cancelada.")}>
                                        Cancelar sessão
                                    </button>
                                    <button className={styles.btnGhost} onClick={() => remind(selected)}>
                                        Lembrar por WhatsApp
                                    </button>
                                </>
                            )}
                            {isStaff && selected.status !== "AGENDADA" && (
                                <button className={styles.btnGhost}
                                        onClick={() => act(`/clinic/agenda/${selected.id}/status/AGENDADA`, "Sessão reaberta.")}>
                                    Voltar para agendada
                                </button>
                            )}
                            {isStaff && (
                                <Link className={styles.btnGhost} to={`/prontuario/${selected.patientId}`}>Prontuário</Link>
                            )}

                            {isPatient && selected.status === "AGENDADA" && !selected.confirmedAt && (
                                <button className={styles.btnPrimary}
                                        onClick={() => act(`/clinic/agenda/${selected.id}/confirm`, "Presença confirmada.")}>
                                    Confirmar presença
                                </button>
                            )}
                            {selected.status === "REALIZADA" && (
                                <Link className={styles.btnGhost} to={`/documento/comparecimento/${selected.id}`}>
                                    Declaração de comparecimento
                                </Link>
                            )}
                        </div>

                        {isPatient && selected.status === "REALIZADA" && !selected.rated && (
                            <div className={styles.form} style={{ marginTop: "1.25rem" }}>
                                <strong>Como foi esta sessão?</strong>
                                <div className={styles.moods}>
                                    {[1, 2, 3, 4, 5].map((n) => (
                                        <button key={n} type="button"
                                                aria-label={`${n} de 5`}
                                                className={`${styles.mood} ${rating.value >= n ? styles.moodSelected : ""}`}
                                                onClick={() => setRating((r) => ({ ...r, value: n }))}>
                                            ★
                                        </button>
                                    ))}
                                </div>
                                <label className={styles.field}>
                                    Comentário (opcional)
                                    <textarea value={rating.comment}
                                              onChange={(e) => setRating((r) => ({ ...r, comment: e.target.value }))} />
                                </label>
                                <div>
                                    <button className={styles.btn}
                                            onClick={() => act(`/clinic/agenda/${selected.id}/rating`, "Obrigado pela avaliação!",
                                                { body: { rating: rating.value, comment: rating.comment } })}>
                                        Enviar avaliação
                                    </button>
                                </div>
                            </div>
                        )}

                        <div className={styles.actions}>
                            <button className={styles.btnGhost} onClick={() => setSelected(null)}>Fechar</button>
                        </div>
                    </div>
                </div>
            )}

            {scheduling && (
                <div className={styles.overlay}>
                    <div className={styles.modal}>
                        <h2>Agendar sessão</h2>
                        <form className={styles.form} onSubmit={submitSchedule}>
                            <label className={styles.field}>
                                Paciente / pacote
                                <select required value={form.sessionPackageId}
                                        onChange={(e) => setForm({ ...form, sessionPackageId: e.target.value })}>
                                    <option value="">Selecione…</option>
                                    {packages.map((p) => (
                                        <option key={p.id} value={p.id}>
                                            {[p.patient?.firstName, p.patient?.lastName].filter(Boolean).join(" ")}
                                            {p.packageTitle ? ` — ${p.packageTitle}` : ""}
                                        </option>
                                    ))}
                                </select>
                                {packages.length === 0 && (
                                    <span className={styles.muted}>
                                        Nenhum pacote encontrado. Crie um em <Link to="/sessions-package">Pacote de Sessões</Link>.
                                    </span>
                                )}
                            </label>
                            <div className={styles.row}>
                                <label className={styles.field}>
                                    Data
                                    <input type="date" required value={form.date}
                                           onChange={(e) => setForm({ ...form, date: e.target.value })} />
                                </label>
                                <label className={styles.field}>
                                    Horário
                                    <input type="time" required value={form.startTime}
                                           onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
                                </label>
                            </div>
                            <div className={styles.row}>
                                <label className={styles.field}>
                                    Duração (min)
                                    <input type="number" min="10" max="240" required value={form.durationMinutes}
                                           onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} />
                                </label>
                                <label className={styles.field}>
                                    Modalidade
                                    <select value={form.modality}
                                            onChange={(e) => setForm({ ...form, modality: e.target.value })}>
                                        <option value="PRESENCIAL">Presencial</option>
                                        <option value="ONLINE">Online</option>
                                    </select>
                                </label>
                            </div>
                            {form.modality === "ONLINE" && (
                                <label className={styles.field}>
                                    Link do atendimento
                                    <input type="url" placeholder="https://…" value={form.meetingLink}
                                           onChange={(e) => setForm({ ...form, meetingLink: e.target.value })} />
                                </label>
                            )}
                            <label className={styles.field}>
                                Título (opcional)
                                <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
                            </label>
                            <label className={styles.field}>
                                Repetir toda semana por
                                <select value={form.repeatWeeks}
                                        onChange={(e) => setForm({ ...form, repeatWeeks: e.target.value })}>
                                    <option value="1">Não repetir</option>
                                    {[2, 4, 8, 12].map((n) => <option key={n} value={n}>{n} semanas</option>)}
                                </select>
                            </label>
                            <div className={styles.actions}>
                                <button type="submit" className={styles.btnPrimary} disabled={saving}>
                                    {saving ? "Agendando…" : "Agendar"}
                                </button>
                                <button type="button" className={styles.btnGhost} onClick={() => setScheduling(false)}>
                                    Cancelar
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
