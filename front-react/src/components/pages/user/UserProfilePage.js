import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiDownload } from "react-icons/fi";
import DynamicForm from "../dynamic/DynamicForm";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { useApi } from "../clinic/clinicApi";
import { Roles } from "../../shared/Roles";

export default function UserProfilePage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const [metadata, setMetadata] = useState(null);
    const [userData, setUserData] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [actionError, setActionError] = useState(null);

    const load = useCallback(() => {
        setLoadError(null);
        // As duas cargas são independentes: em paralelo, a tela abre no tempo da mais lenta.
        Promise.all([request(`/user/entity-description`), request(`/user/keycloak`)])
            .then(([meta, user]) => {
                // O próprio usuário não troca o seu tipo de acesso por aqui.
                const properties = { ...meta.properties };
                if (properties.userType) properties.userType = { ...properties.userType, showInForm: false };
                setMetadata({ ...meta, properties });
                setUserData(user);
            })
            .catch(setLoadError);
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const handleSubmit = async (data) => {
        try {
            const updated = await request(`/user/${userData.id}`, { method: "PUT", body: data });
            if (updated) setUserData(updated);
            toast.success("Perfil atualizado.");
        } catch (err) {
            setActionError(err);
        }
    };

    const exportMyData = async () => {
        try {
            const data = await request(`/clinic/my-data`);
            const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = "meus-dados.json";
            link.click();
            URL.revokeObjectURL(url);
        } catch (err) {
            setActionError(err);
        }
    };

    return (
        <div className="ui-page" style={{ maxWidth: 760 }}>
            <PageHeader title="Meu perfil" subtitle="Seus dados de cadastro e de contato." />

            {loadError && <ErrorState error={loadError} onRetry={load} />}
            {!loadError && (!metadata || !userData) && <div className="ui-card"><Skeleton rows={8} height={28} /></div>}

            {metadata && userData && (
                <div className="ui-stack">
                    <div className="ui-card">
                        <DynamicForm
                            metadata={metadata}
                            initialData={userData}
                            onSubmit={handleSubmit}
                            keycloak={keycloak}
                            realm={realm}
                            onUnauthorized={onUnauthorized}
                            onForbidden={onForbidden}
                            allowedRolesToEdit={[Roles.ADMIN, Roles.PROFESSIONAL, Roles.PATIENT]}
                            allowedRolesToDelete={[]}
                            isNestedForm={false}
                        />
                    </div>

                    <div className="ui-card">
                        <h2 className="ui-card-title">Meus dados (LGPD)</h2>
                        <p className="ui-muted" style={{ marginBottom: 14 }}>
                            Baixe uma cópia de tudo o que a plataforma guarda sobre você: cadastro, sessões,
                            pagamentos, diário de humor e consentimentos.
                        </p>
                        <button type="button" className="ui-btn" onClick={exportMyData}>
                            <FiDownload aria-hidden="true" /> Baixar meus dados
                        </button>
                    </div>
                </div>
            )}

            <ErrorDialog open={!!actionError} message={actionError} onClose={() => setActionError(null)} />
        </div>
    );
}
