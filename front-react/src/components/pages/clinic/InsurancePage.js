import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import { FiChevronLeft, FiChevronRight, FiEdit2, FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { useRoles } from "../../shared/UseRoles";
import { useApi, money, formatDate } from "./clinicApi";

const TABS = [["faturamento", "Faturamento"], ["pacientes", "Pacientes de convênio"], ["cadastro", "Convênios"]];
const STATUS = {
    A_FATURAR: { label: "A faturar", tone: "warning" },
    FATURADA: { label: "Faturada", tone: "info" },
    RECEBIDA: { label: "Recebida", tone: "success" },
};
const EMPTY = { id: null, name: "", ansCode: "", sessionValue: "", paymentDays: "", notes: "", active: true };
const today = () => dayjs().format("YYYY-MM-DD");

/** Convênios: o cadastro, quais pacotes são de convênio e o que foi faturado e recebido. */
export default function InsurancePage(props) {
    const request = useApi(props);
    const { isAdmin } = useRoles(props.keycloak);
    const [tab, setTab] = useState("faturamento");
    const [month, setMonth] = useState(() => dayjs().startOf("month"));
    const [insurances, setInsurances] = useState(null);
    const [packages, setPackages] = useState([]);
    const [report, setReport] = useState(null);
    const [filter, setFilter] = useState("");
    const [error, setError] = useState(null);
    const [form, setForm] = useState(null);
    const [busy, setBusy] = useState(false);

    const from = month.format("YYYY-MM-DD");
    const to = month.endOf("month").format("YYYY-MM-DD");

    const load = useCallback(async () => {
        setError(null);
        try {
            const [list, linked, sessions] = await Promise.all([
                request(`/clinic/insurances`),
                request(`/clinic/insurance-packages`),
                request(`/clinic/insurance-sessions?from=${from}&to=${to}${filter ? `&insuranceId=${filter}` : ""}`),
            ]);
            setInsurances(list || []);
            setPackages(linked || []);
            setReport(sessions);
        } catch (err) {
            setError(err);
        }
    }, [request, from, to, filter]);

    useEffect(() => { load(); }, [load]);

    const run = async (action, message) => {
        setBusy(true);
        try {
            await action();
            if (message) toast.success(message);
            await load();
            return true;
        } catch (err) {
            toast.error(errorText(err));
            return false;
        } finally {
            setBusy(false);
        }
    };

    const saveInsurance = async (e) => {
        e.preventDefault();
        const saved = await run(() => request(`/clinic/insurances`, { method: "POST", body: form }), "Convênio salvo.");
        if (saved) setForm(null);
    };

    const updateSession = (row, body, message) =>
        run(() => request(`/clinic/insurance-sessions/${row.sessionId}`, { method: "PUT", body }), message);

    const linkPackage = (row, patch) => run(() => request(`/clinic/insurance-packages/${row.packageId}`, {
        method: "PUT",
        body: { insuranceId: row.insuranceId, cardNumber: row.cardNumber, ...patch },
    }));

    const active = (insurances || []).filter((i) => i.active);
    const rows = report?.rows || [];

    return (
        <div className={styles.page}>
            <PageHeader title="Convênios" subtitle="Sessões por plano de saúde: o que falta faturar e o que falta receber." />
            {error && <ErrorState error={error} onRetry={load} />}
            {!insurances && !error && <div className={styles.card}><Skeleton rows={6} /></div>}

            {insurances && (
                <div className={styles.stack}>
                    <div className={styles.tabs} role="tablist">
                        {TABS.map(([id, label]) => (
                            <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? styles.tabActive : ""}
                                    onClick={() => setTab(id)}>{label}</button>
                        ))}
                    </div>

                    {tab === "faturamento" && report && (
                        <>
                            <div className={styles.toolbar}>
                                <button className={styles.btnGhost} onClick={() => setMonth((m) => m.subtract(1, "month"))} aria-label="Mês anterior"><FiChevronLeft /></button>
                                <strong style={{ textTransform: "capitalize" }}>{month.format("MMMM [de] YYYY")}</strong>
                                <button className={styles.btnGhost} onClick={() => setMonth((m) => m.add(1, "month"))} aria-label="Próximo mês"><FiChevronRight /></button>
                                <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filtrar por convênio">
                                    <option value="">Todos os convênios</option>
                                    {insurances.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
                                </select>
                            </div>
                            <div className={styles.grid}>
                                {Object.entries(STATUS).map(([key, { label }]) => (
                                    <div key={key} className={styles.stat}>
                                        <div className={styles.statLabel}>{label} ({report.counts[key] || 0})</div>
                                        <div className={styles.statValue}>{money(report.totals[key])}</div>
                                    </div>
                                ))}
                            </div>
                            <div className={styles.card}>
                                <h2>Sessões realizadas no mês</h2>
                                {rows.length === 0 ? (
                                    <EmptyState title="Nenhuma sessão de convênio neste mês">
                                        Entram aqui as sessões marcadas como realizadas em pacotes vinculados a um convênio.
                                    </EmptyState>
                                ) : (
                                    <div style={{ overflowX: "auto" }}>
                                        <table className={styles.table}>
                                            <thead>
                                            <tr>
                                                <th>Data</th><th>Paciente</th><th>Convênio</th>{isAdmin && <th>Profissional</th>}
                                                <th>Guia</th><th className={styles.numeric}>Valor</th><th>Situação</th><th></th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {rows.map((row) => (
                                                <tr key={row.sessionId}>
                                                    <td>{formatDate(row.date)}</td>
                                                    <td>
                                                        {row.patientName}
                                                        {row.cardNumber && <div className={styles.muted}>Carteirinha {row.cardNumber}</div>}
                                                    </td>
                                                    <td>{row.insuranceName}</td>
                                                    {isAdmin && <td>{row.professionalName || "—"}</td>}
                                                    <td>
                                                        <input defaultValue={row.guide || ""} placeholder="Nº da guia" style={{ width: 120 }}
                                                               aria-label={`Guia de ${row.patientName}`}
                                                               onBlur={(e) => e.target.value !== (row.guide || "")
                                                                   && updateSession(row, { guide: e.target.value }, "Guia registrada.")} />
                                                    </td>
                                                    <td className={styles.numeric}>
                                                        <input type="number" min="0" step="0.01" defaultValue={row.amount ?? ""} style={{ width: 100, textAlign: "right" }}
                                                               aria-label={`Valor de ${row.patientName}`}
                                                               onBlur={(e) => Number(e.target.value) !== Number(row.amount ?? "")
                                                                   && updateSession(row, { amount: e.target.value }, "Valor atualizado.")} />
                                                    </td>
                                                    <td>
                                                        <span className={`ui-badge ui-badge--${STATUS[row.status].tone}`}>{STATUS[row.status].label}</span>
                                                        {row.status === "FATURADA" && (
                                                            <div className={styles.muted}>
                                                                em {formatDate(row.billedAt)}{row.expectedAt ? ` · previsto para ${formatDate(row.expectedAt)}` : ""}
                                                            </div>
                                                        )}
                                                        {row.status === "RECEBIDA" && <div className={styles.muted}>em {formatDate(row.paidAt)}</div>}
                                                    </td>
                                                    <td className={styles.numeric} style={{ whiteSpace: "nowrap" }}>
                                                        {row.status === "A_FATURAR" && (
                                                            <button className={styles.btnPrimary} disabled={busy}
                                                                    onClick={() => updateSession(row, { billedAt: today() }, "Sessão faturada.")}>Faturar</button>
                                                        )}
                                                        {row.status === "FATURADA" && (
                                                            <>
                                                                <button className={styles.btnPrimary} disabled={busy}
                                                                        onClick={() => updateSession(row, { paidAt: today() }, "Recebimento registrado.")}>Recebi</button>{" "}
                                                                <button className={styles.btnGhost} disabled={busy}
                                                                        onClick={() => updateSession(row, { billedAt: "" }, "Faturamento desfeito.")}>Desfazer</button>
                                                            </>
                                                        )}
                                                        {row.status === "RECEBIDA" && (
                                                            <button className={styles.btnGhost} disabled={busy}
                                                                    onClick={() => updateSession(row, { paidAt: "" }, "Recebimento desfeito.")}>Desfazer</button>
                                                        )}
                                                    </td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                                <p className={styles.muted} style={{ marginTop: 12 }}>
                                    O repasse do convênio não entra no Carnê-Leão: quem paga é empresa, com imposto retido na fonte.
                                    Por isso ele é acompanhado aqui, separado do livro-caixa.
                                </p>
                            </div>
                        </>
                    )}

                    {tab === "pacientes" && (
                        <div className={styles.card}>
                            <h2>Pacotes e convênios</h2>
                            {active.length === 0 && (
                                <Alert tone="info" title="Cadastre um convênio primeiro">
                                    Depois de cadastrar, escolha aqui quais pacotes são atendidos por ele.
                                </Alert>
                            )}
                            {packages.length === 0 ? (
                                <EmptyState title="Nenhum pacote de sessões">Crie o pacote do paciente e volte aqui para vinculá-lo ao convênio.</EmptyState>
                            ) : (
                                <div style={{ overflowX: "auto" }}>
                                    <table className={styles.table}>
                                        <thead><tr><th>Paciente</th><th>Pacote</th><th>Convênio</th><th>Carteirinha</th></tr></thead>
                                        <tbody>
                                        {packages.map((row) => (
                                            <tr key={row.packageId}>
                                                <td>{row.patientName}</td>
                                                <td>{row.packageTitle || "—"}</td>
                                                <td>
                                                    <select value={row.insuranceId || ""} disabled={busy}
                                                            aria-label={`Convênio de ${row.patientName}`}
                                                            onChange={(e) => linkPackage(row, { insuranceId: e.target.value || null })}>
                                                        <option value="">Particular</option>
                                                        {insurances.filter((i) => i.active || i.id === row.insuranceId)
                                                            .map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
                                                    </select>
                                                </td>
                                                <td>
                                                    {row.insuranceId && (
                                                        <input defaultValue={row.cardNumber || ""} placeholder="Nº da carteirinha"
                                                               aria-label={`Carteirinha de ${row.patientName}`}
                                                               onBlur={(e) => e.target.value !== (row.cardNumber || "")
                                                                   && linkPackage(row, { cardNumber: e.target.value })} />
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}

                    {tab === "cadastro" && (
                        <div className={styles.card}>
                            <div className={styles.header}>
                                <h2>Convênios da clínica</h2>
                                {!form && (
                                    <button className={styles.btnPrimary} onClick={() => setForm(EMPTY)}>
                                        <FiPlus aria-hidden="true" /> Novo convênio
                                    </button>
                                )}
                            </div>
                            {form && (
                                <form className={styles.form} onSubmit={saveInsurance} style={{ marginBottom: 20 }}>
                                    <div className={styles.row}>
                                        <label className={styles.field}>Nome
                                            <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                                        </label>
                                        <label className={styles.field}>Registro ANS
                                            <input value={form.ansCode || ""} onChange={(e) => setForm({ ...form, ansCode: e.target.value })} />
                                        </label>
                                    </div>
                                    <div className={styles.row}>
                                        <label className={styles.field}>Valor por sessão (R$)
                                            <input type="number" min="0" step="0.01" value={form.sessionValue ?? ""}
                                                   onChange={(e) => setForm({ ...form, sessionValue: e.target.value })} />
                                        </label>
                                        <label className={styles.field}>Prazo de repasse (dias)
                                            <input type="number" min="0" max="365" value={form.paymentDays ?? ""}
                                                   onChange={(e) => setForm({ ...form, paymentDays: e.target.value })} />
                                        </label>
                                    </div>
                                    <label className={styles.field}>Observações
                                        <input value={form.notes || ""} placeholder="Ex.: guia precisa de autorização prévia"
                                               onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                                    </label>
                                    <label className={styles.check}>
                                        <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                                        Ativo (aparece para novos vínculos)
                                    </label>
                                    <div className={styles.actions}>
                                        <button type="submit" className={styles.btnPrimary} disabled={busy}>Salvar</button>
                                        <button type="button" className={styles.btnGhost} onClick={() => setForm(null)}>Cancelar</button>
                                    </div>
                                </form>
                            )}
                            {insurances.length === 0 ? (
                                <EmptyState title="Nenhum convênio cadastrado" />
                            ) : (
                                <div style={{ overflowX: "auto" }}>
                                    <table className={styles.table}>
                                        <thead><tr><th>Nome</th><th>ANS</th><th className={styles.numeric}>Valor por sessão</th><th>Repasse</th><th>Pacotes</th><th>Situação</th><th></th></tr></thead>
                                        <tbody>
                                        {insurances.map((i) => (
                                            <tr key={i.id}>
                                                <td>{i.name}{i.notes && <div className={styles.muted}>{i.notes}</div>}</td>
                                                <td>{i.ansCode || "—"}</td>
                                                <td className={styles.numeric}>{i.sessionValue == null ? "—" : money(i.sessionValue)}</td>
                                                <td>{i.paymentDays == null ? "—" : `${i.paymentDays} dias`}</td>
                                                <td>{i.packages}</td>
                                                <td><span className={`ui-badge ui-badge--${i.active ? "success" : "warning"}`}>{i.active ? "Ativo" : "Desativado"}</span></td>
                                                <td className={styles.numeric} style={{ whiteSpace: "nowrap" }}>
                                                    <button className={styles.btnGhost} onClick={() => setForm({ ...EMPTY, ...i })} aria-label={`Editar ${i.name}`}><FiEdit2 aria-hidden="true" /></button>{" "}
                                                    <button className={styles.btnDanger} disabled={busy} aria-label={`Excluir ${i.name}`}
                                                            onClick={() => run(() => request(`/clinic/insurances/${i.id}`, { method: "DELETE" }), "Convênio excluído.")}>
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
                    )}
                </div>
            )}
        </div>
    );
}
