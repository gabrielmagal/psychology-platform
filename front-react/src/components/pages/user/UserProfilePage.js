import React, { useEffect, useState } from "react";
import DynamicForm from "../dynamic/DynamicForm";
import SuccessDialog from "../../shared/dialog/SuccessDialog";
import { apiFetch, extractCleanErrorMessage } from "../../../utils/fetchWithAuth";
import {Roles} from "../../shared/Roles";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { Card, CardContent } from "@mui/material";

export default function UserProfilePage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const [metadata, setMetadata] = useState(null);
    const [userData, setUserData] = useState(null);

    const [successMessage, setSuccessMessage] = useState(null);
    const [alertMessage, setAlertMessage] = useState(null);

    const request = (path, options) =>
        apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options });

    const showError = (err) => {
        const message = extractCleanErrorMessage(err);
        if (message) setAlertMessage(message);
    };

    useEffect(() => {
        let cancelled = false;

        // As duas cargas são independentes: em paralelo, a tela abre no tempo da mais
        // lenta, não na soma das duas. E os erros passam a aparecer para o usuário —
        // antes iam só para o console e a página ficava em "Carregando perfil..." eterno.
        Promise.all([
            request(`/user/entity-description`),
            request(`/user/keycloak`),
        ]).then(([meta, user]) => {
            if (cancelled) return;
            setMetadata(meta);
            setUserData(user);
        }).catch((err) => {
            if (!cancelled) showError(err);
        });

        return () => { cancelled = true; };
    }, []);

    const handleSubmit = async (data) => {
        try {
            const updated = await request(`/user/${userData.id}`, { method: "PUT", body: data });
            if (updated) setUserData(updated);
            setSuccessMessage("Perfil atualizado com sucesso!");
        } catch (err) {
            showError(err);
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
            showError(err);
        }
    };

    if (!metadata || !userData) {
        return (
            <>
                <p style={{ textAlign: "center", marginTop: "3rem" }}>Carregando perfil...</p>
                {alertMessage && (
                    <ErrorDialog
                        open={!!alertMessage}
                        message={alertMessage}
                        onClose={() => setAlertMessage(null)}
                    />
                )}
            </>
        );
    }

    return (
        <div style={{ maxWidth: 700, margin: "3rem auto" }}>
            <Card sx={{ borderRadius: 4, boxShadow: 4 }}>
                <CardContent>
                    <DynamicForm
                        metadata={metadata}
                        initialData={userData}
                        onSubmit={handleSubmit}
                        keycloak={keycloak}
                        realm={realm}
                        onUnauthorized={onUnauthorized}
                        onForbidden={onForbidden}
                        allowedRolesToEdit={[Roles.ADMIN, Roles.PSYCHOLOGIST, Roles.PATIENT]}
                        allowedRolesToDelete={[]}
                        isNestedForm={false}
                    />
                </CardContent>
            </Card>

            <Card sx={{ borderRadius: 4, boxShadow: 4, marginTop: 3 }}>
                <CardContent>
                    <h3 style={{ marginTop: 0 }}>Meus dados (LGPD)</h3>
                    <p style={{ color: "#6c757d" }}>
                        Baixe uma cópia de tudo o que a plataforma guarda sobre você: cadastro, sessões,
                        pagamentos, diário de humor e consentimentos.
                    </p>
                    <button type="button" onClick={exportMyData}
                            style={{ background: "#007bff", color: "#fff", border: "none", borderRadius: 6,
                                     padding: "0.5rem 1rem", fontWeight: 600, cursor: "pointer" }}>
                        Baixar meus dados
                    </button>
                </CardContent>
            </Card>

            {alertMessage && (
                <ErrorDialog
                    open={!!alertMessage}
                    message={alertMessage}
                    onClose={() => setAlertMessage(null)}
                />
            )}
            {successMessage && (
                <SuccessDialog
                    open={!!successMessage}
                    message={successMessage}
                    onClose={() => setSuccessMessage(null)}
                />
            )}
        </div>
    );
}
