import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiCheckCircle, FiCircle, FiPhone, FiPrinter } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import SectionedNote from "../../ui/SectionedNote";
import { errorText } from "../../../utils/errors";
import { useApi, formatDate } from "./clinicApi";
import { EMERGENCY_SERVICES, SAFETY_STEPS, telLink } from "./safetyPlan";

const PLAN_CACHE = "mindhaven.plano-de-crise";
const stamp = (iso) => (iso ? `${formatDate(iso)} às ${iso.slice(11, 16)}` : "");

function usePage(props, path) {
    const request = useApi(props);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const load = useCallback(() => {
        setError(null);
        request(path).then(setData).catch(setError);
    }, [request, path]);
    useEffect(() => { load(); }, [load]);
    return { request, data, error, load };
}

/** As tarefas que o profissional passou: o paciente marca o que fez e conta como foi. */
export function MyTasksPage(props) {
    const { request, data, error, load } = usePage(props, `/clinic/tasks`);
    const [notes, setNotes] = useState({});
    const [busy, setBusy] = useState(false);

    const mark = async (task, done) => {
        setBusy(true);
        try {
            await request(`/clinic/tasks/${task.id}/done`, { method: "PUT", body: { done, note: notes[task.id] ?? task.patientNote ?? "" } });
            toast.success(done ? "Tarefa concluída." : "Tarefa reaberta.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const open = (data || []).filter((t) => !t.done);
    const done = (data || []).filter((t) => t.done);

    return (
        <div className={styles.page}>
            <PageHeader title="Minhas tarefas" subtitle="O que você combinou de fazer entre as sessões." />
            {error && <ErrorState error={error} onRetry={load} />}
            {!data && !error && <div className={styles.card}><Skeleton rows={4} /></div>}
            {data && (
                <div className={styles.stack}>
                    <div className={styles.card}>
                        <h2>Para fazer</h2>
                        {open.length === 0 ? (
                            <EmptyState title="Nada pendente">Quando seu psicólogo passar uma tarefa, ela aparece aqui.</EmptyState>
                        ) : open.map((task) => (
                            <div key={task.id} className={styles.timelineItem}>
                                <FiCircle aria-hidden="true" /> <strong>{task.title}</strong>{" "}
                                {task.overdue && <span className="ui-badge ui-badge--warning">Passou do prazo</span>}
                                <div className={styles.muted}>
                                    {task.professionalName}{task.dueDate ? ` · até ${formatDate(task.dueDate)}` : ""}
                                </div>
                                {task.description && <p className={styles.preserve} style={{ margin: "8px 0" }}>{task.description}</p>}
                                <label className={styles.field}>
                                    Como foi? (opcional)
                                    <textarea value={notes[task.id] ?? ""} placeholder="O que deu certo, o que foi difícil."
                                              onChange={(e) => setNotes({ ...notes, [task.id]: e.target.value })} />
                                </label>
                                <div className={styles.actions}>
                                    <button className={styles.btnPrimary} disabled={busy} onClick={() => mark(task, true)}>
                                        <FiCheckCircle aria-hidden="true" /> Marcar como feita
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                    {done.length > 0 && (
                        <div className={styles.card}>
                            <h2>Feitas</h2>
                            <ul className={styles.list}>
                                {done.map((task) => (
                                    <li key={task.id} className={styles.listItem}>
                                        <span>
                                            <FiCheckCircle aria-hidden="true" /> <strong>{task.title}</strong>{" "}
                                            <span className={styles.muted}>em {formatDate(task.doneAt)}</span>
                                            {task.patientNote && <div className={styles.muted}>{task.patientNote}</div>}
                                        </span>
                                        <button className={styles.btnGhost} disabled={busy} onClick={() => mark(task, false)}>Reabrir</button>
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

/** Botões de ligação direta: os serviços de emergência e os contatos do próprio paciente. */
function EmergencyCalls({ contactName, contactPhone }) {
    return (
        <div className={styles.grid}>
            {EMERGENCY_SERVICES.map((service) => (
                <a key={service.number} className={styles.stat} href={telLink(service.number)} style={{ textDecoration: "none" }}>
                    <div className={styles.statLabel}>{service.description}</div>
                    <div className={styles.statValue}><FiPhone aria-hidden="true" /> {service.name} {service.number}</div>
                </a>
            ))}
            {contactPhone && (
                <a className={styles.stat} href={telLink(contactPhone)} style={{ textDecoration: "none" }}>
                    <div className={styles.statLabel}>Meu contato de emergência</div>
                    <div className={styles.statValue}><FiPhone aria-hidden="true" /> {contactName || contactPhone}</div>
                </a>
            )}
        </div>
    );
}

/**
 * O plano de segurança do paciente. A última cópia fica guardada no aparelho: se a internet
 * falhar com o aplicativo aberto, o plano continua na tela.
 */
export function MySafetyPlanPage(props) {
    const request = useApi(props);
    const [data, setData] = useState(null);
    const [offline, setOffline] = useState(false);
    const [error, setError] = useState(null);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/safety-plan`).then((fresh) => {
            setData(fresh);
            setOffline(false);
            try { localStorage.setItem(PLAN_CACHE, JSON.stringify(fresh)); } catch { /* sem espaço: segue sem a cópia */ }
        }).catch((err) => {
            let cached = null;
            try { cached = JSON.parse(localStorage.getItem(PLAN_CACHE) || "null"); } catch { cached = null; }
            if (cached) {
                setData(cached);
                setOffline(true);
            } else {
                setError(err);
            }
        });
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const plans = data?.plans || [];

    return (
        <div className={styles.page}>
            <PageHeader title="Plano de crise" subtitle="O que fazer, passo a passo, quando as coisas ficarem difíceis.">
                {plans.length > 0 && (
                    <button className={styles.btnGhost} onClick={() => window.print()}><FiPrinter aria-hidden="true" /> Imprimir cartão</button>
                )}
            </PageHeader>

            <div className={styles.stack}>
                <Alert tone="danger" title="Se você está em risco agora">
                    Ligue para o CVV (188) ou o SAMU (192), ou vá ao pronto-socorro mais próximo. Você não precisa passar por isso sozinho(a).
                </Alert>
                <EmergencyCalls contactName={data?.emergencyContactName} contactPhone={data?.emergencyContactPhone} />

                {offline && <Alert tone="warning" title="Sem conexão">Esta é a última cópia guardada neste aparelho.</Alert>}
                {error && <ErrorState error={error} onRetry={load} />}
                {!data && !error && <div className={styles.card}><Skeleton rows={6} /></div>}

                {data && plans.length === 0 && (
                    <div className={styles.card}>
                        <EmptyState title="Você ainda não tem um plano">
                            O plano de crise é montado junto com seu psicólogo, numa sessão. Se quiser ter um, fale com ele.
                        </EmptyState>
                    </div>
                )}
                {plans.map((plan) => (
                    <div key={plan.id} className={styles.card}>
                        <h2>Meu plano</h2>
                        <p className={styles.muted} style={{ marginBottom: 12 }}>
                            Feito com {plan.professionalName}{plan.updatedAt ? `, atualizado em ${formatDate(plan.updatedAt)}` : ""}.
                            Siga os passos na ordem; se um não bastar, vá para o seguinte.
                        </p>
                        <SectionedNote sections={SAFETY_STEPS} values={plan} readOnly emptyText="O plano ainda está em branco." />
                    </div>
                ))}
            </div>
        </div>
    );
}

/** Termos enviados pelo profissional: o paciente lê e aceita digitando o nome completo. */
export function MyTermsPage(props) {
    const { request, data, error, load } = usePage(props, `/clinic/terms`);
    const [names, setNames] = useState({});
    const [agreed, setAgreed] = useState({});
    const [busy, setBusy] = useState(false);

    const sign = async (term) => {
        setBusy(true);
        try {
            await request(`/clinic/terms/${term.id}/sign`, { method: "PUT", body: { name: names[term.id] || "" } });
            toast.success("Termo aceito.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className={styles.page}>
            <PageHeader title="Termos" subtitle="Documentos que seu psicólogo pediu que você lesse e aceitasse.">
                {(data || []).length > 0 && (
                    <button className={styles.btnGhost} onClick={() => window.print()}><FiPrinter aria-hidden="true" /> Imprimir</button>
                )}
            </PageHeader>
            {error && <ErrorState error={error} onRetry={load} />}
            {!data && !error && <div className={styles.card}><Skeleton rows={5} /></div>}
            {data && data.length === 0 && <div className={styles.card}><EmptyState title="Nenhum termo para você" /></div>}
            <div className={styles.stack}>
                {(data || []).map((term) => (
                    <div key={term.id} className={styles.card}>
                        <h2>
                            {term.title}{" "}
                            <span className={`ui-badge ui-badge--${term.signed ? "success" : "warning"}`}>{term.signed ? "Aceito" : "Aguardando você"}</span>
                        </h2>
                        <p className={styles.muted}>Enviado por {term.professionalName} em {stamp(term.requestedAt)}.</p>
                        <div className={styles.preserve} style={{ margin: "14px 0", lineHeight: 1.6 }}>{term.content}</div>
                        {term.signed ? (
                            <Alert tone="success" title={`Aceito em ${stamp(term.signedAt)}`}>Por {term.signedName}.</Alert>
                        ) : (
                            <div className={`${styles.form} ui-no-print`}>
                                <label className={styles.check}>
                                    <input type="checkbox" checked={!!agreed[term.id]}
                                           onChange={(e) => setAgreed({ ...agreed, [term.id]: e.target.checked })} />
                                    Li e concordo com este termo
                                </label>
                                <label className={styles.field}>
                                    Digite seu nome completo
                                    <input value={names[term.id] || ""} autoComplete="name"
                                           onChange={(e) => setNames({ ...names, [term.id]: e.target.value })} />
                                    <span className={styles.muted}>Se você é o responsável legal pelo paciente, digite o seu nome.</span>
                                </label>
                                <div>
                                    <button className={styles.btnPrimary}
                                            disabled={busy || !agreed[term.id] || (names[term.id] || "").trim().split(/\s+/).length < 2}
                                            onClick={() => sign(term)}>Aceitar o termo</button>
                                </div>
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}
