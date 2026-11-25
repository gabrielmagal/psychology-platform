import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiCheckCircle, FiCircle, FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, Skeleton } from "../../ui/Feedback";
import SectionedNote from "../../ui/SectionedNote";
import { errorText } from "../../../utils/errors";
import { formatDate } from "./clinicApi";
import { SAFETY_STEPS } from "./safetyPlan";

const stamp = (iso) => (iso ? `${formatDate(iso)} às ${iso.slice(11, 16)}` : "");

/** Carrega de um endereço e devolve o dado, o estado e uma ação que avisa e recarrega. */
function useResource(request, path) {
    const [data, setData] = useState(null);
    const [failed, setFailed] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setFailed(null);
        request(path).then(setData).catch(setFailed);
    }, [request, path]);

    useEffect(() => { load(); }, [load]);

    const run = async (action, message) => {
        setBusy(true);
        try {
            await action();
            if (message) toast.success(message);
            load();
            return true;
        } catch (err) {
            toast.error(errorText(err));
            return false;
        } finally {
            setBusy(false);
        }
    };
    return { data, failed, busy, run, load };
}

/** Tarefas passadas ao paciente: o profissional cria e acompanha o que foi feito. */
export function TasksPanel({ request, patientId, readOnly }) {
    const { data, failed, busy, run } = useResource(request, `/clinic/tasks?patientId=${patientId}`);
    const [form, setForm] = useState({ title: "", description: "", dueDate: "" });

    if (failed) return <Alert tone="danger" title="Não foi possível carregar as tarefas">{errorText(failed)}</Alert>;
    if (!data) return <div className={styles.card}><Skeleton rows={4} /></div>;

    const add = async (e) => {
        e.preventDefault();
        const saved = await run(() => request(`/clinic/tasks`, { method: "POST", body: { ...form, patientId } }), "Tarefa enviada ao paciente.");
        if (saved) setForm({ title: "", description: "", dueDate: "" });
    };

    return (
        <div className={styles.stack}>
            {!readOnly && (
                <form className={`${styles.card} ${styles.form}`} onSubmit={add}>
                    <h2>Nova tarefa</h2>
                    <div className={styles.row}>
                        <label className={styles.field}>O que fazer
                            <input required maxLength={200} value={form.title} placeholder="Ex.: Diário do sono por 7 dias"
                                   onChange={(e) => setForm({ ...form, title: e.target.value })} />
                        </label>
                        <label className={styles.field}>Até quando (opcional)
                            <input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
                        </label>
                    </div>
                    <label className={styles.field}>Orientações (opcional)
                        <textarea value={form.description} placeholder="Como fazer, o que observar, o que trazer para a próxima sessão."
                                  onChange={(e) => setForm({ ...form, description: e.target.value })} />
                    </label>
                    <div>
                        <button type="submit" className={styles.btnPrimary} disabled={busy}><FiPlus aria-hidden="true" /> Passar tarefa</button>
                    </div>
                </form>
            )}
            <div className={styles.card}>
                {data.length === 0 ? (
                    <EmptyState title="Nenhuma tarefa">O paciente vê as tarefas no aplicativo e marca o que fez.</EmptyState>
                ) : (
                    <ul className={styles.list}>
                        {data.map((task) => (
                            <li key={task.id} className={styles.listItem} style={{ alignItems: "flex-start" }}>
                                <span>
                                    {task.done ? <FiCheckCircle aria-hidden="true" color="var(--success-600, #15803d)" /> : <FiCircle aria-hidden="true" />}{" "}
                                    <strong>{task.title}</strong>{" "}
                                    {task.done && <span className="ui-badge ui-badge--success">Feita em {formatDate(task.doneAt)}</span>}
                                    {task.overdue && <span className="ui-badge ui-badge--warning">Passou do prazo</span>}
                                    {task.description && <div className={`${styles.muted} ${styles.preserve}`}>{task.description}</div>}
                                    <div className={styles.muted}>
                                        Passada em {formatDate(task.createdAt)}{task.dueDate ? ` · até ${formatDate(task.dueDate)}` : ""}
                                        {readOnly && task.professionalName ? ` · ${task.professionalName}` : ""}
                                    </div>
                                    {task.patientNote && <div className={styles.preserve}><em>Paciente: {task.patientNote}</em></div>}
                                </span>
                                {!readOnly && (
                                    <button className={styles.btnDanger} disabled={busy} aria-label={`Remover ${task.title}`}
                                            onClick={() => run(() => request(`/clinic/tasks/${task.id}`, { method: "DELETE" }), "Tarefa removida.")}>
                                        <FiTrash2 aria-hidden="true" />
                                    </button>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}

/** Plano de segurança para crises: o profissional escreve com o paciente; o paciente o leva no aplicativo. */
export function SafetyPlanPanel({ request, patientId }) {
    const { data, failed, busy, run } = useResource(request, `/clinic/safety-plan?patientId=${patientId}`);
    const [draft, setDraft] = useState(null);

    useEffect(() => {
        if (data?.canEdit) setDraft(data.plans[0] || {});
    }, [data]);

    if (failed) return <Alert tone="danger" title="Não foi possível carregar o plano">{errorText(failed)}</Alert>;
    if (!data) return <div className={styles.card}><Skeleton rows={6} /></div>;

    if (!data.canEdit) {
        return (
            <div className={styles.stack}>
                <Alert tone="info" title="Somente leitura">O plano de segurança é escrito pelo profissional junto com o paciente.</Alert>
                {data.plans.length === 0 && <div className={styles.card}><EmptyState title="Nenhum plano de segurança" /></div>}
                {data.plans.map((plan) => (
                    <div key={plan.id} className={styles.card}>
                        <h2>{plan.professionalName}</h2>
                        <SectionedNote sections={SAFETY_STEPS} values={plan} readOnly />
                    </div>
                ))}
            </div>
        );
    }

    const save = () => run(() => request(`/clinic/safety-plan/${patientId}`, { method: "PUT", body: draft }), "Plano salvo. O paciente já o vê no aplicativo.");

    return (
        <div className={styles.card}>
            <h2>Plano de segurança</h2>
            <p className={styles.muted} style={{ marginBottom: 14 }}>
                Construa com o paciente, na sessão, nas palavras dele. Ele abre o plano em "Plano de crise", com botões para
                ligar para o CVV (188) e o SAMU (192), e pode imprimir um cartão.
                {data.plans[0]?.updatedAt ? ` Última alteração em ${stamp(data.plans[0].updatedAt)}.` : ""}
            </p>
            <SectionedNote sections={SAFETY_STEPS} values={draft || {}} onChange={setDraft} />
            <div className={styles.actions}>
                <button className={styles.btnPrimary} disabled={busy} onClick={save}>{busy ? "Salvando…" : "Salvar plano"}</button>
            </div>
        </div>
    );
}

/** Termos enviados ao paciente e a situação de cada um. */
export function TermsPanel({ request, patientId, readOnly }) {
    const { data, failed, busy, run } = useResource(request, `/clinic/terms?patientId=${patientId}`);
    const [open, setOpen] = useState(null);

    if (failed) return <Alert tone="danger" title="Não foi possível carregar os termos">{errorText(failed)}</Alert>;
    if (!data) return <div className={styles.card}><Skeleton rows={3} /></div>;

    return (
        <div className={styles.card}>
            <div className={styles.header}>
                <h2>Termos</h2>
                {!readOnly && data.length === 0 && (
                    <button className={styles.btnPrimary} disabled={busy}
                            onClick={() => run(() => request(`/clinic/terms`, { method: "POST", body: { patientId } }), "Termo enviado ao paciente.")}>
                        Enviar termo de atendimento online
                    </button>
                )}
            </div>
            {data.length === 0 ? (
                <EmptyState title="Nenhum termo enviado">
                    O termo de atendimento online explica sigilo, ambiente, falhas de conexão e o que fazer em urgências.
                    O paciente lê e aceita pelo aplicativo.
                </EmptyState>
            ) : (
                <ul className={styles.list}>
                    {data.map((term) => (
                        <li key={term.id} className={styles.listItem} style={{ alignItems: "flex-start" }}>
                            <span>
                                <strong>{term.title}</strong>{" "}
                                <span className={`ui-badge ui-badge--${term.signed ? "success" : "warning"}`}>
                                    {term.signed ? "Aceito" : "Aguardando o paciente"}
                                </span>
                                <div className={styles.muted}>
                                    Enviado em {stamp(term.requestedAt)}
                                    {term.signed ? ` · aceito em ${stamp(term.signedAt)} por ${term.signedName}` : ""}
                                    {readOnly && term.professionalName ? ` · ${term.professionalName}` : ""}
                                </div>
                                {open === term.id && <div className={styles.preserve} style={{ marginTop: 10 }}>{term.content}</div>}
                            </span>
                            <button className={styles.btnGhost} onClick={() => setOpen(open === term.id ? null : term.id)}>
                                {open === term.id ? "Fechar" : "Ler"}
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            <p className={styles.muted} style={{ marginTop: 12 }}>
                O aceite pelo aplicativo registra data, hora e o nome digitado pelo paciente. Não é assinatura com certificado digital.
            </p>
        </div>
    );
}
