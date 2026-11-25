import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { FiFileText, FiLock, FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, Skeleton } from "../../ui/Feedback";
import SectionedNote from "../../ui/SectionedNote";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import { errorText } from "../../../utils/errors";
import { formatDate } from "./clinicApi";

/**
 * Atestado, relatório, laudo, parecer e encaminhamento: o profissional escreve por seções (as
 * da Resolução CFP 06/2019), guarda como rascunho e emite o PDF numerado quando terminar.
 */
export default function WrittenDocumentsPanel({ request, patientId, onIssued }) {
    const [catalog, setCatalog] = useState(null);
    const [rows, setRows] = useState([]);
    const [failed, setFailed] = useState(null);
    const [draft, setDraft] = useState(null);
    const [dirty, setDirty] = useState(false);
    const [busy, setBusy] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [start, setStart] = useState("");

    const load = useCallback(async () => {
        setFailed(null);
        try {
            const [kinds, list] = await Promise.all([
                request(`/clinic/written-documents/catalog`),
                request(`/clinic/written-documents?patientId=${patientId}`),
            ]);
            setCatalog(kinds);
            setRows(list || []);
        } catch (err) {
            setFailed(err);
        }
    }, [request, patientId]);

    useEffect(() => { load(); }, [load]);

    if (failed) return <Alert tone="danger" title="Não foi possível carregar os documentos">{errorText(failed)}</Alert>;
    if (!catalog) return <Skeleton rows={3} />;

    const kindOf = (code) => catalog.kinds.find((k) => k.code === code);

    const begin = () => {
        if (!start) return;
        const template = catalog.templates.find((t) => `t:${t.id}` === start);
        const kind = kindOf(template ? template.kind : start);
        setDraft({ id: null, kind: kind.code, title: kind.label, sections: template ? { ...template.sections } : {} });
        setDirty(true);
        setStart("");
    };

    const save = async (silent) => {
        setBusy(true);
        try {
            const saved = await request(`/clinic/written-documents`, { method: "POST", body: { ...draft, patientId } });
            setDraft(saved);
            setDirty(false);
            if (!silent) toast.success("Rascunho salvo.");
            load();
            return saved;
        } catch (err) {
            toast.error(errorText(err));
            return null;
        } finally {
            setBusy(false);
        }
    };

    const issue = async () => {
        setConfirming(false);
        const saved = dirty ? await save(true) : draft;
        if (!saved) return;
        setBusy(true);
        try {
            await request(`/clinic/written-documents/${saved.id}/issue`, { method: "PUT" });
            toast.success("Documento emitido.");
            setDraft(null);
            await load();
            onIssued?.();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const saveAsTemplate = async () => {
        setBusy(true);
        try {
            await request(`/clinic/written-documents/templates`, { method: "POST", body: { kind: draft.kind, title: draft.title, sections: draft.sections } });
            toast.success("Modelo salvo. Ele aparece na lista ao criar um documento.");
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(false);
        }
    };

    const remove = async (path, message) => {
        try {
            await request(path, { method: "DELETE" });
            toast.success(message);
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    if (draft) {
        const kind = kindOf(draft.kind);
        return (
            <div className={styles.form}>
                <h2>{kind.label}</h2>
                <p className={styles.muted}>{kind.description}</p>
                <label className={styles.field}>Título do rascunho (só para você se localizar)
                    <input maxLength={200} value={draft.title} onChange={(e) => { setDraft({ ...draft, title: e.target.value }); setDirty(true); }} />
                </label>
                <SectionedNote sections={kind.sections} values={draft.sections}
                               onChange={(sections) => { setDraft({ ...draft, sections }); setDirty(true); }} />
                <p className={styles.muted}>
                    O nome e o CPF do paciente, o seu nome e o seu CRP entram sozinhos no cabeçalho e na assinatura do PDF.
                </p>
                <div className={styles.actions}>
                    <button className={styles.btnPrimary} disabled={busy || !dirty} onClick={() => save(false)}>
                        {dirty ? "Salvar rascunho" : "Rascunho salvo"}
                    </button>
                    <button className={styles.btnGhost} disabled={busy} onClick={() => setConfirming(true)}><FiLock aria-hidden="true" /> Emitir PDF</button>
                    <button className={styles.btnGhost} disabled={busy} onClick={saveAsTemplate}>Salvar como modelo</button>
                    <button className={styles.btnGhost} onClick={() => { setDraft(null); setDirty(false); }}>Fechar</button>
                </div>
                {confirming && (
                    <ConfirmDialog
                        title={`Emitir este ${kind.label.toLowerCase()}?`}
                        message="O documento recebe número e vira PDF. Depois de emitido, o texto não pode mais ser alterado."
                        confirmLabel="Emitir"
                        onConfirm={issue}
                        onCancel={() => setConfirming(false)}
                    />
                )}
            </div>
        );
    }

    const drafts = rows.filter((r) => !r.issued);

    return (
        <div className={styles.stack}>
            <div className={styles.toolbar}>
                <select value={start} onChange={(e) => setStart(e.target.value)} aria-label="Tipo de documento">
                    <option value="">Escrever documento…</option>
                    <optgroup label="Em branco">
                        {catalog.kinds.map((k) => <option key={k.code} value={k.code}>{k.label}</option>)}
                    </optgroup>
                    {catalog.templates.length > 0 && (
                        <optgroup label="A partir de um modelo meu">
                            {catalog.templates.map((t) => <option key={t.id} value={`t:${t.id}`}>{t.title}</option>)}
                        </optgroup>
                    )}
                </select>
                <button className={styles.btnPrimary} disabled={!start} onClick={begin}><FiPlus aria-hidden="true" /> Começar</button>
            </div>

            {drafts.length === 0 ? (
                <EmptyState icon={FiFileText} title="Nenhum rascunho">
                    Atestado, relatório, laudo, parecer e encaminhamento, com as seções pedidas pelo CFP.
                </EmptyState>
            ) : (
                <ul className={styles.list}>
                    {drafts.map((row) => (
                        <li key={row.id} className={styles.listItem}>
                            <span>
                                <strong>{row.title}</strong> <span className="ui-badge ui-badge--warning">Rascunho</span>
                                <div className={styles.muted}>{row.kindLabel} · alterado em {formatDate(row.updatedAt)}</div>
                            </span>
                            <span className={styles.toolbar}>
                                <button className={styles.btnGhost} onClick={() => { setDraft(row); setDirty(false); }}>Continuar</button>
                                <button className={styles.btnDanger} aria-label={`Apagar ${row.title}`}
                                        onClick={() => remove(`/clinic/written-documents/${row.id}`, "Rascunho apagado.")}>
                                    <FiTrash2 aria-hidden="true" />
                                </button>
                            </span>
                        </li>
                    ))}
                </ul>
            )}

            {catalog.templates.length > 0 && (
                <details>
                    <summary className={styles.muted}>Meus modelos ({catalog.templates.length})</summary>
                    <ul className={styles.list}>
                        {catalog.templates.map((t) => (
                            <li key={t.id} className={styles.listItem}>
                                <span><strong>{t.title}</strong> <span className={styles.muted}>{kindOf(t.kind)?.label}</span></span>
                                <button className={styles.btnDanger} aria-label={`Apagar o modelo ${t.title}`}
                                        onClick={() => remove(`/clinic/written-documents/templates/${t.id}`, "Modelo apagado.")}>
                                    <FiTrash2 aria-hidden="true" />
                                </button>
                            </li>
                        ))}
                    </ul>
                </details>
            )}
            <p className={styles.muted}>
                Os documentos emitidos ficam na lista abaixo, com número. O paciente não os vê pelo aplicativo: a entrega é sua,
                na devolutiva. <Link to="/instrumentos">Instrumentos aplicados</Link> podem ser citados em "Procedimento".
            </p>
        </div>
    );
}
