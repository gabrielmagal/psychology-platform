import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { ErrorState, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { useApi, errorMessage, formatDate, MOODS } from "./clinicApi";

export default function MoodDiaryPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const today = dayjs().format("YYYY-MM-DD");

    const [entries, setEntries] = useState([]);
    const [mood, setMood] = useState(null);
    const [note, setNote] = useState("");
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
            await request(`/clinic/mood`, { method: "POST", body: { date: today, mood, note } });
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
                    <h1 className={styles.title}>Diário de humor</h1>
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
