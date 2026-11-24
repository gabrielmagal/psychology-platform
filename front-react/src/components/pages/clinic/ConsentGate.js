import React, { useEffect, useState } from "react";
import styles from "./Clinic.module.css";
import { useApi, errorMessage } from "./clinicApi";
import { useRoles } from "../../shared/UseRoles";

/**
 * Pede o consentimento do paciente para o tratamento dos dados (LGPD) antes do primeiro
 * uso. O aceite fica registrado no servidor com data e versão do termo; se o texto mudar,
 * a versão muda no backend e o aceite é pedido de novo.
 */
export default function ConsentGate({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const { isPatient } = useRoles(keycloak);
    const [pending, setPending] = useState(false);
    const [checked, setChecked] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!isPatient) return;
        let cancelled = false;
        request(`/clinic/consent`)
            .then((status) => { if (!cancelled && status && status.accepted === false) setPending(true); })
            // Sem resposta do servidor não se bloqueia a tela: o aceite é pedido no próximo acesso.
            .catch(() => {});
        return () => { cancelled = true; };
    }, [isPatient, request]);

    if (!pending) return null;

    const accept = async () => {
        setSaving(true);
        setError(null);
        try {
            await request(`/clinic/consent`, { method: "POST" });
            setPending(false);
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className={styles.overlay}>
            <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="consent-title">
                <h2 id="consent-title">Termo de consentimento</h2>
                <p>
                    Para o seu acompanhamento psicológico, esta plataforma guarda seus dados de cadastro, sua agenda
                    de sessões, seus pagamentos, os registros que você fizer no diário de humor e o prontuário mantido
                    pelo(a) seu(sua) psicólogo(a).
                </p>
                <p>
                    Esses dados são usados somente para o atendimento, ficam protegidos pelo sigilo profissional e só
                    são acessados por você e pelo profissional que atende você. O prontuário é guardado pelo prazo
                    exigido pelo Conselho Federal de Psicologia.
                </p>
                <p>
                    Você pode baixar uma cópia dos seus dados a qualquer momento em <strong>Ver Perfil</strong> e pedir
                    correção ou exclusão ao seu psicólogo, nos termos da Lei Geral de Proteção de Dados.
                </p>
                {error && <div className={styles.error}>{error}</div>}
                <label style={{ display: "flex", gap: "0.5rem", alignItems: "flex-start" }}>
                    <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
                    <span>Li e concordo com o tratamento dos meus dados para o atendimento psicológico.</span>
                </label>
                <div className={styles.actions}>
                    <button className={styles.btnPrimary} disabled={!checked || saving} onClick={accept}>
                        {saving ? "Registrando…" : "Aceitar e continuar"}
                    </button>
                    <button className={styles.btnGhost} onClick={() => keycloak.logout()}>Sair</button>
                </div>
            </div>
        </div>
    );
}
