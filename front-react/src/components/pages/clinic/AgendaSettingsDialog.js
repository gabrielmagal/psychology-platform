import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import { FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { ErrorState, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";

const WEEKDAYS = ["Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"];
const emptyWeek = () => WEEKDAYS.map(() => ({ on: false, startTime: "08:00", endTime: "18:00" }));

/** Horário de atendimento da semana e bloqueios (férias, compromissos) do profissional. */
export default function AgendaSettingsDialog({ request, onClose }) {
    const [week, setWeek] = useState(null);
    const [blocks, setBlocks] = useState([]);
    const [error, setError] = useState(null);
    const [saving, setSaving] = useState(false);
    const [block, setBlock] = useState({
        startAt: dayjs().add(1, "day").hour(8).minute(0).format("YYYY-MM-DDTHH:mm"),
        endAt: dayjs().add(1, "day").hour(18).minute(0).format("YYYY-MM-DDTHH:mm"),
        reason: "",
    });

    const load = useCallback(async () => {
        setError(null);
        try {
            const data = await request(`/clinic/agenda-settings`);
            const next = emptyWeek();
            // A tela trabalha com um intervalo por dia; se houver mais de um, vale o que abrange todos.
            for (const h of data.workingHours || []) {
                const day = next[h.weekday - 1];
                day.startTime = day.on && day.startTime < h.startTime ? day.startTime : h.startTime;
                day.endTime = day.on && day.endTime > h.endTime ? day.endTime : h.endTime;
                day.on = true;
            }
            setWeek(next);
            setBlocks(data.blocks || []);
        } catch (err) {
            setError(err);
        }
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const setDay = (index, patch) => setWeek((w) => w.map((d, i) => (i === index ? { ...d, ...patch } : d)));

    const saveHours = async () => {
        setSaving(true);
        try {
            const body = week.map((d, i) => ({ weekday: i + 1, ...d })).filter((d) => d.on)
                .map(({ weekday, startTime, endTime }) => ({ weekday, startTime, endTime }));
            await request(`/clinic/agenda-settings/working-hours`, { method: "PUT", body });
            toast.success(body.length ? "Horário de atendimento salvo." : "Horário de atendimento removido: a agenda fica sem restrição.");
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setSaving(false);
        }
    };

    const addBlock = async (e) => {
        e.preventDefault();
        try {
            const result = await request(`/clinic/agenda-settings/blocks`, { method: "POST", body: block });
            toast.success(result?.sessionsInPeriod > 0
                ? `Bloqueio criado. Há ${result.sessionsInPeriod} sessão(ões) agendada(s) nesse período: remarque-as.`
                : "Bloqueio criado.");
            setBlock((b) => ({ ...b, reason: "" }));
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const removeBlock = async (id) => {
        try {
            await request(`/clinic/agenda-settings/blocks/${id}`, { method: "DELETE" });
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    return (
        <div className={styles.overlay}>
            <div className={styles.modal} style={{ maxWidth: 640 }} role="dialog" aria-modal="true" aria-labelledby="settings-title">
                <h2 id="settings-title">Horários e bloqueios</h2>
                {error && <ErrorState error={error} onRetry={load} compact />}
                {!week && !error && <Skeleton rows={6} />}

                {week && (
                    <div className={styles.stack}>
                        <section>
                            <h3 className={styles.sectionTitle}>Horário de atendimento</h3>
                            <p className={styles.muted} style={{ marginBottom: 10 }}>
                                Sessões só podem ser agendadas (e remarcadas pelo paciente) dentro destes horários.
                                Sem nenhum dia marcado, a agenda fica sem restrição.
                            </p>
                            {week.map((day, index) => (
                                <div key={index} className={styles.hoursRow}>
                                    <label className={styles.check}>
                                        <input type="checkbox" checked={day.on} onChange={(e) => setDay(index, { on: e.target.checked })} />
                                        {WEEKDAYS[index]}
                                    </label>
                                    <input type="time" value={day.startTime} disabled={!day.on} aria-label={`Início de ${WEEKDAYS[index]}`}
                                           onChange={(e) => setDay(index, { startTime: e.target.value })} />
                                    <span className={styles.muted}>às</span>
                                    <input type="time" value={day.endTime} disabled={!day.on} aria-label={`Fim de ${WEEKDAYS[index]}`}
                                           onChange={(e) => setDay(index, { endTime: e.target.value })} />
                                </div>
                            ))}
                            <div className={styles.actions}>
                                <button className={styles.btnPrimary} onClick={saveHours} disabled={saving}>
                                    {saving ? "Salvando…" : "Salvar horário"}
                                </button>
                            </div>
                        </section>

                        <section>
                            <h3 className={styles.sectionTitle}>Bloqueios</h3>
                            <p className={styles.muted} style={{ marginBottom: 10 }}>
                                Períodos em que você não atende: férias, feriado, compromisso.
                            </p>
                            {blocks.length > 0 && (
                                <ul className={styles.list} style={{ marginBottom: 12 }}>
                                    {blocks.map((b) => (
                                        <li key={b.id} className={styles.listItem}>
                                            <span>
                                                <strong>{dayjs(b.startAt).format("DD/MM/YYYY HH:mm")}</strong> até{" "}
                                                <strong>{dayjs(b.endAt).format("DD/MM/YYYY HH:mm")}</strong>
                                                {b.reason ? ` · ${b.reason}` : ""}
                                            </span>
                                            <button className={styles.btnDanger} onClick={() => removeBlock(b.id)} aria-label="Remover bloqueio">
                                                <FiTrash2 aria-hidden="true" />
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                            <form className={styles.form} onSubmit={addBlock}>
                                <div className={styles.row}>
                                    <label className={styles.field}>
                                        Início
                                        <input type="datetime-local" required value={block.startAt}
                                               onChange={(e) => setBlock({ ...block, startAt: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>
                                        Fim
                                        <input type="datetime-local" required value={block.endAt}
                                               onChange={(e) => setBlock({ ...block, endAt: e.target.value })} />
                                    </label>
                                </div>
                                <label className={styles.field}>
                                    Motivo (opcional)
                                    <input value={block.reason} placeholder="Férias, congresso…"
                                           onChange={(e) => setBlock({ ...block, reason: e.target.value })} />
                                </label>
                                <div><button type="submit" className={styles.btnGhost}>Adicionar bloqueio</button></div>
                            </form>
                        </section>
                    </div>
                )}

                <div className={styles.actions}>
                    <button className={styles.btnGhost} onClick={onClose}>Fechar</button>
                </div>
            </div>
        </div>
    );
}
