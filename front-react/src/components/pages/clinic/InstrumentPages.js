import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiEdit2, FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import Questionnaire, { isComplete } from "../../ui/Questionnaire";
import { errorText } from "../../../utils/errors";
import { useApi, formatDate } from "./clinicApi";

const TYPE_LABEL = { SCALE: "Escala", YESNO: "Sim ou não", TEXT: "Texto livre" };
const NEW_TEMPLATE = {
    id: null, title: "", instructions: "",
    options: ["Nada", "Um pouco", "Bastante", "Muito"],
    items: [{ text: "", type: "SCALE" }],
};

/** Questionários que o profissional pediu: o paciente responde um de cada vez. */
export function MyFormsPage(props) {
    const request = useApi(props);
    const [rows, setRows] = useState(null);
    const [error, setError] = useState(null);
    const [active, setActive] = useState(null);
    const [answers, setAnswers] = useState({});
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/forms`).then((data) => setRows(data || [])).catch(setError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const submit = async () => {
        setBusy(true);
        try {
            await request(`/clinic/forms/${active.id}/answers`, { method: "PUT", body: { answers } });
            toast.success("Respostas enviadas. Obrigado!");
            setActive(null);
            setAnswers({});
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const pending = (rows || []).filter((r) => !r.answered && r.available);
    const done = (rows || []).filter((r) => r.answered);

    return (
        <div className={styles.page}>
            <PageHeader title="Questionários" subtitle="Perguntas que seu psicólogo pediu para você responder." />
            {error && <ErrorState error={error} onRetry={load} />}
            {!rows && !error && <div className={styles.card}><Skeleton rows={4} /></div>}
            {rows && (
                <div className={styles.stack}>
                    {active ? (
                        <div className={styles.card}>
                            <h2>{active.title}</h2>
                            <Questionnaire definition={active.definition} answers={answers} onChange={setAnswers} name="meu" />
                            <div className={styles.actions}>
                                <button className={styles.btnPrimary} disabled={busy || !isComplete(active.definition, answers)} onClick={submit}>
                                    {busy ? "Enviando…" : "Enviar respostas"}
                                </button>
                                <button className={styles.btnGhost} onClick={() => { setActive(null); setAnswers({}); }}>Responder depois</button>
                            </div>
                            <p className={styles.muted}>
                                Não há resposta certa ou errada. Seu psicólogo lê as respostas e conversa com você sobre elas na sessão.
                            </p>
                        </div>
                    ) : (
                        <div className={styles.card}>
                            <h2>Para responder</h2>
                            {pending.length === 0 ? (
                                <EmptyState title="Nada para responder agora" />
                            ) : (
                                <ul className={styles.list}>
                                    {pending.map((r) => (
                                        <li key={r.id} className={styles.listItem}>
                                            <span>
                                                <strong>{r.title}</strong>
                                                <div className={styles.muted}>Pedido por {r.professionalName} · {r.definition.items.length} pergunta(s)</div>
                                            </span>
                                            <button className={styles.btnPrimary} onClick={() => { setActive(r); setAnswers({}); }}>Responder</button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                    )}
                    {done.length > 0 && !active && (
                        <div className={styles.card}>
                            <h2>Já respondidos</h2>
                            <ul className={styles.list}>
                                {done.map((r) => (
                                    <li key={r.id} className={styles.listItem}>
                                        <span><strong>{r.title}</strong> <span className={styles.muted}>em {formatDate(r.answeredAt)}</span></span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

/** Os instrumentos disponíveis: as escalas prontas e os formulários que o profissional monta. */
export function InstrumentTemplatesPage(props) {
    const request = useApi(props);
    const [rows, setRows] = useState(null);
    const [error, setError] = useState(null);
    const [draft, setDraft] = useState(null);
    const [preview, setPreview] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/instruments`).then((data) => setRows(data || [])).catch(setError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const save = async (e) => {
        e.preventDefault();
        setBusy(true);
        try {
            await request(`/clinic/instruments`, { method: "POST", body: draft });
            toast.success("Instrumento salvo.");
            setDraft(null);
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const remove = async (row) => {
        try {
            await request(`/clinic/instruments/${row.id}`, { method: "DELETE" });
            toast.success("Instrumento removido. O que já foi respondido continua no prontuário.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const edit = (row) => setDraft({
        id: row.id, title: row.title, instructions: row.instructions || "",
        options: (row.options || []).map((o) => o.label),
        items: row.items.map((i) => ({ text: i.text, type: i.type })),
    });
    const setItem = (index, patch) => setDraft({ ...draft, items: draft.items.map((item, i) => (i === index ? { ...item, ...patch } : item)) });
    const hasScale = draft?.items.some((i) => i.type === "SCALE");

    return (
        <div className={styles.page}>
            <PageHeader title="Instrumentos" subtitle="Escalas prontas e formulários que você monta para acompanhar seus pacientes.">
                {!draft && (
                    <button className={styles.btnPrimary} onClick={() => setDraft(NEW_TEMPLATE)}><FiPlus aria-hidden="true" /> Novo formulário</button>
                )}
            </PageHeader>
            {error && <ErrorState error={error} onRetry={load} />}
            {!rows && !error && <div className={styles.card}><Skeleton rows={5} /></div>}

            {rows && (
                <div className={styles.stack}>
                    <Alert tone="info" title="Sobre as escalas prontas">
                        PHQ-9, GAD-7 e WHO-5 são escalas de rastreio de uso livre. Elas não fazem diagnóstico e não substituem a
                        avaliação clínica. Confira o texto dos itens com a versão validada que você adota antes de aplicar.
                        Para aplicar, abra o prontuário do paciente, na aba "Escalas".
                    </Alert>

                    {draft && (
                        <form className={`${styles.card} ${styles.form}`} onSubmit={save}>
                            <h2>{draft.id ? "Alterar formulário" : "Novo formulário"}</h2>
                            <label className={styles.field}>Nome
                                <input required maxLength={160} value={draft.title} placeholder="Ex.: Check-in semanal"
                                       onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
                            </label>
                            <label className={styles.field}>Orientação para quem responde (opcional)
                                <input value={draft.instructions} placeholder="Ex.: Pense na última semana."
                                       onChange={(e) => setDraft({ ...draft, instructions: e.target.value })} />
                            </label>
                            <div className={styles.sectionTitle}>Perguntas</div>
                            {draft.items.map((item, index) => (
                                <div key={index} className={styles.row} style={{ alignItems: "end" }}>
                                    <label className={styles.field} style={{ flex: 3 }}>Pergunta {index + 1}
                                        <input required value={item.text} onChange={(e) => setItem(index, { text: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>Resposta
                                        <select value={item.type} onChange={(e) => setItem(index, { type: e.target.value })}>
                                            {Object.entries(TYPE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                                        </select>
                                    </label>
                                    <div>
                                        <button type="button" className={styles.btnDanger} disabled={draft.items.length === 1}
                                                aria-label={`Remover pergunta ${index + 1}`}
                                                onClick={() => setDraft({ ...draft, items: draft.items.filter((_, i) => i !== index) })}>
                                            <FiTrash2 aria-hidden="true" />
                                        </button>
                                    </div>
                                </div>
                            ))}
                            <div>
                                <button type="button" className={styles.btnGhost}
                                        onClick={() => setDraft({ ...draft, items: [...draft.items, { text: "", type: "SCALE" }] })}>
                                    <FiPlus aria-hidden="true" /> Adicionar pergunta
                                </button>
                            </div>
                            {hasScale && (
                                <label className={styles.field}>
                                    Opções das perguntas de escala, da menor para a maior (uma por linha)
                                    <textarea value={draft.options.join("\n")}
                                              onChange={(e) => setDraft({ ...draft, options: e.target.value.split("\n") })} />
                                    <span className={styles.muted}>A primeira vale 0, a segunda 1, e assim por diante. A pontuação é a soma.</span>
                                </label>
                            )}
                            <div className={styles.actions}>
                                <button type="submit" className={styles.btnPrimary} disabled={busy}>{busy ? "Salvando…" : "Salvar formulário"}</button>
                                <button type="button" className={styles.btnGhost} onClick={() => setDraft(null)}>Cancelar</button>
                            </div>
                        </form>
                    )}

                    <div className={styles.card}>
                        {rows.length === 0 ? <EmptyState title="Nenhum instrumento" /> : (
                            <ul className={styles.list}>
                                {rows.map((row) => (
                                    <li key={row.code} className={styles.listItem} style={{ alignItems: "flex-start", flexWrap: "wrap" }}>
                                        <span style={{ flex: "1 1 320px" }}>
                                            <strong>{row.title}</strong>{" "}
                                            <span className={`ui-badge ui-badge--${row.builtIn ? "info" : "success"}`}>{row.builtIn ? "Pronta" : "Meu formulário"}</span>
                                            <div className={styles.muted}>
                                                {row.description || `${row.items.length} pergunta(s)`}
                                            </div>
                                            {preview === row.code && (
                                                <div style={{ marginTop: 12 }}>
                                                    <Questionnaire definition={row} answers={{}} onChange={() => {}} name={`p-${row.code}`} />
                                                </div>
                                            )}
                                        </span>
                                        <span className={styles.toolbar}>
                                            <button className={styles.btnGhost} onClick={() => setPreview(preview === row.code ? null : row.code)}>
                                                {preview === row.code ? "Fechar" : "Ver perguntas"}
                                            </button>
                                            {!row.builtIn && (
                                                <>
                                                    <button className={styles.btnGhost} onClick={() => edit(row)} aria-label={`Alterar ${row.title}`}><FiEdit2 aria-hidden="true" /></button>
                                                    <button className={styles.btnDanger} onClick={() => remove(row)} aria-label={`Remover ${row.title}`}><FiTrash2 aria-hidden="true" /></button>
                                                </>
                                            )}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

const RESULT_LABEL = {
    ENVIADO: ["success", "Enviado"],
    DESLIGADO: ["warning", "Canal desligado"],
    SEM_CONTATO: ["warning", "Seu cadastro não tem o contato deste canal"],
    FALHOU: ["danger", "Falhou"],
};

/** Para o administrador: como os lembretes automáticos de sessão estão configurados. */
export function RemindersPage(props) {
    const request = useApi(props);
    const [status, setStatus] = useState(null);
    const [error, setError] = useState(null);
    const [results, setResults] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/reminders/status`).then(setStatus).catch(setError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const test = async () => {
        setBusy(true);
        try {
            setResults(await request(`/clinic/reminders/test`, { method: "POST" }));
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className={styles.page}>
            <PageHeader title="Lembretes automáticos" subtitle="Aviso de sessão enviado ao paciente sem ninguém precisar lembrar." />
            {error && <ErrorState error={error} onRetry={load} />}
            {!status && !error && <div className={styles.card}><Skeleton rows={4} /></div>}
            {status && (
                <div className={styles.stack}>
                    <Alert tone={status.enabled ? "success" : "warning"} title={status.enabled ? "Lembretes ligados" : "Lembretes desligados"}>
                        {status.enabled
                            ? `O sistema confere a agenda a cada ${status.every} e avisa o paciente ${status.hoursBefore} horas antes da sessão.`
                            : "Nenhum aviso é enviado sozinho. Quem liga é o responsável pelo servidor, na configuração (REMINDER_ENABLED=true)."}
                    </Alert>
                    <div className={styles.card}>
                        <h2>Canais</h2>
                        <ul className={styles.list}>
                            {status.channels.map((channel) => (
                                <li key={channel.name} className={styles.listItem}>
                                    <span>
                                        <strong>{channel.name}</strong>
                                        <div className={styles.muted}>
                                            {channel.name === "WhatsApp"
                                                ? "API oficial do WhatsApp (Meta). Precisa de uma conta WhatsApp Business, do número, do token e de um modelo de mensagem aprovado."
                                                : "Usa o servidor de e-mail configurado no sistema."}
                                        </div>
                                    </span>
                                    <span className={`ui-badge ui-badge--${channel.enabled ? "success" : "warning"}`}>{channel.enabled ? "Ligado" : "Desligado"}</span>
                                </li>
                            ))}
                        </ul>
                        <div className={styles.actions}>
                            <button className={styles.btnPrimary} disabled={busy} onClick={test}>{busy ? "Enviando…" : "Enviar um lembrete de teste para mim"}</button>
                        </div>
                        {results && (
                            <ul className={styles.list} style={{ marginTop: 12 }}>
                                {results.map((r) => (
                                    <li key={r.name} className={styles.listItem}>
                                        <span><strong>{r.name}</strong>{r.detail && <div className={styles.muted}>{r.detail}</div>}</span>
                                        <span className={`ui-badge ui-badge--${RESULT_LABEL[r.result][0]}`}>{RESULT_LABEL[r.result][1]}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                    <p className={styles.muted}>
                        Enquanto o WhatsApp automático não estiver ligado, o botão "Lembrar por WhatsApp" da agenda continua
                        abrindo a conversa com a mensagem pronta.
                    </p>
                </div>
            )}
        </div>
    );
}
