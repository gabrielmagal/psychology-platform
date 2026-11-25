import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { ErrorState, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { useApi, formatDate, MOODS, SLEEP_QUALITY, sleepText } from "./clinicApi";

export default function MoodDiaryPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const today = dayjs().format("YYYY-MM-DD");

    const [entries, setEntries] = useState([]);
    const [mood, setMood] = useState(null);
    const [note, setNote] = useState("");
    const [sleepHours, setSleepHours] = useState("");
    const [sleepQuality, setSleepQuality] = useState("");
    const [error, setError] = useState(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        setError(null);
        try {
            const data = (await request(`/clinic/mood`)) || [];
            setEntries(data);
            const todayEntry = data.find((e) => e.date === today);
            if (todayEntry) {
                setMood(todayEntry.mood);
                setNote(todayEntry.note || "");
                setSleepHours(todayEntry.sleepHours ?? "");
                setSleepQuality(todayEntry.sleepQuality ?? "");
            }
        } catch (err) {
            setError(err);
        }
    }, [request, today]);

    useEffect(() => { load(); }, [load]);

    const save = async (e) => {
        e.preventDefault();
        if (!mood) {
            toast.error("Escolha como você está se sentindo.");
            return;
        }
        setSaving(true);
        try {
            await request(`/clinic/mood`, { method: "POST", body: { date: today, mood, note, sleepHours, sleepQuality } });
            toast.success("Registro salvo.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setSaving(false);
        }
    };

    const moodOf = (value) => MOODS.find((m) => m.value === value);

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>Diário de humor e sono</h1>
                    <p className={styles.subtitle}>
                        Um registro por dia. Seu psicólogo vê estes registros para acompanhar você entre as sessões.
                    </p>
                </div>
            </div>

            {error && <ErrorState error={error} onRetry={load} />}

            <div className={styles.stack}>
                <form className={`${styles.card} ${styles.form}`} onSubmit={save}>
                    <h2>Como você está hoje?</h2>
                    <div className={styles.moods}>
                        {MOODS.map((m) => (
                            <button key={m.value} type="button" title={m.label} aria-label={m.label}
                                    aria-pressed={mood === m.value}
                                    className={`${styles.mood} ${mood === m.value ? styles.moodSelected : ""}`}
                                    onClick={() => setMood(m.value)}>
                                {m.emoji}
                            </button>
                        ))}
                    </div>
                    <div className={styles.row}>
                        <label className={styles.field}>
                            Quantas horas você dormiu? (opcional)
                            <input type="number" min="0" max="24" step="0.5" placeholder="Ex.: 7" value={sleepHours}
                                   onChange={(e) => setSleepHours(e.target.value)} />
                        </label>
                        <label className={styles.field}>
                            Como foi o sono? (opcional)
                            <select value={sleepQuality} onChange={(e) => setSleepQuality(e.target.value)}>
                                <option value="">Não informar</option>
                                {SLEEP_QUALITY.map((label, index) => <option key={label} value={index + 1}>{label}</option>)}
                            </select>
                        </label>
                    </div>
                    <label className={styles.field}>
                        Quer anotar algo sobre o dia? (opcional)
                        <textarea value={note} onChange={(e) => setNote(e.target.value)} />
                    </label>
                    <div>
                        <button type="submit" className={styles.btnPrimary} disabled={saving}>
                            {saving ? "Salvando…" : "Salvar registro de hoje"}
                        </button>
                    </div>
                </form>

                <div className={styles.card}>
                    <h2>Últimos registros</h2>
                    {entries.length === 0 ? (
                        <p className={styles.muted}>Você ainda não tem registros.</p>
                    ) : (
                        <ul className={styles.list}>
                            {entries.map((entry) => (
                                <li key={entry.date} className={styles.listItem}>
                                    <span>
                                        {moodOf(entry.mood)?.emoji} <strong>{formatDate(entry.date)}</strong> —{" "}
                                        {moodOf(entry.mood)?.label}
                                        {sleepText(entry) && <span className={styles.muted}> · {sleepText(entry)}</span>}
                                    </span>
                                    {entry.note && <span className={styles.muted}>{entry.note}</span>}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>
        </div>
    );
}
