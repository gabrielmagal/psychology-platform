import EntityCrudPage from "../dynamic/EntityCrudPage";
import React, { useState } from "react";
import SessionPaidDetailDialog from "./SessionPaidDetailDialog";
import styles from "../dynamic/EntityCrudPage.module.css";
import {Roles} from "../../shared/Roles";
import {useRoles} from "../../shared/UseRoles";
import { apiFetch, extractCleanErrorMessage } from "../../../utils/fetchWithAuth";

export default function SessionPackageCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sessionsTotal, setSessionsTotal] = useState(null);
    const [modalError, setModalError] = useState(null);
    const { isPatient } = useRoles(keycloak);

    // Estas chamadas usavam fetch cru: um 401 aqui não disparava o fluxo de sessão
    // expirada, e a mensagem real do backend era descartada.
    const request = (path, options) =>
        apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options });

    const fetchSessionsTotal = async (id) => {
        setLoading(true);
        setModalError(null);
        setSessionsTotal(null);
        try {
            setSessionsTotal(await request(`/session-package/${id}/payments`));
        } catch (err) {
            setModalError(extractCleanErrorMessage(err) || "Erro ao buscar total de sessões.");
        } finally {
            setLoading(false);
        }
    };

    const handleOpenModal = (item) => {
        setModalOpen(true);
        fetchSessionsTotal(item.id);
    };

    const handleCloseModal = () => {
        setModalOpen(false);
        setSessionsTotal(null);
        setModalError(null);
    };

    const handlePaymentRedirect = async (item) => {
        try {
            const raw = await request(`/mercado-pago-info/payment-preference/${item.id}`);
            const paymentUrl = String(raw ?? "").trim().replace(/^"|"$/g, "");

            if (!paymentUrl) {
                throw new Error("paymentUrl vazio.");
            }

            window.location.href = paymentUrl;
        } catch (e) {
            setModalError(e?.message || "Erro ao redirecionar para pagamento.");
            setModalOpen(true);
            console.error(e);
        }
    };

    return (
        <>
            <EntityCrudPage
                title={isPatient ? "🧠 Meus Pacotes" : "🧠 Pacote de Sessões"}
                resource="session-package"
                keycloak={keycloak}
                realm={realm}
                onUnauthorized={onUnauthorized}
                onForbidden={onForbidden}
                renderExtraAction={
                    [
                        // O paciente também consulta o que já pagou (e tira o recibo de cada pagamento).
                        (item) =>
                        (
                            <button
                                key="total-pago"
                                className={styles.actionBtn}
                                onClick={() => handleOpenModal(item)}
                            >
                                {isPatient ? "Meus pagamentos" : "Total Pago"}
                            </button>
                        ),
                        (item) =>
                            (
                                <button
                                    key="pagar"
                                    className={styles.actionBtn}
                                    onClick={() => handlePaymentRedirect(item)}
                                >
                                    {isPatient ? "Pagar com Mercado Pago" : "Efetuar Pagamento Mercado Pago"}
                                </button>
                            )
                    ]
                }
                allowedRolesToEdit={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
                allowedRolesToDelete={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
                allowedRolesToSubEntity={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
            />
            <SessionPaidDetailDialog
                open={modalOpen}
                onClose={handleCloseModal}
                loading={loading}
                error={modalError}
                data={sessionsTotal}
            />
        </>
    );

}

