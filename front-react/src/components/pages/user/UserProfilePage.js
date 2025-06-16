import React, { useEffect, useState } from "react";
import DynamicForm from "../dynamic/DynamicForm";
import SuccessDialog from "../../shared/dialog/SuccessDialog";
import { fetchWithAuth } from "../../../utils/fetchWithAuth";
import {Roles} from "../../shared/Roles";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { Card, CardContent, Avatar, Typography, Divider } from "@mui/material";
import PersonIcon from '@mui/icons-material/Person';

export default function UserProfilePage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const [metadata, setMetadata] = useState(null);
    const [userData, setUserData] = useState(null);
    const apiUrl = process.env.REACT_APP_API_URL;

    const [successMessage, setSuccessMessage] = useState(null);
    const [alertMessage, setAlertMessage] = useState(null);

    const fetchMetadata = async () => {
        try {
            const res = await fetchWithAuth(`${apiUrl}/user/entity-description`, {
                headers: {
                    Authorization: "Bearer " + keycloak.token,
                    Tenant: realm,
                },
            });
            const data = await res.json();
            setMetadata(data);
        } catch (err) {
            console.error("Erro ao carregar metadados do usuário:", err);
        }
    };

    const fetchUserData = async () => {
        try {
            const res = await fetchWithAuth(`${apiUrl}/user/keycloak`, {
                headers: {
                    Authorization: "Bearer " + keycloak.token,
                    Tenant: realm,
                },
            });
            const data = await res.json();
            setUserData(data);
        } catch (err) {
            console.error("Erro ao carregar dados do usuário:", err);
        }
    };

    const handleSubmit = async (data) => {
        try {
            const res = await fetchWithAuth(`${apiUrl}/user/${userData.id}`, {
                method: "PUT",
                headers: {
                    Authorization: "Bearer " + keycloak.token,
                    Tenant: realm,
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(data),
            });
            if (!res.ok) throw new Error("Erro ao atualizar o perfil");
            setSuccessMessage("Perfil atualizado com sucesso!");
        } catch (err) {
            const message = extractCleanErrorMessage(err);
            setAlertMessage(message);
        }
    };

    const extractCleanErrorMessage = (err) => {
        if (typeof err?.message === "string") {
            const match = err.message.match(/: (.+)$/);
            return match ? match[1] : err.message;
        }
        return "Erro inesperado.";
    };

    useEffect(() => {
        fetchMetadata();
        fetchUserData();
    }, []);

    if (!metadata || !userData) return <p>Carregando perfil...</p>;

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
