import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { FiAlertTriangle, FiSend, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, Skeleton } from "../../ui/Feedback";
import LineChart from "../../ui/LineChart";
import Questionnaire, { isComplete } from "../../ui/Questionnaire";
import { errorText } from "../../../utils/errors";
import { formatDate } from "./clinicApi";

const REPEAT = [["", "Uma vez só"], ["7", "Toda semana"], ["14", "A cada 15 dias"], ["30", "Todo mês"]];
const shortDate = (iso) => formatDate(iso).slice(0, 5);

/**
 * Escalas e formulários do paciente: enviar, responder na sessão, ler as respostas e ver a
 * evolução da pontuação em gráfico.
 */
export default function InstrumentsPanel({ request, patientId, readOnly }) {
    const [templates, setTemplates] = useState([]);
    const [responses, setResponses] = useState(null);
    const [failed, setFailed] = useState(null);
    const [form, setForm] = useState({ templateCode: "", repeatDays: "" });
    const [busy, setBusy] = useState(false);
    const [open, setOpen] = useState(null);
    const [filling, setFilling] = useState(null);
    const [answers, setAnswers] = useState({});

    const load = useCallback(async () => {
        setFailed(null);
        try {
            const [list, catalog] = await Promise.all([
                request(`/clinic/forms?patientId=${patientId}`),
                readOnly ? Promise.resolve([]) : request(`/clinic/instruments`),
            ]);
            setResponses(list || []);
            setTemplates(catalog || []);
        } catch (err) {
            setFailed(err);
        }
    }, [request, patientId, readOnly]);

    useEffect(() => { load(); }, [load]);

    const run = async (action, message) => {
        setBusy(true);
        try {
            await action();
            toast.success(message);
            await load();
            return true;
        } catch (err) {
            toast.error(errorText(err));
            return false;
        } finally {
            setBusy(false);
        }
    };

    // Uma série por instrumento, na ordem do tempo, só com o que tem pontuação.
    const series = useMemo(() => {
        const groups = {};
        for (const r of [...(responses || [])].reverse()) {
            if (!r.answered || r.score == null) continue;
            (groups[r.templateCode] = groups[r.templateCode] || { title: r.title, max: r.maxScore, points: [] })
                .points.push({ label: shortDate(r.answeredAt), value: r.score, flagged: r.alert });
        }
        return Object.entries(groups);
    }, [responses]);

    if (failed) return <Alert tone="danger" title="Não foi possível carregar as escalas">{errorText(failed)}</Alert>;
    if (!responses) return <div className={styles.card}><Skeleton rows={5} /></div>;

    const apply = async (e) => {
        e.preventDefault();
        await run(() => request(`/clinic/forms`, { method: "POST", body: { ...form, patientId } }), "Enviado ao paciente.");
    };

    const submit = async (response) => {
        const done = await run(() => request(`/clinic/forms/${response.id}/answers`, { method: "PUT", body: { answers } }), "Respostas registradas.");
        if (done) {
            setFilling(null);
            setAnswers({});
        }
    };

    return (
        <div className={styles.stack}>
            {!readOnly && (
                <form className={`${styles.card} ${styles.form}`} onSubmit={apply}>
                    <h2>Aplicar escala ou formulário</h2>
                    <div className={styles.row}>
                        <label className={styles.field}>Instrumento
                            <select required value={form.templateCode} onChange={(e) => setForm({ ...form, templateCode: e.target.value })}>
                                <option value="">Selecione…</option>
                                {templates.map((t) => <option key={t.code} value={t.code}>{t.title}{t.builtIn ? "" : " (meu)"}</option>)}
                            </select>
                        </label>
                        <label className={styles.field}>Repetir
                            <select value={form.repeatDays} onChange={(e) => setForm({ ...form, repeatDays: e.target.value })}>
                                {REPEAT.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                            </select>
                        </label>
                    </div>
                    <div className={styles.actions}>
                        <button type="submit" className={styles.btnPrimary} disabled={busy}><FiSend aria-hidden="true" /> Enviar ao paciente</button>
                        <Link className={styles.btnGhost} to="/instrumentos">Meus instrumentos</Link>
                    </div>
                    <p className={styles.muted}>
                        O paciente responde pelo aplicativo e não vê a pontuação. Com repetição, cada resposta já agenda a próxima.
                        Escalas de rastreio não fazem diagnóstico: o resultado é lido junto com a avaliação clínica.
                    </p>
                </form>
            )}

            {series.map(([code, group]) => (
                <div key={code} className={styles.card}>
                    <h2>{group.title}</h2>
                    <LineChart points={group.points} max={group.max} ariaLabel={`Pontuação de ${group.title} ao longo do tempo`} />
                    {group.points.some((p) => p.flagged) && (
                        <p className={styles.muted}>Ponto vermelho: resposta com item de atenção marcado.</p>
                    )}
                </div>
            ))}

            <div className={styles.card}>
                <h2>Aplicações</h2>
                {responses.length === 0 ? (
                    <EmptyState title="Nenhuma escala aplicada">As respostas e a evolução da pontuação aparecem aqui.</EmptyState>
                ) : (
                    <ul className={styles.list}>
                        {responses.map((r) => (
                            <li key={r.id} className={styles.listItem} style={{ alignItems: "flex-start", flexWrap: "wrap" }}>
                                <span style={{ flex: "1 1 320px" }}>
                                    <strong>{r.title}</strong>{" "}
                                    {r.answered ? (
                                        <>
                                            {r.score != null && <span className="ui-badge ui-badge--info">{r.score}{r.maxScore ? ` de ${r.maxScore}` : ""}{r.band ? ` · ${r.band}` : ""}</span>}
                                            {r.alert && <span className="ui-badge ui-badge--danger" style={{ marginLeft: 6 }}><FiAlertTriangle aria-hidden="true" /> Item de atenção</span>}
                                        </>
                                    ) : (
                                        <span className="ui-badge ui-badge--warning">
                                            {r.available ? "Aguardando resposta" : `Liberado em ${formatDate(r.dueDate)}`}
                                        </span>
                                    )}
                                    <div className={styles.muted}>
                                        {r.answered
                                            ? `Respondido em ${formatDate(r.answeredAt)} ${r.answeredBy === "PROFISSIONAL" ? "na sessão" : "pelo paciente"}`
                                            : `Enviado em ${formatDate(r.requestedAt)}`}
                                        {r.repeatDays ? ` · repete a cada ${r.repeatDays} dias` : ""}
                                        {readOnly && r.professionalName ? ` · ${r.professionalName}` : ""}
                                    </div>
                                    {open === r.id && (
                                        <div style={{ marginTop: 12 }}>
                                            <Questionnaire definition={r.definition} answers={r.answers} readOnly name={`r-${r.id}`} />
                                        </div>
                                    )}
                                    {filling === r.id && (
                                        <div style={{ marginTop: 12 }}>
                                            <Questionnaire definition={r.definition} answers={answers} onChange={setAnswers} name={`f-${r.id}`} />
                                            <div className={styles.actions}>
                                                <button className={styles.btnPrimary} disabled={busy || !isComplete(r.definition, answers)}
                                                        onClick={() => submit(r)}>Registrar respostas</button>
                                                <button className={styles.btnGhost} onClick={() => { setFilling(null); setAnswers({}); }}>Cancelar</button>
                                            </div>
                                        </div>
                                    )}
                                </span>
                                <span className={styles.toolbar}>
                                    {r.answered && (
                                        <button className={styles.btnGhost} onClick={() => setOpen(open === r.id ? null : r.id)}>
                                            {open === r.id ? "Fechar" : "Ver respostas"}
                                        </button>
                                    )}
                                    {!r.answered && !readOnly && filling !== r.id && (
                                        <>
                                            <button className={styles.btnGhost} onClick={() => { setFilling(r.id); setAnswers({}); }}>Responder na sessão</button>
                                            <button className={styles.btnDanger} disabled={busy} aria-label={`Cancelar ${r.title}`}
                                                    onClick={() => run(() => request(`/clinic/forms/${r.id}`, { method: "DELETE" }), "Aplicação cancelada.")}>
                                                <FiTrash2 aria-hidden="true" />
                                            </button>
                                        </>
                                    )}
                                </span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}
