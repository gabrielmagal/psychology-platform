import React, { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import { FiDownload, FiFileText, FiLock, FiPaperclip, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, Skeleton } from "../../ui/Feedback";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import { errorText } from "../../../utils/errors";
import { saveBlob } from "../../../utils/fetchWithAuth";
import { useApi, useDownload, formatDate, formatCpf, STATUS_LABEL, MOODS, sleepText } from "./clinicApi";
import { SafetyPlanPanel, TasksPanel, TermsPanel } from "./PatientCarePanels";
import InstrumentsPanel from "./InstrumentsPanel";
import WrittenDocumentsPanel from "./WrittenDocumentsPanel";
import SectionedNote from "../../ui/SectionedNote";
import SessionRecordPanel, { RECORD_SECTIONS } from "./SessionRecordPanel";

const FIELDS = [
    ["chiefComplaint", "Queixa principal"],
    ["complaintHistory", "História da queixa"],
    ["familyHistory", "Histórico familiar"],
    ["healthHistory", "Saúde, medicações e tratamentos anteriores"],
    ["diagnosticHypothesis", "Hipótese diagnóstica"],
    ["therapeuticGoals", "Objetivos terapêuticos"],
    ["observations", "Observações"],
];

// Na linha do tempo o registro aparece inteiro: as seções, os adendos e, para quem escreveu, as notas privadas.
const TIMELINE_SECTIONS = [
    ...RECORD_SECTIONS,
    { key: "technicalIssues", label: "Intercorrências do atendimento online" },
    { key: "addenda", label: "Adendos" },
    { key: "privateNotes", label: "Notas privadas", private: true },
];

const TABS = [
    ["evolucao", "Evolução"],
    ["anamnese", "Anamnese"],
    ["anexos", "Anexos"],
    ["documentos", "Documentos"],
    ["escalas", "Escalas"],
    ["humor", "Humor e sono"],
    ["tarefas", "Tarefas"],
    ["plano", "Plano de crise"],
    ["termos", "Termos"],
];

const size = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

const toBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
    reader.onerror = reject;
    reader.readAsDataURL(file);
});

export default function PatientRecordPage(props) {
    const { patientId } = useParams();
    const request = useApi(props);
    const download = useDownload(props);
    const fileInput = useRef(null);

    const [tab, setTab] = useState("evolucao");
    const [record, setRecord] = useState(null);
    const [form, setForm] = useState({});
    const [timeline, setTimeline] = useState([]);
    const [openRecord, setOpenRecord] = useState(null);
    const [moods, setMoods] = useState([]);
    const [attachments, setAttachments] = useState([]);
    const [documents, setDocuments] = useState([]);
    const [error, setError] = useState(null);
    const [saving, setSaving] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [removing, setRemoving] = useState(null);

    const load = useCallback(async () => {
        setError(null);
        try {
            const [data, sessions, diary, files, docs] = await Promise.all([
                request(`/clinic/patient-record/${patientId}`),
                request(`/clinic/patient-record/${patientId}/timeline`),
                request(`/clinic/mood?patientId=${patientId}`),
                request(`/clinic/patient-record/${patientId}/attachments`),
                request(`/clinic/documents?patientId=${patientId}`),
            ]);
            setRecord(data);
            setForm(Object.fromEntries(FIELDS.map(([key]) => [key, data?.[key] || ""])));
            setTimeline(sessions || []);
            setMoods(diary || []);
            setAttachments(files || []);
            setDocuments(docs || []);
        } catch (err) {
            setError(err);
        }
    }, [request, patientId]);

    useEffect(() => { load(); }, [load]);

    /** Só a linha do tempo: recarregar tudo fecharia o registro que a pessoa está escrevendo. */
    const reloadTimeline = useCallback(() => {
        request(`/clinic/patient-record/${patientId}/timeline`).then((sessions) => setTimeline(sessions || [])).catch(() => {});
    }, [request, patientId]);

    const save = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await request(`/clinic/patient-record/${patientId}`, { method: "PUT", body: form });
            toast.success("Anamnese salva.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setSaving(false);
        }
    };

    const upload = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = "";
        if (!file) return;
        if (file.size > 10 * 1024 * 1024) {
            toast.error("O arquivo passa do limite de 10 MB.");
            return;
        }
        setUploading(true);
        try {
            await request(`/clinic/patient-record/${patientId}/attachments`, {
                method: "POST",
                body: { fileName: file.name, contentType: file.type, content: await toBase64(file) },
            });
            toast.success("Arquivo anexado ao prontuário.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setUploading(false);
        }
    };

    const open = async (path) => {
        try {
            const { blob, fileName } = await download(path);
            saveBlob(blob, fileName);
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const removeAttachment = async () => {
        const target = removing;
        setRemoving(null);
        try {
            await request(`/clinic/attachments/${target.id}`, { method: "DELETE" });
            toast.success("Anexo removido.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    if (error) return <div className={styles.page}><ErrorState error={error} onRetry={load} /></div>;
    if (!record) return <div className={styles.page}><div className={styles.card}><Skeleton rows={6} /></div></div>;

    const patient = record.patient || {};
    const moodOf = (value) => MOODS.find((m) => m.value === value);
    const counts = { anexos: attachments.length, documentos: documents.length, evolucao: timeline.length };

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>{patient.name}</h1>
                    <p className={styles.subtitle}>
                        Prontuário · CPF {formatCpf(patient.cpf)} · Nascimento {formatDate(patient.birthDate)} · {patient.phoneNumber}
                        {patient.guardianName ? ` · Responsável ${patient.guardianName}` : ""}
                    </p>
                </div>
                <div className={styles.toolbar}>
                    {!record.readOnly && (
                        <Link className={styles.btnGhost} to={`/documento/contrato/${patientId}`}>
                            <FiFileText aria-hidden="true" /> Emitir contrato
                        </Link>
                    )}
                    <Link className={styles.btnGhost} to="/agenda">Agenda</Link>
                </div>
            </div>

            <div className={styles.tabs} role="tablist">
                {TABS.map(([id, label]) => (
                    <button key={id} role="tab" aria-selected={tab === id}
                            className={tab === id ? styles.tabActive : ""} onClick={() => setTab(id)}>
                        {label}{counts[id] ? <span className={styles.tabCount}>{counts[id]}</span> : null}
                    </button>
                ))}
            </div>

            {tab === "evolucao" && (
                <div className={styles.card}>
                    {timeline.length === 0 ? (
                        <EmptyState title="Nenhuma sessão registrada">
                            As sessões agendadas aparecem aqui, com o registro que você fizer em cada uma.
                        </EmptyState>
                    ) : (
                        <div className={styles.timeline}>
                            {timeline.map((s) => (
                                <div key={s.id} className={styles.timelineItem}>
                                    <strong>{formatDate(s.date)}{s.startTime ? ` às ${s.startTime}` : ""} — {s.title || "Sessão"}</strong>{" "}
                                    <span className={`${styles.badge} ${styles[`badge${s.status}`] || ""}`}>{STATUS_LABEL[s.status]}</span>
                                    {s.professionalName && <span className={styles.muted}> · {s.professionalName}</span>}
                                    {s.signedAt && <span className="ui-badge ui-badge--success" style={{ marginLeft: 8 }}><FiLock aria-hidden="true" /> Concluído</span>}
                                    {s.riskAlert && <span className="ui-badge ui-badge--danger" style={{ marginLeft: 8 }}>Sinal de atenção</span>}
                                    <div style={{ marginTop: 12 }}>
                                        {openRecord === s.id ? (
                                            <SessionRecordPanel sessionId={s.id} request={request} onSaved={reloadTimeline} />
                                        ) : (
                                            <SectionedNote
                                                sections={TIMELINE_SECTIONS}
                                                values={s}
                                                readOnly
                                                emptyText={s.status === "REALIZADA" ? "Sessão realizada sem registro." : "Sem registro."}
                                            />
                                        )}
                                    </div>
                                    {s.status !== "CANCELADA" && (
                                        <div style={{ marginTop: 10 }}>
                                            <button type="button" className="ui-btn ui-btn--sm"
                                                    onClick={() => setOpenRecord(openRecord === s.id ? null : s.id)}>
                                                {openRecord === s.id ? "Fechar" : record.readOnly || s.signedAt ? "Abrir registro" : RECORD_SECTIONS.some((section) => s[section.key]) ? "Editar registro" : "Escrever registro"}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {tab === "anamnese" && record.readOnly && (
                <div className={styles.stack}>
                    <Alert tone="info" title="Somente leitura">
                        Cada profissional mantém a própria anamnese do paciente. Como administrador você vê todas, mas não as altera.
                    </Alert>
                    {(record.records || []).length === 0 && (
                        <div className={styles.card}><EmptyState title="Nenhuma anamnese registrada" /></div>
                    )}
                    {(record.records || []).map((r, index) => (
                        <div key={index} className={styles.card}>
                            <h2>{r.professionalName}</h2>
                            <dl className={styles.details}>
                                {FIELDS.filter(([key]) => r[key]).map(([key, label]) => (
                                    <React.Fragment key={key}><dt>{label}</dt><dd className={styles.preserve}>{r[key]}</dd></React.Fragment>
                                ))}
                            </dl>
                            {r.updatedAt && <p className={styles.muted} style={{ marginTop: 10 }}>Última alteração em {formatDate(r.updatedAt)}</p>}
                        </div>
                    ))}
                </div>
            )}

            {tab === "anamnese" && !record.readOnly && (
                <form className={`${styles.card} ${styles.form}`} onSubmit={save}>
                    {FIELDS.map(([key, label]) => (
                        <label key={key} className={styles.field}>
                            {label}
                            <textarea value={form[key] || ""} onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
                        </label>
                    ))}
                    <div className={styles.actions}>
                        <button type="submit" className={styles.btnPrimary} disabled={saving}>
                            {saving ? "Salvando…" : "Salvar anamnese"}
                        </button>
                        {record.updatedAt && <span className={styles.muted}>Última alteração em {formatDate(record.updatedAt)}</span>}
                    </div>
                </form>
            )}

            {tab === "anexos" && (
                <div className={styles.card}>
                    <div className={styles.toolbar} style={{ marginBottom: 14 }}>
                        <input ref={fileInput} type="file" hidden onChange={upload}
                               accept=".pdf,.png,.jpg,.jpeg,.doc,.docx,.txt" />
                        <button className={styles.btnPrimary} disabled={uploading || record.readOnly} onClick={() => fileInput.current?.click()}>
                            <FiPaperclip aria-hidden="true" /> {uploading ? "Enviando…" : "Anexar arquivo"}
                        </button>
                        <span className={styles.muted}>PDF, imagem, Word ou texto, até 10 MB.</span>
                    </div>
                    {attachments.length === 0 ? (
                        <EmptyState icon={FiPaperclip} title="Nenhum anexo">
                            Guarde aqui laudos, encaminhamentos e outros documentos do paciente.
                        </EmptyState>
                    ) : (
                        <ul className={styles.list}>
                            {attachments.map((a) => (
                                <li key={a.id} className={styles.listItem}>
                                    <span>
                                        <strong>{a.fileName}</strong>{" "}
                                        <span className={styles.muted}>{size(a.sizeBytes)} · {formatDate(a.uploadedAt)}</span>
                                    </span>
                                    <span className={styles.toolbar}>
                                        <button className={styles.btnGhost} onClick={() => open(`/clinic/attachments/${a.id}`)}>
                                            <FiDownload aria-hidden="true" /> Baixar
                                        </button>
                                        <button className={styles.btnDanger} onClick={() => setRemoving(a)} aria-label={`Remover ${a.fileName}`}>
                                            <FiTrash2 aria-hidden="true" />
                                        </button>
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            {tab === "documentos" && !record.readOnly && (
                <div className={styles.card} style={{ marginBottom: 16 }}>
                    <WrittenDocumentsPanel request={request} patientId={patientId} onIssued={load} />
                </div>
            )}

            {tab === "documentos" && (
                <div className={styles.card}>
                    <h2>Documentos emitidos</h2>
                    {documents.length === 0 ? (
                        <EmptyState icon={FiFileText} title="Nenhum documento emitido">
                            Recibos, declarações, contratos, laudos e relatórios emitidos para este paciente ficam guardados aqui, com numeração.
                        </EmptyState>
                    ) : (
                        <ul className={styles.list}>
                            {documents.map((d) => (
                                <li key={d.id} className={styles.listItem}>
                                    <span>
                                        <strong>{d.title}</strong>{" "}
                                        <span className={styles.muted}>emitido em {formatDate(d.issuedAt)}</span>
                                    </span>
                                    <Link className={styles.btnGhost} to={`/documento/emitido/${d.id}`}>Abrir PDF</Link>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            {tab === "humor" && (
                <div className={styles.card}>
                    {moods.length === 0 ? (
                        <EmptyState title="Sem registros de humor">O paciente ainda não usou o diário de humor.</EmptyState>
                    ) : (
                        <ul className={styles.list}>
                            {moods.map((m) => (
                                <li key={m.date} className={styles.listItem}>
                                    <span>
                                        {moodOf(m.mood)?.emoji} <strong>{formatDate(m.date)}</strong> — {moodOf(m.mood)?.label}
                                        {sleepText(m) && <span className={styles.muted}> · {sleepText(m)}</span>}
                                    </span>
                                    {m.note && <span className={styles.muted}>{m.note}</span>}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            {tab === "escalas" && <InstrumentsPanel request={request} patientId={patientId} readOnly={record.readOnly} />}
            {tab === "tarefas" && <TasksPanel request={request} patientId={patientId} readOnly={record.readOnly} />}
            {tab === "plano" && <SafetyPlanPanel request={request} patientId={patientId} />}
            {tab === "termos" && <TermsPanel request={request} patientId={patientId} readOnly={record.readOnly} />}

            {removing && (
                <ConfirmDialog
                    title="Remover este anexo?"
                    message={`"${removing.fileName}" será apagado do prontuário.`}
                    confirmLabel="Remover"
                    onConfirm={removeAttachment}
                    onCancel={() => setRemoving(null)}
                />
            )}
        </div>
    );
}
