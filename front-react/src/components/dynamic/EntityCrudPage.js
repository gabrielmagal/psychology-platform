import React, { useEffect, useState } from "react";
import DynamicForm from "./DynamicForm";
import DynamicTable from "./DynamicTable";
import ConfirmDialog from "../shared/ConfirmDialog";
import styles from "./EntityCrudPage.module.css";
import { toast } from "react-toastify";

export default function EntityCrudPage({ title, resource, keycloak, realm, renderExtraAction }) {
  const [items, setItems] = useState([]);
  const [metadata, setMetadata] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [editando, setEditando] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [page, setPage] = useState(0);
  const size = 5;

  const apiUrl = process.env.REACT_APP_API_URL;

  const fetchMetadata = async () => {
    try {
      const res = await fetch(`${apiUrl}/${resource}/entity-description`, {
        headers: {
          Authorization: "Bearer " + keycloak.token,
          Tenant: realm,
        },
      });
      const data = await res.json();
      setMetadata(data);
    } catch (err) {
      toast.error("Erro ao carregar metadados.");
      console.error(err);
    }
  };

  const fetchItems = async () => {
    try {
      const res = await fetch(`${apiUrl}/${resource}?page=${page}&size=${size}`, {
        headers: {
          Authorization: "Bearer " + keycloak.token,
          Tenant: realm,
        },
      });

      if (res.status === 401) {
        toast.error("Sessão expirada.");
        keycloak.logout();
        return;
      }

      const data = await res.json();
      const content = Array.isArray(data) ? data : data.content || [];
      setItems(content);
    } catch (err) {
      toast.error("Erro ao buscar dados.");
      console.error(err);
    }
  };

  const handleSubmit = async (item) => {
    const method = item.id ? "PUT" : "POST";
    const url = item.id
      ? `${apiUrl}/${resource}/${item.id}`
      : `${apiUrl}/${resource}`;

    try {
      const response = await fetch(url, {
        method,
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer " + keycloak.token,
          Tenant: realm,
        },
        body: JSON.stringify(item),
      });

      if (!response.ok) {
        const error = await response.text();
        throw new Error(error || "Erro na requisição.");
      }

      toast.success(`${resource} ${method === "POST" ? "criado" : "atualizado"} com sucesso!`);
      fetchItems();
    } catch (err) {
      console.error(err);
      toast.error(`Erro ao ${method === "POST" ? "criar" : "atualizar"}.`);
    }
  };

  const deletar = async (id) => {
    try {
      await fetch(`${apiUrl}/${resource}/${id}`, {
        method: "DELETE",
        headers: {
          Authorization: "Bearer " + keycloak.token,
          Tenant: realm,
        },
      });
      toast.success("Removido com sucesso!");
      fetchItems();
    } catch (err) {
      console.error(err);
      toast.error("Erro ao excluir.");
    } finally {
      setConfirmDeleteId(null);
    }
  };

  useEffect(() => {
    fetchMetadata();
    fetchItems();
  }, [page]);

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>{title}</h1>
      <button
        onClick={() => {
          setEditando(null);
          setShowModal(true);
        }}
        className={styles.createBtn}
      >
        + Novo
      </button>

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
