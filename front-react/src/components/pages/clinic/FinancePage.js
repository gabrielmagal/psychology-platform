import React, { useCallback, useEffect, useState } from "react";
import dayjs from "dayjs";
import { toast } from "react-toastify";
import { FiChevronLeft, FiChevronRight, FiDownload, FiPlus, FiTrash2 } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { EmptyState, ErrorState, Skeleton } from "../../ui/Feedback";
import { errorText } from "../../../utils/errors";
import { saveBlob } from "../../../utils/fetchWithAuth";
import { useApi, useDownload, money, formatDate, whatsappLink } from "./clinicApi";
import TaxReceiptsPanel from "./TaxReceiptsPanel";

const MONTHS = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
const CATEGORIES = ["Aluguel", "Supervisão", "Conselho e anuidades", "Cursos e livros", "Sistemas e internet", "Impostos", "Outros"];
const TABS = [["mes", "Mês"], ["despesas", "Despesas"], ["ano", "Ano e Carnê-Leão"], ["receita", "Receita Saúde"]];
const isAdminOf = (keycloak) => (keycloak?.tokenParsed?.realm_access?.roles || []).includes("ADMINISTRADOR_ROLE");

export default function FinancePage(props) {
    const request = useApi(props);
    const download = useDownload(props);
    const isAdmin = isAdminOf(props.keycloak);
    const [tab, setTab] = useState("mes");
    const [month, setMonth] = useState(() => dayjs().startOf("month"));
    const [summary, setSummary] = useState(null);
    const [expenses, setExpenses] = useState([]);
    const [year, setYear] = useState(null);
    const [error, setError] = useState(null);
    const [form, setForm] = useState({ date: dayjs().format("YYYY-MM-DD"), description: "", category: "", amount: "", deductible: true });
    const [saving, setSaving] = useState(false);

    const from = month.format("YYYY-MM-DD");
    const to = month.endOf("month").format("YYYY-MM-DD");
    const yearNumber = month.year();

    const load = useCallback(async () => {
        setError(null);
        try {
            const [monthData, expenseRows, yearData] = await Promise.all([
                request(`/clinic/finance?from=${from}&to=${to}`),
                request(`/clinic/expenses?from=${from}&to=${to}`),
                request(`/clinic/finance/year?year=${yearNumber}`),
            ]);
            setSummary(monthData);
            setExpenses(expenseRows || []);
            setYear(yearData);
        } catch (err) {
            setError(err);
        }
    }, [request, from, to, yearNumber]);

    useEffect(() => { load(); }, [load]);

    const addExpense = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await request(`/clinic/expenses`, { method: "POST", body: form });
            toast.success("Despesa lançada.");
            setForm((f) => ({ ...f, description: "", amount: "" }));
            load();
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setSaving(false);
        }
    };

    const removeExpense = async (id) => {
        try {
            await request(`/clinic/expenses/${id}`, { method: "DELETE" });
            load();
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const exportCsv = async () => {
        try {
            const { blob, fileName } = await download(`/clinic/finance/year.csv?year=${yearNumber}`);
            saveBlob(blob, fileName || `livro-caixa-${yearNumber}.csv`);
        } catch (err) {
            toast.error(errorText(err));
        }
    };

    const spent = expenses.reduce((total, e) => total + Number(e.amount), 0);
    const debtors = summary?.debtors || [];
    const sessions = summary?.sessionsByStatus || {};

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>Financeiro</h1>
                    <p className={styles.subtitle}>{month.format("MMMM [de] YYYY").replace(/^./, (c) => c.toUpperCase())}</p>
                </div>
                <div className={styles.toolbar}>
                    <button className={styles.btnGhost} onClick={() => setMonth((m) => m.subtract(1, "month"))} aria-label="Mês anterior"><FiChevronLeft /></button>
                    <button className={styles.btnGhost} onClick={() => setMonth(dayjs().startOf("month"))}>Mês atual</button>
                    <button className={styles.btnGhost} onClick={() => setMonth((m) => m.add(1, "month"))} aria-label="Próximo mês"><FiChevronRight /></button>
                    <button className={styles.btnGhost} onClick={exportCsv}><FiDownload aria-hidden="true" /> Exportar {yearNumber} (CSV)</button>
                </div>
            </div>

            {error && <ErrorState error={error} onRetry={load} />}
            {!summary && !error && <div className={styles.card}><Skeleton rows={6} /></div>}

            {summary && (
                <div className={styles.stack}>
                    <div className={styles.grid}>
                        <div className={styles.stat}><div className={styles.statLabel}>Recebido no mês</div><div className={styles.statValue}>{money(summary.received)}</div></div>
                        <div className={styles.stat}><div className={styles.statLabel}>Despesas do mês</div><div className={styles.statValue}>{money(spent)}</div></div>
                        <div className={styles.stat}><div className={styles.statLabel}>Resultado do mês</div><div className={styles.statValue}>{money(Number(summary.received) - spent)}</div></div>
                        <div className={styles.stat}><div className={styles.statLabel}>A receber (todos os pacotes)</div><div className={styles.statValue}>{money(summary.outstanding)}</div></div>
                    </div>

                    <div className={styles.tabs} role="tablist">
                        {TABS.map(([id, label]) => (
                            <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? styles.tabActive : ""}
                                    onClick={() => setTab(id)}>{label}</button>
                        ))}
                    </div>

                    {tab === "mes" && (
                        <>
                            <div className={styles.card}>
                                <h2>Sessões do mês</h2>
                                <p className={styles.muted}>
                                    {sessions.REALIZADA || 0} realizada(s) · {sessions.AGENDADA || 0} agendada(s) · {sessions.FALTA || 0} falta(s) · {sessions.CANCELADA || 0} cancelada(s)
                                </p>
                            </div>
                            <div className={styles.card}>
                                <h2>Saldo em aberto</h2>
                                {debtors.length === 0 ? (
                                    <EmptyState title="Nenhum saldo em aberto">
                                        Entram aqui os pacotes e as sessões avulsas com valor definido e ainda não quitados.
                                    </EmptyState>
                                ) : (
                                    <div style={{ overflowX: "auto" }}>
                                        <table className={styles.table}>
                                            <thead><tr><th>Paciente</th><th>Referente a</th><th className={styles.numeric}>Valor</th><th className={styles.numeric}>Pago</th><th className={styles.numeric}>Saldo</th><th></th></tr></thead>
                                            <tbody>
                                            {debtors.map((d) => {
                                                const link = whatsappLink(d.patientPhone,
                                                    `Olá, ${d.patientName.split(" ")[0]}! Consta um saldo em aberto de ${money(d.balance)} referente a "${d.packageTitle || "pacote de sessões"}". Qualquer dúvida, estou à disposição.`);
                                                return (
                                                    <tr key={d.packageId}>
                                                        <td>{d.patientName}</td>
                                                        <td>{d.packageTitle || "—"}</td>
                                                        <td className={styles.numeric}>{money(d.price)}</td>
                                                        <td className={styles.numeric}>{money(d.paid)}</td>
                                                        <td className={styles.numeric}><strong>{money(d.balance)}</strong></td>
                                                        <td className={styles.numeric}>
                                                            {link && <a className={styles.btnGhost} href={link} target="_blank" rel="noopener noreferrer">Cobrar por WhatsApp</a>}
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </div>
                        </>
                    )}

                    {tab === "despesas" && (
                        <div className={styles.card}>
                            {isAdmin && (
                                <p className={styles.muted} style={{ marginBottom: 12 }}>
                                    Como administrador você vê as despesas de todos os profissionais. Cada um lança as suas.
                                </p>
                            )}
                            {!isAdmin && <h2>Lançar despesa</h2>}
                            <form className={styles.form} onSubmit={addExpense} hidden={isAdmin}>
                                <div className={styles.row}>
                                    <label className={styles.field}>Data
                                        <input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>Valor (R$)
                                        <input type="number" min="0.01" step="0.01" required placeholder="0,00" value={form.amount}
                                               onChange={(e) => setForm({ ...form, amount: e.target.value })} />
                                    </label>
                                </div>
                                <div className={styles.row}>
                                    <label className={styles.field}>Descrição
                                        <input required value={form.description} placeholder="Ex.: aluguel da sala"
                                               onChange={(e) => setForm({ ...form, description: e.target.value })} />
                                    </label>
                                    <label className={styles.field}>Categoria
                                        <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                                            <option value="">Sem categoria</option>
                                            {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                                        </select>
                                    </label>
                                </div>
                                <label className={styles.check}>
                                    <input type="checkbox" checked={form.deductible} onChange={(e) => setForm({ ...form, deductible: e.target.checked })} />
                                    Dedutível no Carnê-Leão (despesa necessária à atividade)
                                </label>
                                <div><button type="submit" className={styles.btnPrimary} disabled={saving}><FiPlus aria-hidden="true" /> {saving ? "Lançando…" : "Lançar despesa"}</button></div>
                            </form>

                            <h2 style={{ marginTop: 24 }}>Despesas de {month.format("MMMM")}</h2>
                            {expenses.length === 0 ? (
                                <EmptyState title="Nenhuma despesa neste mês" />
                            ) : (
                                <div style={{ overflowX: "auto" }}>
                                    <table className={styles.table}>
                                        <thead><tr><th>Data</th><th>Descrição</th><th>Categoria</th><th>Dedutível</th><th className={styles.numeric}>Valor</th><th></th></tr></thead>
                                        <tbody>
                                        {expenses.map((e) => (
                                            <tr key={e.id}>
                                                <td>{formatDate(e.date)}</td>
                                                <td>{e.description}</td>
                                                <td>{e.category || "—"}</td>
                                                <td>{e.deductible ? "Sim" : "Não"}</td>
                                                <td className={styles.numeric}>{money(e.amount)}</td>
                                                <td className={styles.numeric}>
                                                    <button className={styles.btnDanger} onClick={() => removeExpense(e.id)} aria-label={`Excluir ${e.description}`}><FiTrash2 aria-hidden="true" /></button>
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}

                    {tab === "receita" && <TaxReceiptsPanel request={request} year={yearNumber} readOnly={isAdmin} />}

                    {tab === "ano" && year && (
                        <div className={styles.card}>
                            <h2>{yearNumber} mês a mês</h2>
                            <p className={styles.muted} style={{ marginBottom: 12 }}>
                                Receita pelo dia em que foi recebida (regime de caixa). A base do Carnê-Leão é o recebido
                                menos as despesas dedutíveis; o imposto em si é calculado no programa da Receita.
                            </p>
                            <div style={{ overflowX: "auto" }}>
                                <table className={styles.table}>
                                    <thead><tr><th>Mês</th><th className={styles.numeric}>Recebido</th><th className={styles.numeric}>Despesas</th><th className={styles.numeric}>Dedutíveis</th><th className={styles.numeric}>Resultado</th><th className={styles.numeric}>Base Carnê-Leão</th></tr></thead>
                                    <tbody>
                                    {year.months.map((m) => (
                                        <tr key={m.month}>
                                            <td style={{ textTransform: "capitalize" }}>{MONTHS[m.month - 1]}</td>
                                            <td className={styles.numeric}>{money(m.income)}</td>
                                            <td className={styles.numeric}>{money(m.expenses)}</td>
                                            <td className={styles.numeric}>{money(m.deductible)}</td>
                                            <td className={styles.numeric}>{money(m.result)}</td>
                                            <td className={styles.numeric}><strong>{money(m.taxBase)}</strong></td>
                                        </tr>
                                    ))}
                                    </tbody>
                                    <tfoot>
                                    <tr>
                                        <td><strong>Total</strong></td>
                                        <td className={styles.numeric}><strong>{money(year.income)}</strong></td>
                                        <td className={styles.numeric}><strong>{money(year.expenses)}</strong></td>
                                        <td className={styles.numeric}><strong>{money(year.deductible)}</strong></td>
                                        <td className={styles.numeric}><strong>{money(year.result)}</strong></td>
                                        <td></td>
                                    </tr>
                                    </tfoot>
                                </table>
                            </div>
                            <div className={styles.actions}>
                                <button className={styles.btnGhost} onClick={exportCsv}>
                                    <FiDownload aria-hidden="true" /> Exportar lançamentos para o contador (CSV)
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
