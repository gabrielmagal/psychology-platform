import React, { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import styles from "./Clinic.module.css";
import { useApi, errorMessage, formatDate, formatCpf, STATUS_LABEL, MOODS } from "./clinicApi";

const FIELDS = [
    ["chiefComplaint", "Queixa principal"],
    ["complaintHistory", "História da queixa"],
    ["familyHistory", "Histórico familiar"],
    ["healthHistory", "Saúde, medicações e tratamentos anteriores"],
    ["diagnosticHypothesis", "Hipótese diagnóstica"],
    ["therapeuticGoals", "Objetivos terapêuticos"],
    ["observations", "Observações"],
];

const ANNOTATION_FIELDS = [
    ["mainFeeling", "Sentimento principal"],
    ["significantEvents", "Eventos significativos"],
    ["currentPhase", "Fase atual"],
    ["dominantThought", "Pensamento dominante"],
    ["intervention", "Intervenção"],
];

export default function PatientRecordPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const { patientId } = useParams();
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });

    const [record, setRecord] = useState(null);
    const [form, setForm] = useState({});
    const [timeline, setTimeline] = useState([]);
    const [moods, setMoods] = useState([]);
    const [error, setError] = useState(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        setError(null);
        try {
            const [data, sessions, diary] = await Promise.all([
                request(`/clinic/patient-record/${patientId}`),
                request(`/clinic/patient-record/${patientId}/timeline`),
                request(`/clinic/mood?patientId=${patientId}`),
            ]);
            setRecord(data);
            setForm(Object.fromEntries(FIELDS.map(([key]) => [key, data?.[key] || ""])));
            setTimeline(sessions || []);
            setMoods(diary || []);
        } catch (err) {
            setError(errorMessage(err));
        }
    }, [request, patientId]);

    useEffect(() => { load(); }, [load]);

    const save = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await request(`/clinic/patient-record/${patientId}`, { method: "PUT", body: form });
            toast.success("Prontuário salvo.");
            load();
        } catch (err) {
            toast.error(errorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    if (error) {
        return <div className={styles.page}><div className={styles.error}>{error}</div></div>;
    }
    if (!record) {
        return <div className={styles.page}><p className={styles.muted}>Carregando prontuário…</p></div>;
    }

    const patient = record.patient || {};
    const moodOf = (value) => MOODS.find((m) => m.value === value);

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>📋 Prontuário — {patient.name}</h1>
                    <p className={styles.subtitle}>
                        CPF {formatCpf(patient.cpf)} · Nascimento {formatDate(patient.birthDate)} · {patient.phoneNumber}
                        {patient.guardianName ? ` · Responsável ${patient.guardianName}` : ""}
                    </p>
                </div>
                <div className={styles.toolbar}>
                    <Link className={styles.btnGhost} to={`/documento/contrato/${patientId}`}>Contrato terapêutico</Link>
                    <Link className={styles.btnGhost} to="/agenda">Agenda</Link>
                </div>
            </div>

            <div className={styles.stack}>
                <form className={`${styles.card} ${styles.form}`} onSubmit={save}>
                    <h2>Anamnese</h2>
                    {FIELDS.map(([key, label]) => (
                        <label key={key} className={styles.field}>
                            {label}
                            <textarea value={form[key] || ""}
                                      onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
                        </label>
                    ))}
                    <div className={styles.actions}>
                        <button type="submit" className={styles.btnPrimary} disabled={saving}>
                            {saving ? "Salvando…" : "Salvar prontuário"}
                        </button>
                        {record.updatedAt && (
                            <span className={styles.muted}>Última alteração em {formatDate(record.updatedAt)}</span>
                        )}
                    </div>
                </form>

                <div className={styles.card}>
                    <h2>Diário de humor do paciente</h2>
                    {moods.length === 0 ? (
                        <p className={styles.muted}>O paciente ainda não registrou o humor.</p>
                    ) : (
                        <ul className={styles.list}>
                            {moods.slice(0, 14).map((m) => (
                                <li key={m.date} className={styles.listItem}>
                                    <span>{moodOf(m.mood)?.emoji} <strong>{formatDate(m.date)}</strong> — {moodOf(m.mood)?.label}</span>
                                    {m.note && <span className={styles.muted}>{m.note}</span>}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div className={styles.card}>
                    <h2>Evolução ({timeline.length} {timeline.length === 1 ? "sessão" : "sessões"})</h2>
                    {timeline.length === 0 && <p className={styles.muted}>Nenhuma sessão registrada.</p>}
                    <div className={styles.timeline}>
                        {timeline.map((s) => (
                            <div key={s.id} className={styles.timelineItem}>
                                <strong>
                                    {formatDate(s.date)}{s.startTime ? ` às ${s.startTime}` : ""} — {s.title || "Sessão"}
                                </strong>{" "}
                                <span className={`${styles.badge} ${styles[`badge${s.status}`] || ""}`}>
                                    {STATUS_LABEL[s.status]}
                                </span>
                                {s.summary && <p className={styles.preserve}>{s.summary}</p>}
                                {s.privateNotes && (
                                    <p className={styles.preserve}><em>Notas privadas:</em> {s.privateNotes}</p>
                                )}
                                {s.annotations.map((a, i) => (
                                    <dl key={i} className={styles.details}>
                                        {ANNOTATION_FIELDS.filter(([key]) => a[key]).map(([key, label]) => (
                                            <React.Fragment key={key}>
                                                <dt>{label}</dt><dd>{a[key]}</dd>
                                            </React.Fragment>
                                        ))}
                                    </dl>
                                ))}
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
