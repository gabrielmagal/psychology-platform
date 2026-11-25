import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { ErrorState } from "../../ui/Feedback";

/** Agendar uma sessão: num pacote existente (com repetição semanal) ou avulsa, com valor próprio. */
export default function ScheduleDialog({ request, initial, canScheduleSingle, onClose, onSaved }) {
    const [kind, setKind] = useState("package");
    const [packages, setPackages] = useState([]);
    const [patients, setPatients] = useState([]);
    const [error, setError] = useState(null);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        sessionPackageId: "",
        patientId: "",
        price: "",
        date: initial.format("YYYY-MM-DD"),
        startTime: initial.hour() ? `${String(initial.hour()).padStart(2, "0")}:00` : "09:00",
        durationMinutes: 50,
        modality: "PRESENCIAL",
        meetingLink: "",
        title: "",
        repeatWeeks: 1,
    });
    const set = (patch) => setForm((f) => ({ ...f, ...patch }));

    useEffect(() => {
        let cancelled = false;
        Promise.all([
            request(`/session-package?page=0&size=200`),
            canScheduleSingle ? request(`/user?page=0&size=200&userType=PACIENTE`) : Promise.resolve(null),
        ]).then(([pkgs, users]) => {
            if (cancelled) return;
            // Pacotes de sessão avulsa já cumpriram o papel deles: não entram na lista.
            setPackages((pkgs?.content || []).filter((p) => !p.singleSession));
            setPatients(users?.content || []);
        }).catch((err) => { if (!cancelled) setError(err); });
        return () => { cancelled = true; };
    }, [request, canScheduleSingle]);

    const submit = async (e) => {
        e.preventDefault();
        setError(null);
        setSaving(true);
        try {
            const result = kind === "single"
                ? await request(`/clinic/agenda/single`, { method: "POST", body: form })
                : await request(`/clinic/agenda`, { method: "POST", body: form });
            const count = result?.created?.length || 1;
            toast.success(count > 1 ? `${count} sessões agendadas.` : "Sessão agendada.");
            onSaved();
        } catch (err) {
            // Conflito de horário, bloqueio, fora do expediente: a mensagem fica no formulário.
            setError(err);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className={styles.overlay}>
            <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="schedule-title">
                <h2 id="schedule-title">Agendar sessão</h2>
                <form className={styles.form} onSubmit={submit}>
                    {error && <ErrorState error={error} compact />}

                    {canScheduleSingle && (
                        <div className={styles.segmented} role="tablist">
                            <button type="button" role="tab" aria-selected={kind === "package"}
                                    className={kind === "package" ? styles.segmentedActive : ""}
                                    onClick={() => setKind("package")}>De um pacote</button>
                            <button type="button" role="tab" aria-selected={kind === "single"}
                                    className={kind === "single" ? styles.segmentedActive : ""}
                                    onClick={() => setKind("single")}>Sessão avulsa</button>
                        </div>
                    )}

                    {kind === "package" ? (
                        <label className={styles.field}>
                            Paciente / pacote
                            <select required value={form.sessionPackageId} onChange={(e) => set({ sessionPackageId: e.target.value })}>
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
                                    Nenhum pacote encontrado. Crie um em <Link to="/sessions-package">Pacotes de sessões</Link>
                                    {canScheduleSingle ? " ou agende uma sessão avulsa." : "."}
                                </span>
                            )}
                        </label>
                    ) : (
                        <div className={styles.row}>
                            <label className={styles.field}>
                                Paciente
                                <select required value={form.patientId} onChange={(e) => set({ patientId: e.target.value })}>
                                    <option value="">Selecione…</option>
                                    {patients.map((u) => (
                                        <option key={u.id} value={u.id}>{[u.firstName, u.lastName].filter(Boolean).join(" ")}</option>
                                    ))}
                                </select>
                            </label>
                            <label className={styles.field}>
                                Valor da sessão (R$)
                                <input type="number" min="0" step="0.01" placeholder="0,00" value={form.price}
                                       onChange={(e) => set({ price: e.target.value })} />
                            </label>
                        </div>
                    )}

                    <div className={styles.row}>
                        <label className={styles.field}>
                            Data
                            <input type="date" required value={form.date} onChange={(e) => set({ date: e.target.value })} />
                        </label>
                        <label className={styles.field}>
                            Horário
                            <input type="time" required value={form.startTime} onChange={(e) => set({ startTime: e.target.value })} />
                        </label>
                    </div>
                    <div className={styles.row}>
                        <label className={styles.field}>
                            Duração (min)
                            <input type="number" min="10" max="240" required value={form.durationMinutes}
                                   onChange={(e) => set({ durationMinutes: e.target.value })} />
                        </label>
                        <label className={styles.field}>
                            Modalidade
                            <select value={form.modality} onChange={(e) => set({ modality: e.target.value })}>
                                <option value="PRESENCIAL">Presencial</option>
                                <option value="ONLINE">Online</option>
                            </select>
                        </label>
                    </div>
                    {form.modality === "ONLINE" && (
                        <label className={styles.field}>
                            Link do atendimento
                            <input type="url" placeholder="https://…" value={form.meetingLink}
                                   onChange={(e) => set({ meetingLink: e.target.value })} />
                        </label>
                    )}
                    {kind === "package" && (
                        <label className={styles.field}>
                            Repetir toda semana por
                            <select value={form.repeatWeeks} onChange={(e) => set({ repeatWeeks: e.target.value })}>
                                <option value="1">Não repetir</option>
                                {[2, 4, 8, 12].map((n) => <option key={n} value={n}>{n} semanas</option>)}
                            </select>
                        </label>
                    )}
                    <div className={styles.actions}>
                        <button type="submit" className={styles.btnPrimary} disabled={saving}>
                            {saving ? "Agendando…" : "Agendar"}
                        </button>
                        <button type="button" className={styles.btnGhost} onClick={onClose}>Cancelar</button>
                    </div>
                </form>
            </div>
        </div>
    );
}
