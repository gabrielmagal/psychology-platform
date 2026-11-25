import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiCheckCircle, FiCopy } from "react-icons/fi";
import { Alert, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import { useApi } from "../clinic/clinicApi";

function CopyField({ label, value, hint }) {
    const copy = async () => {
        try {
            await navigator.clipboard.writeText(value);
            toast.success("Copiado.");
        } catch {
            toast.error("Não foi possível copiar. Selecione o texto e copie manualmente.");
        }
    };
    return (
        <label className="ui-field">
            {label}
            <div style={{ display: "flex", gap: 8 }}>
                <input readOnly value={value} onFocus={(e) => e.target.select()} />
                <button type="button" className="ui-btn ui-btn--icon" onClick={copy} aria-label={`Copiar ${label}`}>
                    <FiCopy aria-hidden="true" />
                </button>
            </div>
            {hint && <span className="ui-field-hint">{hint}</span>}
        </label>
    );
}

/** O profissional conecta a própria conta do Asaas para receber os pacotes online. */
export default function PaymentSettingsPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const [account, setAccount] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [saveError, setSaveError] = useState(null);
    const [apiKey, setApiKey] = useState("");
    const [sandbox, setSandbox] = useState(true);
    const [saving, setSaving] = useState(false);
    const [removing, setRemoving] = useState(false);

    const load = useCallback(async () => {
        setLoadError(null);
        try {
            const data = await request(`/online-payment/account`);
            setAccount(data);
            if (data?.configured) setSandbox(!!data.sandbox);
        } catch (err) {
            setLoadError(err);
        }
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const save = async (e) => {
        e.preventDefault();
        setSaveError(null);
        if (!apiKey.trim()) {
            setSaveError("Cole a chave de API gerada no Asaas.");
            return;
        }
        setSaving(true);
        try {
            await request(`/online-payment/account`, { method: "PUT", body: { apiKey, sandbox } });
            toast.success("Conta conectada. O Asaas aceitou a chave.");
            setApiKey("");
            load();
        } catch (err) {
            setSaveError(err);
        } finally {
            setSaving(false);
        }
    };

    const remove = async () => {
        setRemoving(false);
        try {
            await request(`/online-payment/account`, { method: "DELETE" });
            toast.success("Conta desconectada.");
            load();
        } catch (err) {
            setSaveError(err);
        }
    };

    return (
        <div className="ui-page">
            <PageHeader
                title="Recebimento online"
                subtitle="Conecte sua conta do Asaas para seus pacientes pagarem por Pix, boleto ou cartão. O dinheiro cai direto na sua conta."
            />

            {loadError && <ErrorState error={loadError} onRetry={load} />}
            {!account && !loadError && <div className="ui-card"><Skeleton rows={5} /></div>}

            {account && (
                <div className="ui-stack">
                    {account.configured ? (
                        <Alert tone="success" title="Conta do Asaas conectada" icon={FiCheckCircle}>
                            Chave terminada em <strong>{account.apiKeyHint}</strong>, no ambiente{" "}
                            <strong>{account.sandbox ? "de testes" : "de produção"}</strong>.
                            {account.sandbox && " No ambiente de testes nenhum dinheiro de verdade é movimentado."}
                        </Alert>
                    ) : (
                        <Alert tone="info" title="Nenhuma conta conectada">
                            Enquanto não houver uma conta conectada, o botão de pagamento online avisa o paciente
                            para combinar o pagamento diretamente com você.
                        </Alert>
                    )}

                    <form className="ui-card ui-form" onSubmit={save} noValidate>
                        <h2 className="ui-card-title">{account.configured ? "Trocar a chave" : "Conectar conta"}</h2>

                        {saveError && <ErrorState error={saveError} compact />}

                        <label className="ui-field">
                            Chave de API do Asaas
                            <input
                                type="password"
                                autoComplete="off"
                                placeholder="$aact_…"
                                value={apiKey}
                                onChange={(e) => setApiKey(e.target.value)}
                            />
                            <span className="ui-field-hint">
                                Gere a chave no painel do Asaas, na área de integrações. Ela é conferida no Asaas
                                antes de ser salva e nunca mais é exibida aqui.
                            </span>
                        </label>

                        <label className="ui-field">
                            Ambiente
                            <select value={sandbox ? "sandbox" : "production"}
                                    onChange={(e) => setSandbox(e.target.value === "sandbox")}>
                                <option value="sandbox">Testes (sandbox) — não movimenta dinheiro</option>
                                <option value="production">Produção — cobranças de verdade</option>
                            </select>
                            <span className="ui-field-hint">
                                A chave precisa ter sido gerada no mesmo ambiente escolhido.
                            </span>
                        </label>

                        <div className="ui-toolbar">
                            <button type="submit" className="ui-btn ui-btn--primary" disabled={saving}>
                                {saving ? "Conferindo no Asaas…" : account.configured ? "Salvar nova chave" : "Conectar conta"}
                            </button>
                            {account.configured && (
                                <button type="button" className="ui-btn ui-btn--danger-soft" onClick={() => setRemoving(true)}>
                                    Desconectar
                                </button>
                            )}
                        </div>
                    </form>

                    {account.configured && (
                        <div className="ui-card ui-form">
                            <h2 className="ui-card-title">Confirmação automática (webhook)</h2>
                            <p className="ui-muted">
                                Opcional, mas recomendado: com o webhook, o pagamento é confirmado sozinho, sem ninguém
                                precisar clicar em "verificar". No painel do Asaas, na área de integrações, crie um webhook
                                para eventos de <strong>cobranças</strong> e preencha com os dados abaixo.
                            </p>
                            <CopyField label="URL do webhook" value={account.webhookUrl}
                                       hint="Precisa ser um endereço público (https). Em ambiente local o Asaas não consegue chamar." />
                            <CopyField label="Token de autenticação" value={account.webhookToken}
                                       hint="É diferente da chave de API: serve só para o sistema reconhecer os avisos do Asaas." />
                        </div>
                    )}
                </div>
            )}

            {removing && (
                <ConfirmDialog
                    title="Desconectar a conta do Asaas?"
                    message="Seus pacientes deixam de conseguir pagar online até você conectar uma conta de novo. Os pagamentos já registrados continuam no sistema."
                    confirmLabel="Desconectar"
                    onConfirm={remove}
                    onCancel={() => setRemoving(false)}
                />
            )}
        </div>
    );
}
