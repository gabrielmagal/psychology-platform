import EntityCrudPage from "../dynamic/EntityCrudPage";
import React, { useState } from "react";
import { FiCreditCard, FiList } from "react-icons/fi";
import SessionPaidDetailDialog from "./SessionPaidDetailDialog";
import OnlinePaymentDialog from "./OnlinePaymentDialog";
import styles from "../dynamic/EntityCrudPage.module.css";
import { Roles } from "../../shared/Roles";
import { useRoles } from "../../shared/UseRoles";
import { apiFetch } from "../../../utils/fetchWithAuth";

export default function SessionPackageCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sessionsTotal, setSessionsTotal] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [paying, setPaying] = useState(null);
    const { isPatient } = useRoles(keycloak);

    const request = (path, options) =>
        apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options });

    const fetchSessionsTotal = async (id) => {
        setLoading(true);
        setModalError(null);
        setSessionsTotal(null);
        try {
            setSessionsTotal(await request(`/session-package/${id}/payments`));
        } catch (err) {
            setModalError(err);
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
                title={isPatient ? "Meus pacotes" : "Pacotes de sessões"}
                subtitle={isPatient
                    ? "Seus pacotes contratados, o que já foi pago e o pagamento online."
                    : "Pacotes contratados por paciente, com sessões, pagamentos e cobrança online."}
                itemName="pacote"
                emptyHint={isPatient
                    ? "Quando seu psicólogo cadastrar um pacote para você, ele aparece aqui."
                    : "Crie um pacote para um paciente e depois agende as sessões na Agenda."}
                resource="session-package"
                keycloak={keycloak}
                realm={realm}
                onUnauthorized={onUnauthorized}
                onForbidden={onForbidden}
                renderExtraAction={[
                    // O paciente também consulta o que já pagou (e tira o recibo de cada pagamento).
                    (item) => (
                        <button key="pagamentos" className={styles.actionBtn} style={{ gap: 6 }}
                                onClick={() => handleOpenModal(item)}>
                            <FiList aria-hidden="true" /> {isPatient ? "Meus pagamentos" : "Recibos"}
                        </button>
                    ),
                    (item) => (
                        <button key="pagar" className={styles.actionBtn} style={{ gap: 6 }}
                                onClick={() => setPaying(item)}>
                            <FiCreditCard aria-hidden="true" /> {isPatient ? "Pagar online" : "Cobrar online"}
                        </button>
                    ),
                ]}
                // O pacote é aberto pelo profissional que atende; o administrador acompanha e ajusta.
                allowedRolesToCreate={[Roles.PROFESSIONAL]}
                allowedRolesToEdit={[Roles.ADMIN, Roles.PROFESSIONAL]}
                allowedRolesToDelete={[Roles.ADMIN, Roles.PROFESSIONAL]}
                // Sessões e pagamentos em detalhe (com as anotações clínicas) ficam só com o psicólogo.
                allowedRolesToSubEntity={[Roles.ADMIN, Roles.PROFESSIONAL]}
            />
            <SessionPaidDetailDialog
                open={modalOpen}
                onClose={handleCloseModal}
                loading={loading}
                error={modalError}
                data={sessionsTotal}
            />
            {paying && (
                <OnlinePaymentDialog
                    pkg={paying}
                    isPatient={isPatient}
                    request={request}
                    onClose={() => setPaying(null)}
                />
            )}
        </>
    );
}
