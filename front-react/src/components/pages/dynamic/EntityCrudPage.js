import React, { useCallback, useEffect, useRef, useState } from "react";
import DynamicForm from "./DynamicForm";
import DynamicTable from "./DynamicTable";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import styles from "./EntityCrudPage.module.css";
import { toast } from "react-toastify";
import { apiFetch, extractCleanErrorMessage } from "../../../utils/fetchWithAuth";
import { getUserRoles } from "../../shared/Auth";

const PAGE_SIZE = 5;

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
    const [totalElements, setTotalElements] = useState(0);
    const [metadata, setMetadata] = useState(null);
    const [alertMessage, setAlertMessage] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [editando, setEditando] = useState(null);
    const [confirmDeleteId, setConfirmDeleteId] = useState(null);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);

    // Cancela a busca anterior quando o usuário troca de página rapidamente, para que uma
    // resposta atrasada não sobrescreva a página atual.
    const inFlight = useRef(null);

    const userRoles = getUserRoles(keycloak);
    const hasAnyRole = (roles) => roles.some(role => userRoles.includes(role));

    const request = useCallback(
        (path, options) => apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options }),
        [keycloak, realm, onUnauthorized, onForbidden]
    );

    const showError = (err) => {
        const message = extractCleanErrorMessage(err);
        if (message) setAlertMessage(message);
    };

    /**
     * A descrição da entidade é derivada de anotações no backend: ela não muda entre uma
     * página e outra. Estava no mesmo efeito da listagem, com `[page]` na lista de
     * dependências, então cada clique em "Próxima" refazia essa chamada à toa.
     */
    useEffect(() => {
        let cancelled = false;

        request(`/${resource}/entity-description`)
            .then((data) => { if (!cancelled) setMetadata(data); })
            .catch(showError);

        return () => { cancelled = true; };
    }, [resource, request]);

    const fetchItems = useCallback(async () => {
        inFlight.current?.abort();
        const controller = new AbortController();
        inFlight.current = controller;

        setLoading(true);
        try {
            const data = await request(`/${resource}?page=${page}&size=${PAGE_SIZE}`, { signal: controller.signal });
            const content = Array.isArray(data) ? data : data?.content || [];
            setItems(content);
            setTotalElements(Array.isArray(data) ? content.length : data?.totalElements ?? content.length);
        } catch (err) {
            showError(err);
        } finally {
            if (inFlight.current === controller) {
                inFlight.current = null;
                setLoading(false);
            }
        }
    }, [request, resource, page]);

    useEffect(() => {
        fetchItems();
        return () => inFlight.current?.abort();
    }, [fetchItems]);

    const handleSubmit = async (item) => {
        try {
            await request(
                item.id ? `/${resource}/${item.id}` : `/${resource}`,
                { method: item.id ? "PUT" : "POST", body: item }
            );
            toast.success(`${resource} ${item.id ? "atualizado" : "criado"} com sucesso!`);
            fetchItems();
        } catch (err) {
            showError(err);
        }
    };

    const deletar = async (id) => {
        try {
            await request(`/${resource}/${id}`, { method: "DELETE" });
            toast.success("Removido com sucesso!");
            fetchItems();
        } catch (err) {
            showError(err);
        } finally {
            setConfirmDeleteId(null);
        }
    };

    // O botão "Próxima" se baseava em `items.length < size`, um palpite que trava na última
    // página cheia e não sabe quantas páginas existem. O backend já devolve `totalElements`.
    const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
    const isLastPage = page >= totalPages - 1;

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
                    onUnauthorized={onUnauthorized}
                    onForbidden={onForbidden}
                    renderExtraAction={renderExtraAction}
                    allowedRolesToEdit={allowedRolesToEdit}
                    allowedRolesToDelete={allowedRolesToDelete}
                    allowedRolesToSubEntity={allowedRolesToSubEntity}
                />
            )}

            <div className={styles.pagination}>
                <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0 || loading}
                    className={styles.pageBtn}
                >
                    ⬅ Anterior
                </button>
                <span className={styles.pageInfo}>
                    Página {page + 1} de {totalPages} ({totalElements} registros)
                </span>
                <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={isLastPage || loading}
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
