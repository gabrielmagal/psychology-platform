import EntityCrudPage from "../dynamic/EntityCrudPage";
import React, { useState } from "react";
import SessionPaidDetailDialog from "./SessionPaidDetailDialog";
import styles from "../dynamic/EntityCrudPage.module.css";

export default function SessionPackageCrud({ keycloak, realm }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sessionsTotal, setSessionsTotal] = useState(null);
    const [modalError, setModalError] = useState(null);

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

    return (
        <>
            <EntityCrudPage
                title="Pacote de Sessões"
                resource="session-package"
                keycloak={keycloak}
                realm={realm}
                renderExtraAction={[
                    (item) =>
                    (
                        <button
                            className={styles.actionBtn}
                            onClick={() => handleOpenModal(item)}
                        >
                            Total Pago
                        </button>
                    )
                ]}
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

