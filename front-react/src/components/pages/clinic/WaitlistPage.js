import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { useRoles } from "../../shared/UseRoles";
import { useApi, formatDate, whatsappLink, MODALITY_LABEL } from "./clinicApi";

const STATUS = {
    AGUARDANDO: { label: "Aguardando", tone: "warning" },
    CONTATADO: { label: "Contatado", tone: "info" },
    AGENDADO: { label: "Agendado", tone: "success" },
    DESISTIU: { label: "Desistiu", tone: "danger" },
};
const EMPTY = { name: "", phone: "", preference: "", modality: "", notes: "", professionalId: "" };

/** Lista de espera: quem aguarda vaga, por ordem de chegada. */
export default function WaitlistPage(props) {
    const request = useApi(props);
    const { isAdmin } = useRoles(props.keycloak);
    const [rows, setRows] = useState(null);
    const [professionals, setProfessionals] = useState([]);
    const [showAll, setShowAll] = useState(false);
    const [error, setError] = useState(null);
    const [form, setForm] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setError(null);
        request(`/clinic/waitlist?all=${showAll}`).then((data) => setRows(data || [])).catch(setError);
    }, [request, showAll]);

    useEffect(() => { load(); }, [load]);

    // Só a recepção escolhe para qual profissional é a espera.
    useEffect(() => {
        if (!isAdmin) return;
        request(`/directory/professionals`).then((data) => setProfessionals(data || [])).catch(() => setProfessionals([]));
    }, [request, isAdmin]);

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

    const add = async (e) => {
        e.preventDefault();
        if (await run(() => request(`/clinic/waitlist`, { method: "POST", body: form }), "Adicionado à lista de espera.")) setForm(null);
    };

    const setStatus = (row, status, message) =>
        run(() => request(`/clinic/waitlist/${row.id}/status/${status}`, { method: "PUT" }), message);

    const contact = (row) => {
        const link = whatsappLink(row.phone,
            `Olá, ${row.name.split(" ")[0]}! Abriu uma vaga para atendimento. Você ainda tem interesse? Posso sugerir um horário.`);
        if (!link) {
            toast.error("Não há um telefone válido para esta pessoa.");
            return;
        }
        window.open(link, "_blank", "noopener");
        if (row.status === "AGUARDANDO") setStatus(row, "CONTATADO", "Contato registrado.");
    };

    const waiting = (rows || []).filter((r) => r.status === "AGUARDANDO" || r.status === "CONTATADO").length;

    return (
        <div className={styles.page}>
            <PageHeader title="Lista de espera"
                        subtitle={rows ? `${waiting} pessoa(s) aguardando vaga, por ordem de chegada.` : undefined}>
                {!form && (
                    <button className={styles.btnPrimary} onClick={() => setForm(EMPTY)}>
                        <FiPlus aria-hidden="true" /> Adicionar à lista
                    </button>
                )}
            </PageHeader>
            {error && <ErrorState error={error} onRetry={load} />}
            {!rows && !error && <div className={styles.card}><Skeleton rows={5} /></div>}

            {rows && (
                <div className={styles.stack}>
                    {form && (
                        <div className={styles.card}>
                            <h2>Quem está esperando</h2>
                            <form className={styles.form} onSubmit={add}>
                                <div className={styles.row}>
                                    <label className={styles.field}>Nome
                                        <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>Telefone (WhatsApp)
                                        <input value={form.phone} placeholder="(11) 99999-0000" onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                                    </label>
                                </div>
                                <div className={styles.row}>
                                    <label className={styles.field}>Dias e horários que servem
                                        <input value={form.preference} placeholder="Ex.: terça ou quinta à tarde"
                                               onChange={(e) => setForm({ ...form, preference: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>Modalidade
                                        <select value={form.modality} onChange={(e) => setForm({ ...form, modality: e.target.value })}>
                                            <option value="">Tanto faz</option>
                                            <option value="PRESENCIAL">Presencial</option>
                                            <option value="ONLINE">Online</option>
                                        </select>
                                    </label>
                                </div>
                                {isAdmin && (
                                    <label className={styles.field}>Profissional
                                        <select value={form.professionalId} onChange={(e) => setForm({ ...form, professionalId: e.target.value })}>
                                            <option value="">Qualquer profissional</option>
                                            {professionals.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                                        </select>
                                    </label>
                                )}
                                <label className={styles.field}>Observações
                                    <input value={form.notes} placeholder="Ex.: indicado pela Dra. Marta; prefere atendimento infantil"
                                           onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                                </label>
                                <div className={styles.actions}>
                                    <button type="submit" className={styles.btnPrimary} disabled={busy}>Adicionar</button>
                                    <button type="button" className={styles.btnGhost} onClick={() => setForm(null)}>Cancelar</button>
                                </div>
                            </form>
                        </div>
                    )}

                    <div className={styles.card}>
                        <label className={styles.check} style={{ marginBottom: 12 }}>
                            <input type="checkbox" checked={showAll} onChange={(e) => setShowAll(e.target.checked)} />
                            Mostrar também quem já foi agendado ou desistiu
                        </label>
                        {rows.length === 0 ? (
                            <EmptyState title="Ninguém na lista de espera">
                                Quando não houver vaga, anote aqui quem pediu atendimento para chamar assim que um horário abrir.
                            </EmptyState>
                        ) : (
                            <div style={{ overflowX: "auto" }}>
                                <table className={styles.table}>
                                    <thead>
                                    <tr>
                                        <th>#</th><th>Nome</th><th>Preferência</th>{isAdmin && <th>Profissional</th>}
                                        <th>Desde</th><th>Situação</th><th></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {rows.map((row, index) => (
                                        <tr key={row.id}>
                                            <td>{index + 1}</td>
                                            <td>
                                                {row.name}
                                                {row.phone && <div className={styles.muted}>{row.phone}</div>}
                                                {row.notes && <div className={styles.muted}>{row.notes}</div>}
                                            </td>
                                            <td>
                                                {row.preference || "—"}
                                                {row.modality && <div className={styles.muted}>{MODALITY_LABEL[row.modality]}</div>}
                                            </td>
                                            {isAdmin && <td>{row.professionalName || "Qualquer"}</td>}
                                            <td>
                                                {formatDate(row.createdAt)}
                                                <div className={styles.muted}>{row.waitingDays} dia(s)</div>
                                            </td>
                                            <td><span className={`ui-badge ui-badge--${STATUS[row.status].tone}`}>{STATUS[row.status].label}</span></td>
                                            <td className={styles.numeric} style={{ whiteSpace: "nowrap" }}>
                                                {(row.status === "AGUARDANDO" || row.status === "CONTATADO") ? (
                                                    <>
                                                        <button className={styles.btnGhost} disabled={busy} onClick={() => contact(row)}>Chamar no WhatsApp</button>{" "}
                                                        <button className={styles.btnPrimary} disabled={busy}
                                                                onClick={() => setStatus(row, "AGENDADO", "Marcado como agendado.")}>Agendei</button>{" "}
                                                        <button className={styles.btnGhost} disabled={busy}
                                                                onClick={() => setStatus(row, "DESISTIU", "Marcado como desistência.")}>Desistiu</button>
                                                    </>
                                                ) : (
                                                    <button className={styles.btnGhost} disabled={busy}
                                                            onClick={() => setStatus(row, "AGUARDANDO", "Voltou para a fila.")}>Voltar para a fila</button>
                                                )}{" "}
                                                <button className={styles.btnDanger} disabled={busy} aria-label={`Remover ${row.name}`}
                                                        onClick={() => run(() => request(`/clinic/waitlist/${row.id}`, { method: "DELETE" }), "Removido da lista.")}>
                                                    <FiTrash2 aria-hidden="true" />
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
