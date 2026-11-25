import React, { useCallback, useEffect, useRef, useState } from "react";
import { FiPlus, FiSearch } from "react-icons/fi";
import DynamicForm from "./DynamicForm";
import DynamicTable from "./DynamicTable";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { EmptyState, ErrorState, Skeleton } from "../../ui/Feedback";
import styles from "./EntityCrudPage.module.css";
import { toast } from "react-toastify";
import { apiFetch } from "../../../utils/fetchWithAuth";
import { getUserRoles } from "../../shared/Auth";
import { useDebouncedCallback } from "../../../utils/useDebouncedCallback";

const PAGE_SIZE = 10;

export default function EntityCrudPage({
                                           title,
                                           subtitle,
                                           // Como chamar um registro nas mensagens: "paciente", "pacote"...
                                           itemName = "registro",
                                           emptyHint,
                                           resource,
                                           keycloak,
                                           realm,
                                           onUnauthorized,
                                           onForbidden,
                                           renderExtraAction,
                                           cellFormatters,
                                           hideColumns,
                                           // "campo,desc" ou "campo,asc": ordem da listagem.
                                           defaultSort,
                                           // Chamado depois de salvar, com o registro devolvido pelo servidor.
                                           onSaved,
                                           allowedRolesToEdit = [],
                                           // Quem pode criar; por padrão, quem pode editar.
                                           allowedRolesToCreate,
                                           allowedRolesToDelete = [],
                                           allowedRolesToSubEntity = []
    }) {
    const [items, setItems] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [metadata, setMetadata] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [editando, setEditando] = useState(null);
    const [confirmDeleteId, setConfirmDeleteId] = useState(null);
    const [page, setPage] = useState(0);
    const [search, setSearch] = useState("");
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    // Cancela a busca anterior quando o usuário troca de página rapidamente, para que uma
    // resposta atrasada não sobrescreva a página atual.
    const inFlight = useRef(null);

    const userRoles = getUserRoles(keycloak);
    const hasAnyRole = (roles) => roles.some(role => userRoles.includes(role));
    const canCreate = hasAnyRole(allowedRolesToCreate || allowedRolesToEdit);

    const request = useCallback(
        (path, options) => apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options }),
        [keycloak, realm, onUnauthorized, onForbidden]
    );

    const loadMetadata = useCallback(() => {
        let cancelled = false;
        request(`/${resource}/entity-description`)
            .then((data) => { if (!cancelled) setMetadata(data); })
            .catch((err) => { if (!cancelled && err?.name !== "AbortError") setLoadError(err); });
        return () => { cancelled = true; };
    }, [resource, request]);

    // A descrição da entidade não muda entre páginas: carrega uma vez só.
    useEffect(() => loadMetadata(), [loadMetadata]);

    const fetchItems = useCallback(async () => {
        inFlight.current?.abort();
        const controller = new AbortController();
        inFlight.current = controller;

        setLoading(true);
        try {
            const filter = (query ? `&search=${encodeURIComponent(query)}` : "")
                + (defaultSort ? `&sort=${encodeURIComponent(defaultSort)}` : "");
            const data = await request(`/${resource}?page=${page}&size=${PAGE_SIZE}${filter}`, { signal: controller.signal });
            const content = Array.isArray(data) ? data : data?.content || [];
            setItems(content);
            setTotalElements(Array.isArray(data) ? content.length : data?.totalElements ?? content.length);
            setLoadError(null);
        } catch (err) {
            if (err?.name !== "AbortError") setLoadError(err);
        } finally {
            if (inFlight.current === controller) {
                inFlight.current = null;
                setLoading(false);
            }
        }
    }, [request, resource, page, query, defaultSort]);

    useEffect(() => {
        fetchItems();
        return () => inFlight.current?.abort();
    }, [fetchItems]);

    const applySearch = useDebouncedCallback((value) => {
        setPage(0);
        setQuery(value.trim());
    }, 350);

    const retry = () => {
        setLoadError(null);
        if (!metadata) loadMetadata();
        fetchItems();
    };

    /** O formulário só fecha se o servidor aceitou: em caso de erro, o que foi digitado fica. */
    const handleSubmit = async (item) => {
        setSaving(true);
        try {
            const saved = await request(
                item.id ? `/${resource}/${item.id}` : `/${resource}`,
                { method: item.id ? "PUT" : "POST", body: item }
            );
            toast.success(item.id ? "Alterações salvas." : `Novo ${itemName} cadastrado.`);
            setShowModal(false);
            onSaved?.(saved, !item.id);
            fetchItems();
        } catch (err) {
            if (err?.name !== "AbortError") setActionError(err);
        } finally {
            setSaving(false);
        }
    };

    const deletar = async (id) => {
        try {
            await request(`/${resource}/${id}`, { method: "DELETE" });
            toast.success("Registro excluído.");
            // Se era o último da página, volta uma: senão a tela ficaria vazia.
            if (items.length === 1 && page > 0) setPage((p) => p - 1);
            else fetchItems();
        } catch (err) {
            if (err?.name !== "AbortError") setActionError(err);
        } finally {
            setConfirmDeleteId(null);
        }
    };

    const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
    const isLastPage = page >= totalPages - 1;
    const first = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
    const last = Math.min(totalElements, page * PAGE_SIZE + items.length);
    const firstLoad = loading && items.length === 0 && !loadError;

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>{title}</h1>
                    {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
                </div>
                <div className="ui-toolbar">
                    <div style={{ position: "relative" }}>
                        <FiSearch aria-hidden="true"
                                  style={{ position: "absolute", left: 11, top: 12, color: "var(--ink-400)" }} />
                        <input
                            className="ui-input"
                            style={{ paddingLeft: 34, width: 240 }}
                            type="search"
                            placeholder="Buscar…"
                            aria-label={`Buscar ${itemName}`}
                            value={search}
                            onChange={(e) => { setSearch(e.target.value); applySearch(e.target.value); }}
                        />
                    </div>
                    {canCreate && (
                        <button
                            onClick={() => { setEditando(null); setShowModal(true); }}
                            className={styles.createBtn}
                            disabled={!metadata}
                        >
                            <FiPlus aria-hidden="true" /> Novo {itemName}
                        </button>
                    )}
                </div>
            </div>

            {loadError && <ErrorState error={loadError} onRetry={retry} />}

            {firstLoad && <div className="ui-card"><Skeleton rows={6} /></div>}

            {!firstLoad && !loadError && metadata && items.length === 0 && (
                <div className="ui-card">
                    <EmptyState
                        title={query ? `Nada encontrado para "${query}"` : `Nenhum ${itemName} por aqui ainda`}
                        action={!query && canCreate && (
                            <button className="ui-btn ui-btn--primary"
                                    onClick={() => { setEditando(null); setShowModal(true); }}>
                                <FiPlus aria-hidden="true" /> Novo {itemName}
                            </button>
                        )}
                    >
                        {query
                            ? "Confira a grafia ou tente outro termo."
                            : emptyHint || (canCreate ? "Comece cadastrando o primeiro." : "Quando houver registros, eles aparecem aqui.")}
                    </EmptyState>
                </div>
            )}

            {metadata && items.length > 0 && (
                <>
                    <DynamicTable
                        metadata={metadata}
                        data={items}
                        onEdit={(item) => { setEditando(item); setShowModal(true); }}
                        onDelete={(id) => setConfirmDeleteId(id)}
                        keycloak={keycloak}
                        realm={realm}
                        onUnauthorized={onUnauthorized}
                        onForbidden={onForbidden}
                        renderExtraAction={renderExtraAction}
                        cellFormatters={cellFormatters}
                        hideColumns={hideColumns}
                        allowedRolesToEdit={allowedRolesToEdit}
                        allowedRolesToDelete={allowedRolesToDelete}
                        allowedRolesToSubEntity={allowedRolesToSubEntity}
                    />

                    <div className={styles.pagination}>
                        <span className={styles.pageInfo}>
                            Mostrando {first}–{last} de {totalElements}
                        </span>
                        <div className={styles.pageButtons}>
                            <button
                                onClick={() => setPage((p) => Math.max(0, p - 1))}
                                disabled={page === 0 || loading}
                                className={styles.pageBtn}
                            >
                                Anterior
                            </button>
                            <span className={styles.pageInfo} style={{ alignSelf: "center" }}>
                                Página {page + 1} de {totalPages}
                            </span>
                            <button
                                onClick={() => setPage((p) => p + 1)}
                                disabled={isLastPage || loading}
                                className={styles.pageBtn}
                            >
                                Próxima
                            </button>
                        </div>
                    </div>
                </>
            )}

            {showModal && metadata && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modalContent} role="dialog" aria-modal="true" aria-busy={saving}>
                        <h2>{editando ? `Editar ${itemName}` : `Novo ${itemName}`}</h2>
                        <DynamicForm
                            metadata={metadata}
                            initialData={editando}
                            onSubmit={handleSubmit}
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

            <ErrorDialog open={!!actionError} message={actionError} onClose={() => setActionError(null)} />

            {confirmDeleteId && (
                <ConfirmDialog
                    title={`Excluir este ${itemName}?`}
                    message="O registro será removido definitivamente, junto com o que depende dele."
                    onConfirm={() => deletar(confirmDeleteId)}
                    onCancel={() => setConfirmDeleteId(null)}
                />
            )}
        </div>
    );
}
