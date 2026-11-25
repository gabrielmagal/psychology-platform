import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import { FiChevronLeft, FiChevronRight, FiPlus, FiSettings } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { ErrorState } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { useRoles } from "../../shared/UseRoles";
import { useApi, formatDate, STATUS_LABEL, MODALITY_LABEL } from "./clinicApi";
import SessionDialog from "./SessionDialog";
import ScheduleDialog from "./ScheduleDialog";
import AgendaSettingsDialog from "./AgendaSettingsDialog";

const ISO = "YYYY-MM-DD";
const HOURS = Array.from({ length: 15 }, (_, i) => i + 7); // 07h às 21h
const mondayOf = (date) => date.subtract((date.day() + 6) % 7, "day").startOf("day");

const VIEWS = [
    { id: "day", label: "Dia", unit: "day" },
    { id: "week", label: "Semana", unit: "week" },
    { id: "month", label: "Mês", unit: "month" },
];

function rangeOf(view, anchor) {
    if (view === "day") return [anchor.startOf("day"), anchor.startOf("day")];
    if (view === "week") return [mondayOf(anchor), mondayOf(anchor).add(6, "day")];
    const first = mondayOf(anchor.startOf("month"));
    return [first, first.add(41, "day")]; // 6 semanas: cobre qualquer mês
}

export default function AgendaPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const { isPatient, isProfessional } = useRoles(keycloak);
    const isStaff = !isPatient;

    const [view, setView] = useState("week");
    const [anchor, setAnchor] = useState(() => dayjs().startOf("day"));
    const [items, setItems] = useState([]);
    const [settings, setSettings] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [selected, setSelected] = useState(null);
    const [scheduling, setScheduling] = useState(null);
    const [configuring, setConfiguring] = useState(false);
    const [dragging, setDragging] = useState(null);

    const [from, to] = useMemo(() => rangeOf(view, anchor), [view, anchor]);
    const fromKey = from.format(ISO);
    const toKey = to.format(ISO);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const [sessions, config] = await Promise.all([
                request(`/clinic/agenda?from=${fromKey}&to=${toKey}`),
                isStaff ? request(`/clinic/agenda-settings?from=${fromKey}&to=${toKey}`) : Promise.resolve(null),
            ]);
            setItems(sessions || []);
            setSettings(config);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, [request, fromKey, toKey, isStaff]);

    useEffect(() => { load(); }, [load]);

    const byDate = useMemo(() => {
        const map = {};
        for (const item of items) (map[item.date] = map[item.date] || []).push(item);
        return map;
    }, [items]);

    const blocksOn = useCallback((key) => (settings?.blocks || []).filter(
        (b) => b.startAt.slice(0, 10) <= key && b.endAt.slice(0, 10) >= key
    ), [settings]);

    const move = (step) => setAnchor((a) => a.add(step, VIEWS.find((v) => v.id === view).unit));

    /** Solta a sessão arrastada num dia (mantém o horário) ou numa hora do dia. */
    const drop = async (date, hour) => {
        const item = dragging;
        setDragging(null);
        if (!item) return;
        const startTime = hour === undefined ? item.startTime : `${String(hour).padStart(2, "0")}:00`;
        if (item.date === date && item.startTime === startTime) return;
        try {
            await request(`/clinic/agenda/${item.id}/reschedule`, { method: "PUT", body: { date, startTime } });
            toast.success(`Sessão de ${item.patientName} remarcada para ${formatDate(date)} às ${startTime}.`);
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const dragProps = (item) => (isStaff && item.status === "AGENDADA" && item.startTime ? {
        draggable: true,
        onDragStart: (e) => { e.dataTransfer.effectAllowed = "move"; setDragging(item); },
        onDragEnd: () => setDragging(null),
    } : {});

    const dropProps = (date, hour) => (isStaff ? {
        onDragOver: (e) => { if (dragging) e.preventDefault(); },
        onDrop: (e) => { e.preventDefault(); drop(date, hour); },
    } : {});

    const renderSlot = (item, compact) => (
        <button
            key={item.id}
            className={`${styles.slot} ${styles[`status${item.status}`] || ""} ${compact ? styles.slotCompact : ""}`}
            onClick={(e) => { e.stopPropagation(); setSelected(item); }}
            title={isStaff && item.status === "AGENDADA" ? "Arraste para remarcar" : undefined}
            {...dragProps(item)}
        >
            <strong>{item.startTime || "Sem horário"}</strong>
            <span className={styles.slotName}>{isStaff ? item.patientName : item.professionalName || item.title}</span>
            {!compact && (
                <span className={styles.slotMeta}>
                    {MODALITY_LABEL[item.modality]}
                    {item.insuranceName ? ` · ${item.insuranceName}` : ""}
                    {item.status !== "AGENDADA" ? ` · ${STATUS_LABEL[item.status]}` : ""}
                    {item.status === "AGENDADA" && item.confirmedAt ? " · confirmada" : ""}
                    {item.status === "REALIZADA" && isStaff && !item.hasRecord ? " · sem registro" : ""}
                    {item.status === "REALIZADA" && isStaff && item.hasRecord && !item.recordSigned ? " · registro em aberto" : ""}
                </span>
            )}
        </button>
    );

    const today = dayjs().format(ISO);
    const title = view === "day"
        ? anchor.format("dddd, D [de] MMMM")
        : view === "week"
            ? `${from.format("DD/MM")} a ${to.format("DD/MM/YYYY")}`
            : anchor.format("MMMM [de] YYYY");

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>Agenda</h1>
                    <p className={styles.subtitle}>
                        {title.charAt(0).toUpperCase() + title.slice(1)}{loading ? " · carregando…" : ""}
                    </p>
                </div>
                <div className={styles.toolbar}>
                    <div className={styles.segmented} role="tablist" aria-label="Visão da agenda">
                        {VIEWS.map((v) => (
                            <button key={v.id} role="tab" aria-selected={view === v.id}
                                    className={view === v.id ? styles.segmentedActive : ""}
                                    onClick={() => setView(v.id)}>{v.label}</button>
                        ))}
                    </div>
                    <button className={styles.btnGhost} onClick={() => move(-1)} aria-label="Anterior"><FiChevronLeft /></button>
                    <button className={styles.btnGhost} onClick={() => setAnchor(dayjs().startOf("day"))}>Hoje</button>
                    <button className={styles.btnGhost} onClick={() => move(1)} aria-label="Próximo"><FiChevronRight /></button>
                    {isProfessional && (
                        <button className={styles.btnGhost} onClick={() => setConfiguring(true)}>
                            <FiSettings aria-hidden="true" /> Horários e bloqueios
                        </button>
                    )}
                    {isStaff && (
                        <button className={styles.btnPrimary} onClick={() => setScheduling(anchor)}>
                            <FiPlus aria-hidden="true" /> Agendar sessão
                        </button>
                    )}
                </div>
            </div>

            {error && <ErrorState error={error} onRetry={load} />}

            {view === "week" && (
                <div className={styles.week}>
                    {Array.from({ length: 7 }, (_, i) => from.add(i, "day")).map((day) => {
                        const key = day.format(ISO);
                        const list = byDate[key] || [];
                        return (
                            <div key={key} className={`${styles.day} ${key === today ? styles.dayToday : ""}`}
                                 {...dropProps(key)}>
                                <div className={styles.dayHeader}>
                                    <span>{day.format("ddd")}</span><span>{day.format("DD/MM")}</span>
                                </div>
                                <div className={styles.dayBody}>
                                    {blocksOn(key).map((b) => (
                                        <span key={b.id} className={styles.blockChip}>Bloqueado{b.reason ? ` · ${b.reason}` : ""}</span>
                                    ))}
                                    {list.length === 0 && <span className={styles.muted} style={{ fontSize: "0.8rem" }}>Sem sessões</span>}
                                    {list.map((item) => renderSlot(item, false))}
                                    {isStaff && (
                                        <button className={styles.btnGhost} onClick={() => setScheduling(day)}
                                                aria-label={`Agendar em ${day.format("DD/MM")}`}>+</button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {view === "day" && (
                <div className={styles.card} style={{ padding: 0 }}>
                    {blocksOn(fromKey).length > 0 && (
                        <div style={{ padding: "12px 16px" }}>
                            {blocksOn(fromKey).map((b) => (
                                <span key={b.id} className={styles.blockChip}>
                                    Bloqueado das {b.startAt.slice(11, 16)} às {b.endAt.slice(11, 16)}{b.reason ? ` · ${b.reason}` : ""}
                                </span>
                            ))}
                        </div>
                    )}
                    {HOURS.map((hour) => {
                        const list = (byDate[fromKey] || []).filter((i) => i.startTime && Number(i.startTime.slice(0, 2)) === hour);
                        return (
                            <div key={hour} className={styles.hourRow} {...dropProps(fromKey, hour)}>
                                <div className={styles.hourLabel}>{String(hour).padStart(2, "0")}:00</div>
                                <div className={styles.hourBody}>
                                    {list.map((item) => renderSlot(item, false))}
                                    {isStaff && list.length === 0 && (
                                        <button className={styles.hourAdd}
                                                onClick={() => setScheduling(anchor.hour(hour))}
                                                aria-label={`Agendar às ${hour}h`}>+ agendar</button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                    {(byDate[fromKey] || []).filter((i) => !i.startTime).map((item) => (
                        <div key={item.id} style={{ padding: 12 }}>{renderSlot(item, false)}</div>
                    ))}
                </div>
            )}

            {view === "month" && (
                <div className={styles.month}>
                    {["seg", "ter", "qua", "qui", "sex", "sáb", "dom"].map((d) => (
                        <div key={d} className={styles.monthHead}>{d}</div>
                    ))}
                    {Array.from({ length: 42 }, (_, i) => from.add(i, "day")).map((day) => {
                        const key = day.format(ISO);
                        const list = byDate[key] || [];
                        const outside = day.month() !== anchor.month();
                        return (
                            <div key={key}
                                 className={`${styles.monthCell} ${outside ? styles.monthOutside : ""} ${key === today ? styles.dayToday : ""}`}
                                 onClick={() => { setAnchor(day); setView("day"); }}
                                 {...dropProps(key)}>
                                <div className={styles.monthDate}>{day.date()}</div>
                                {blocksOn(key).length > 0 && <span className={styles.blockChip}>Bloqueado</span>}
                                {list.slice(0, 3).map((item) => renderSlot(item, true))}
                                {list.length > 3 && <span className={styles.muted} style={{ fontSize: "0.75rem" }}>+{list.length - 3} sessões</span>}
                            </div>
                        );
                    })}
                </div>
            )}

            {isStaff && view !== "month" && (
                <p className={styles.muted} style={{ marginTop: 14 }}>
                    Dica: arraste uma sessão agendada para outro {view === "day" ? "horário" : "dia"} para remarcar.
                    {" "}<Link to="/sessions-package">Pacotes de sessões</Link>
                </p>
            )}

            {selected && (
                <SessionDialog
                    item={selected}
                    request={request}
                    isPatient={isPatient}
                    patientChangeHours={settings?.patientChangeHours || 24}
                    onClose={() => setSelected(null)}
                    onChanged={() => { setSelected(null); load(); }}
                    onRecordSaved={load}
                />
            )}
            {scheduling && (
                <ScheduleDialog
                    request={request}
                    initial={scheduling}
                    canScheduleSingle={isProfessional}
                    onClose={() => setScheduling(null)}
                    onSaved={() => { setScheduling(null); load(); }}
                />
            )}
            {configuring && (
                <AgendaSettingsDialog
                    request={request}
                    onClose={() => { setConfiguring(false); load(); }}
                />
            )}
        </div>
    );
}
