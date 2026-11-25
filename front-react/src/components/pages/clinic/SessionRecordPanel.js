import React, { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiAlertTriangle, FiLock } from "react-icons/fi";
import { Alert, Skeleton } from "../../ui/Feedback";
import SectionedNote, { AutoTextarea } from "../../ui/SectionedNote";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import { errorText } from "../../../utils/errors";

/**
 * As seções seguem o que a Resolução CFP 001/2009 pede do registro: demanda, evolução do
 * trabalho, procedimentos adotados e encaminhamento.
 */
export const RECORD_SECTIONS = [
    { key: "demand", label: "Demanda e relato", hint: "O que o paciente trouxe nesta sessão.",
        placeholder: "Ex.: Relatou piora do sono depois da mudança de turno no trabalho." },
    { key: "observations", label: "Observações", hint: "O que você observou: humor, afeto, comportamento, pensamento.",
        placeholder: "Ex.: Humor ansioso, fala acelerada; contato visual preservado." },
    { key: "interventions", label: "Intervenções e técnicas", hint: "Os procedimentos que você utilizou.",
        placeholder: "Ex.: Psicoeducação sobre higiene do sono; registro de pensamentos." },
    { key: "evolution", label: "Evolução e análise", hint: "Como o paciente respondeu e sua leitura do processo.",
        placeholder: "Ex.: Boa adesão; identifica a relação entre preocupação e insônia." },
    { key: "plan", label: "Plano e encaminhamentos", hint: "Combinados, tarefas, foco da próxima sessão ou encaminhamento.",
        placeholder: "Ex.: Diário do sono por 7 dias. Próxima sessão: rever rotina noturna." },
];

const PRIVATE_SECTION = [
    { key: "privateNotes", label: "Notas privadas", private: true,
        hint: "Anotações pessoais suas. Não fazem parte do prontuário e ninguém mais as vê, nem a administração.",
        placeholder: "Hipóteses a confirmar, lembretes, pontos para supervisão…" },
];

function formatStamp(iso) {
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || "");
    return match ? `${match[3]}/${match[2]}/${match[1]} às ${match[4]}:${match[5]}` : "";
}

/**
 * Registro clínico de uma sessão: escrita enquanto está aberto, leitura e adendos depois de
 * concluído. Usado na agenda e no prontuário.
 */
export default function SessionRecordPanel({ sessionId, request, onSaved }) {
    const [record, setRecord] = useState(null);
    const [draft, setDraft] = useState({});
    const [dirty, setDirty] = useState(false);
    const [failed, setFailed] = useState(null);
    const [busy, setBusy] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [addendum, setAddendum] = useState("");

    const accept = (data) => {
        setRecord(data);
        setDraft(data);
        setDirty(false);
    };

    useEffect(() => {
        let cancelled = false;
        setRecord(null);
        request(`/clinic/agenda/${sessionId}/record`)
            .then((data) => { if (!cancelled) accept(data); })
            .catch((err) => { if (!cancelled && err?.name !== "AbortError") setFailed(err); });
        return () => { cancelled = true; };
    }, [request, sessionId]);

    const run = async (call, message) => {
        setBusy(true);
        try {
            accept(await call());
            toast.success(message);
            onSaved?.();
            return true;
        } catch (err) {
            toast.error(errorText(err));
            return false;
        } finally {
            setBusy(false);
        }
    };

    const save = () => run(() => request(`/clinic/agenda/${sessionId}/record`, { method: "PUT", body: draft }), "Registro salvo.");

    const sign = async () => {
        setConfirming(false);
        // Conclui o que está na tela: salva antes, para não fechar uma versão desatualizada.
        if (dirty && !(await save())) return;
        run(() => request(`/clinic/agenda/${sessionId}/record/sign`, { method: "PUT" }), "Registro concluído.");
    };

    const addAddendum = async () => {
        const done = await run(() => request(`/clinic/agenda/${sessionId}/record/addendum`,
            { method: "POST", body: { text: addendum } }), "Adendo acrescentado.");
        if (done) setAddendum("");
    };

    if (failed) return <Alert tone="danger" title="Não foi possível abrir o registro">{errorText(failed)}</Alert>;
    if (!record) return <Skeleton rows={4} />;

    const change = (values) => { setDraft(values); setDirty(true); };
    const hasContent = RECORD_SECTIONS.some((section) => (draft[section.key] || "").trim());

    return (
        <div className="ui-stack" style={{ gap: 16 }}>
            {record.signedAt && (
                <Alert tone="success" icon={FiLock} title={`Registro concluído em ${formatStamp(record.signedAt)}`}>
                    O texto não pode mais ser alterado. Para complementar, acrescente um adendo: ele fica datado.
                </Alert>
            )}
            {!record.signedAt && !record.canEdit && (
                <Alert tone="info" title="Somente leitura">O registro é escrito pelo profissional que atendeu a sessão.</Alert>
            )}

            {record.canEdit ? (
                <>
                    <SectionedNote sections={RECORD_SECTIONS} values={draft} onChange={change} />
                    <label className="ui-check">
                        <input type="checkbox" checked={!!draft.riskAlert}
                               onChange={(e) => change({ ...draft, riskAlert: e.target.checked })} />
                        <span>
                            Sinal de atenção nesta sessão
                            <small>Marca a sessão no prontuário para acompanhamento de perto (risco, crise, encaminhamento urgente).</small>
                        </span>
                    </label>
                    <SectionedNote sections={PRIVATE_SECTION} values={draft} onChange={change} />
                    <div className="ui-toolbar">
                        <button type="button" className="ui-btn ui-btn--primary" disabled={busy || !dirty} onClick={save}>
                            {busy ? "Salvando…" : dirty ? "Salvar registro" : "Registro salvo"}
                        </button>
                        <button type="button" className="ui-btn" disabled={busy || !hasContent} onClick={() => setConfirming(true)}>
                            <FiLock aria-hidden="true" /> Concluir registro
                        </button>
                    </div>
                </>
            ) : (
                <>
                    {record.riskAlert && (
                        <span className="ui-badge ui-badge--danger" style={{ alignSelf: "flex-start" }}>
                            <FiAlertTriangle aria-hidden="true" /> Sinal de atenção
                        </span>
                    )}
                    <SectionedNote sections={RECORD_SECTIONS} values={record} readOnly emptyText="Esta sessão ainda não tem registro." />
                    {record.privateNotes && <SectionedNote sections={PRIVATE_SECTION} values={record} readOnly />}
                </>
            )}

            {record.addenda && (
                <div className="ui-note-section">
                    <span className="ui-note-label">Adendos</span>
                    <div className="ui-note-read">{record.addenda}</div>
                </div>
            )}

            {record.canAddAddendum && (
                <div className="ui-note-section">
                    <label className="ui-note-label" htmlFor="record-addendum">Novo adendo</label>
                    <AutoTextarea id="record-addendum" value={addendum} onChange={setAddendum}
                                  placeholder="Informação que complementa o registro concluído." />
                    <div>
                        <button type="button" className="ui-btn" disabled={busy || !addendum.trim()} onClick={addAddendum}>
                            Acrescentar adendo
                        </button>
                    </div>
                </div>
            )}

            {confirming && (
                <ConfirmDialog
                    title="Concluir o registro desta sessão?"
                    message="Depois de concluído, o texto fica travado e passa a valer como registro do atendimento. Complementos entram como adendos datados."
                    confirmLabel="Concluir registro"
                    onConfirm={sign}
                    onCancel={() => setConfirming(false)}
                />
            )}
        </div>
    );
}
