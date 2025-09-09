import EntityCrudPage from "../dynamic/EntityCrudPage";
import React, { useState } from "react";
import SessionPaidDetailDialog from "./SessionPaidDetailDialog";
import styles from "../dynamic/EntityCrudPage.module.css";
import {Roles} from "../../shared/Roles";
import {useRoles} from "../../shared/UseRoles";

export default function SessionPackageCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sessionsTotal, setSessionsTotal] = useState(null);
    const [modalError, setModalError] = useState(null);
    const { isAdmin, isPsychologist, isPatient } = useRoles(keycloak);

    const fetchSessionsTotal = async (id) => {
        setLoading(true);
        setModalError(null);
        setSessionsTotal(null);
        try {
            const apiUrl = process.env.REACT_APP_API_URL;
            const res = await fetch(`${apiUrl}/session-package/${id}/payments`, {
                headers: {
                    Authorization: "Bearer " + keycloak.token,
                    Tenant: realm,
                },
            });
            if (!res.ok) throw new Error("Erro ao buscar total.");
            const data = await res.json();
            setSessionsTotal(data);
        } catch {
            setModalError("Erro ao buscar total de sessões.");
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
            const apiUrl = process.env.REACT_APP_API_URL;
            const res = await fetch(`${apiUrl}/mercado-pago-info/payment-preference/${item.id}`, {
                method: 'GET',
                headers: {
                    Authorization: "Bearer " + keycloak.token,
                    Tenant: realm,
                },
            });

            if (!res.ok) throw new Error("Erro ao iniciar pagamento");
            const { paymentUrl } = await res.json();
            window.location.href = paymentUrl;
        } catch (e) {
            alert("Erro ao redirecionar para pagamento.");
            console.error(e);
        }
    };

    return (
        <>
            <EntityCrudPage
                title="🧠 Pacote de Sessões"
                resource="session-package"
                keycloak={keycloak}
                realm={realm}
                onUnauthorized={onUnauthorized}
                onForbidden={onForbidden}
                renderExtraAction={
                    [
                        (item) =>
                        (
                            (isAdmin || isPsychologist) &&
                            (
                                <button
                                    className={styles.actionBtn}
                                    onClick={() => handleOpenModal(item)}
                                >
                                    Total Pago
                                </button>
                            )
                        ),
                        (item) =>
                            (
                                <button
                                    className={styles.actionBtn}
                                    onClick={() => handlePaymentRedirect(item)}
                                >
                                    Efetuar Pagamento Mercado Pago
                                </button>
                            )
                    ]
                }
                allowedRolesToEdit={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
                allowedRolesToDelete={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
                allowedRolesToSubEntity={[Roles.ADMIN, Roles.PSYCHOLOGIST, Roles.PATIENT]}
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

