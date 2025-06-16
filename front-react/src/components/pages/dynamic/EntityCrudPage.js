import React, { useEffect, useState } from "react";
import DynamicForm from "./DynamicForm";
import DynamicTable from "./DynamicTable";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import styles from "./EntityCrudPage.module.css";
import { toast } from "react-toastify";
import { fetchWithAuth } from "../../../utils/fetchWithAuth";
import {getUserRoles} from "../../shared/Auth";

export default function EntityCrudPage({
                                           title,
                                           resource,
                                           keycloak,
                                           realm,
                                           onUnauthorized,
                                           onForbidden,
                                           renderExtraAction,
                                           allowedRolesToEdit = [],
                                           allowedRolesToDelete = [],
                                           allowedRolesToSubEntity = []
    }) {
    const [items, setItems] = useState([]);
    const [metadata, setMetadata] = useState(null);
    const [alertMessage, setAlertMessage] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [editando, setEditando] = useState(null);
    const [confirmDeleteId, setConfirmDeleteId] = useState(null);
    const [page, setPage] = useState(0);
    const size = 5;

    const apiUrl = process.env.REACT_APP_API_URL;

    const hasAnyRole = (roles) => {
        const userRoles = getUserRoles(keycloak);
        return roles.some(role => userRoles.includes(role));
    };

    const fetchMetadata = async () => {
        try {
            const res = await fetchWithAuth(
                `${apiUrl}/${resource}/entity-description`,
                {
                    headers: {
                        Authorization: "Bearer " + keycloak.token,
                        Tenant: realm,
                    },
                },
                {
                    onUnauthorized,
                    onForbidden
                }
            );

            if (res.ok) {
                const data = await res.json();
                setMetadata(data);
            }
            else
            {
                let errorMsg = `Erro ${res.status}`;
                try {
                    const data = await res.json();
                    if (data?.details) {
                        errorMsg = data.details;
                    } else if (data?.message) {
                        errorMsg = data.message;
                    }
                } catch (e) {
                    const fallbackText = await res.text();
                    if (fallbackText) errorMsg = fallbackText;
                }

                if (res.status === 401 && onUnauthorized) onUnauthorized();
                if (res.status === 403 && onForbidden) onForbidden();

                throw new Error(errorMsg);
            }
        } catch (err) {
            setAlertMessage(extractCleanErrorMessage(err));
        }
    };

    const fetchItems = async () => {
        try {
            const res = await fetchWithAuth(`${apiUrl}/${resource}?page=${page}&size=${size}`,
                {
                    headers: {
                        Authorization: "Bearer " + keycloak.token,
                        Tenant: realm,
                    },
                },
                {
                    onUnauthorized,
                    onForbidden
                }
            );

            if (res.status === 401) {
                toast.error("Sessão expirada.");
                keycloak.logout();
                return;
            }

            if (res.ok) {
                const data = await res.json();
                const content = Array.isArray(data) ? data : data.content || [];
                setItems(content);
            }
            else
            {
                let errorMsg = `Erro ${res.status}`;
                try {
                    const data = await res.json();
                    if (data?.details) {
                        errorMsg = data.details;
                    } else if (data?.message) {
                        errorMsg = data.message;
                    }
                } catch (e) {
                    const fallbackText = await res.text();
                    if (fallbackText) errorMsg = fallbackText;
                }

                if (res.status === 401 && onUnauthorized) onUnauthorized();
                if (res.status === 403 && onForbidden) onForbidden();

                throw new Error(errorMsg);
            }
        } catch (err) {
            setAlertMessage(extractCleanErrorMessage(err));
        }
    };

    const handleSubmit = async (item) => {
        const method = item.id ? "PUT" : "POST";
        const url = item.id
            ? `${apiUrl}/${resource}/${item.id}`
            : `${apiUrl}/${resource}`;

        try {
            const res = await fetchWithAuth(url,
            {
                    method,
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: "Bearer " + keycloak.token,
                        Tenant: realm,
                    },
                    body: JSON.stringify(item),
                },
                {
                    onUnauthorized,
                    onForbidden
                }
            );

            if (res.ok) {
                toast.success(`${resource} ${method === "POST" ? "criado" : "atualizado"} com sucesso!`);
                fetchItems();
            }
            else
            {
                let errorMsg = `Erro ${res.status}`;
                try {
                    const data = await res.json();
                    if (data?.details) {
                        errorMsg = data.details;
                    } else if (data?.message) {
                        errorMsg = data.message;
                    }
                } catch (e) {
                    const fallbackText = await res.text();
                    if (fallbackText) errorMsg = fallbackText;
                }

                if (res.status === 401 && onUnauthorized) onUnauthorized();
                if (res.status === 403 && onForbidden) onForbidden();

                throw new Error(errorMsg);
            }
        } catch (err) {
            setAlertMessage(extractCleanErrorMessage(err));
        }
    };

    const deletar = async (id) => {
        try {
            const res = await fetchWithAuth(`${apiUrl}/${resource}/${id}`, {
                method: "DELETE",
                    headers: {
                        Authorization: "Bearer " + keycloak.token,
                        Tenant: realm,
                    },
                },
                {
                    onUnauthorized,
                    onForbidden
                }
            );

            if (res.ok) {
                toast.success("Removido com sucesso!");
                fetchItems();
            }
            else
            {
                let errorMsg = `Erro ${res.status}`;
                try {
                    const data = await res.json();
                    if (data?.details) {
                        errorMsg = data.details;
                    } else if (data?.message) {
                        errorMsg = data.message;
                    }
                } catch (e) {
                    const fallbackText = await res.text();
                    if (fallbackText) errorMsg = fallbackText;
                }

                if (res.status === 401 && onUnauthorized) onUnauthorized();
                if (res.status === 403 && onForbidden) onForbidden();

                throw new Error(errorMsg);
            }
        } catch (err) {
            setAlertMessage(extractCleanErrorMessage(err));
        } finally {
            setConfirmDeleteId(null);
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
        fetchItems();
    }, [page]);

    return (
        <div className={styles.container}>
            <h1 className={styles.title}>{title}</h1>
            { hasAnyRole(allowedRolesToEdit) && (
                    <button
                        onClick={() => {
                            setEditando(null);
                            setShowModal(true);
                        }}
                        className={styles.createBtn}
                    >
                        + Novo
                    </button>
                )
            }

            {showModal && metadata && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modalContent}>
                        <h2>{editando ? "Editar" : "Novo"}</h2>
                        <DynamicForm
                            metadata={metadata}
                            initialData={editando}
                            onSubmit={(data) => {
                                handleSubmit(data);
                                setShowModal(false);
                            }}
                            onClose={() => setShowModal(false)}
                            keycloak={keycloak}
                            realm={realm}
                            onUnauthorized={onUnauthorized}
                            onForbidden={onForbidden}
                            allowedRolesToEdit={allowedRolesToEdit}
                            allowedRolesToDelete={allowedRolesToDelete}
                            isNestedForm={false}
                        />
                    </div>
                </div>
            )}

            {metadata && (
                <DynamicTable
                    metadata={metadata}
                    data={items}
                    onEdit={(item) => {
                        setEditando(item);
                        setShowModal(true);
                    }}
                    onDelete={(id) => setConfirmDeleteId(id)}
                    keycloak={keycloak}
                    realm={realm}
                    renderExtraAction={renderExtraAction}
                    allowedRolesToEdit={allowedRolesToEdit}
                    allowedRolesToDelete={allowedRolesToDelete}
                    allowedRolesToSubEntity={allowedRolesToSubEntity}
                />
            )}

            <div className={styles.pagination}>
                <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className={styles.pageBtn}
                >
                    ⬅ Anterior
                </button>
                <span className={styles.pageInfo}>Página {page + 1}</span>
                <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={items.length < size}
                    className={styles.pageBtn}
                >
                    Próxima ➡
                </button>
            </div>

            {alertMessage && (
                <ErrorDialog
                    open={!!alertMessage}
                    message={alertMessage}
                    onClose={() => setAlertMessage(null)}
                />
            )}

            {confirmDeleteId && (
                <ConfirmDialog
                    message="Deseja realmente excluir este item?"
                    onConfirm={() => deletar(confirmDeleteId)}
                    onCancel={() => setConfirmDeleteId(null)}
                />
            )}
        </div>
    );
}
