import React, { useState } from "react";
import { createPortal } from "react-dom";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  Button,
  useMediaQuery,
  useTheme,
  IconButton,
  TextField,
  Pagination,
  CircularProgress
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import DynamicForm from "./DynamicForm";
import DynamicTable from "./DynamicTable";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { getUserRoles } from "../../shared/Auth";
import { apiFetch, extractCleanErrorMessage } from "../../../utils/fetchWithAuth";
import { useDebouncedCallback } from "../../../utils/useDebouncedCallback";

const PAGE_SIZE = 10;

const SubEntityButton = ({
  label,
  parentId,
  prop,
  keycloak,
  realm,
  onCreated,
  className,
  // Antes o componente usava onUnauthorized/onForbidden nos blocos de erro SEM recebê-los
  // como props: qualquer resposta 4xx/5xx estourava ReferenceError e a mensagem real do
  // backend virava um "Erro inesperado".
  onUnauthorized,
  onForbidden,
  allowedRolesToEdit = [],
  allowedRolesToDelete = [],
  allowedRolesToSubEntity = []
}) => {
  const [open, setOpen] = useState(false);
  const [subData, setSubData] = useState([]);
  const [alertMessage, setAlertMessage] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [loading, setLoading] = useState(false);
  const [confirmingItemId, setConfirmingItemId] = useState(null);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down("md"));

  const hasAnyRole = (roles) => {
    const userRoles = getUserRoles(keycloak);
    return roles.some(role => userRoles.includes(role));
  };

  const parentField =
    prop.parentField ||
    Object.entries(prop.metadata?.properties || {}).find(
      ([_, meta]) => meta?.manyToOne
    )?.[0];

  if (!parentField) {
    console.warn("Não foi possível determinar o campo pai (ManyToOne/OneToOne) para SubEntity.");
  }

  const basePath = prop.path || `/${prop.relatedType}`;

  const request = (path, options) =>
    apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options });

  const showError = (err) => {
    const message = extractCleanErrorMessage(err);
    if (message) setAlertMessage(message);
  };

  const loadSubEntities = async (page = 1, search = "") => {
    try {
      setLoading(true);
      const json = await request(
        `${basePath}?${parentField}=${parentId}&page=${page - 1}&size=${PAGE_SIZE}&search=${encodeURIComponent(search)}`
      );
      const content = json?.content || [];
      setSubData(content);
      setTotalPages(Math.max(1, Math.ceil((json?.totalElements ?? content.length) / PAGE_SIZE)));
    } catch (err) {
      showError(err);
    } finally {
      setLoading(false);
    }
  };

  // Buscar a cada tecla gerava uma requisição por caractere digitado; espera a pausa.
  const debouncedSearch = useDebouncedCallback((value) => loadSubEntities(1, value), 350);

  const handleOpen = async () => {
    await loadSubEntities();
    setOpen(true);
  };

  const handleCreateOrUpdate = async (data) => {
    const isEdit = !!editingItem;
    const payload = {
      ...data,
      [parentField]: { id: parentId },
    };

    try {
      await request(`${basePath}${isEdit ? `/${editingItem.id}` : ""}`, {
        method: isEdit ? "PUT" : "POST",
        body: payload,
      });
      setShowForm(false);
      setEditingItem(null);
      await loadSubEntities(page, search);
      if (onCreated) onCreated();
    } catch (err) {
      showError(err);
    }
  };

  const handleEdit = (item) => {
    setEditingItem(item);
    setShowForm(true);
  };

  const confirmDelete = (id) => {
    setConfirmingItemId(id);
  };

  const handleDeleteConfirmed = async () => {
    try {
      await request(`${basePath}/${confirmingItemId}`, { method: "DELETE" });
      await loadSubEntities(page, search);
    } catch (err) {
      showError(err);
    }
    setConfirmingItemId(null);
  };

  return (
    <>
      <button onClick={handleOpen} className={className}>
        Gerenciar {label}
      </button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        fullWidth
        maxWidth="xl"
        fullScreen={fullScreen}
        scroll="paper"
        PaperProps={{
          style: {
            minHeight: "80vh",
            borderRadius: fullScreen ? 0 : "16px",
          }
        }}
      >
        <DialogTitle>
          {label}
          <IconButton
            aria-label="close"
            onClick={() => setOpen(false)}
            sx={{
              position: "absolute",
              right: 8,
              top: 8,
              color: (theme) => theme.palette.grey[500],
            }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers style={{ maxHeight: "80vh", overflowY: "auto", position: "relative" }}>
          {showForm ? (
            <DynamicForm
              metadata={prop.metadata}
              initialData={{ [parentField]: { id: parentId }, ...editingItem }}
              disabledFields={[parentField]}
              onSubmit={handleCreateOrUpdate}
              keycloak={keycloak}
              realm={realm}
              isNestedForm={true}
              onClose={() => {
                setShowForm(false);
                setEditingItem(null);
              }}
              allowedRolesToEdit={allowedRolesToEdit}
              allowedRolesToDelete={allowedRolesToDelete}
            />
          ) : (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "1rem" }}>
                <TextField
                  label="Buscar"
                  variant="outlined"
                  size="small"
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(1);
                    debouncedSearch(e.target.value);
                  }}
                  style={{ marginRight: "auto", width: "300px" }}
                />
                { hasAnyRole(allowedRolesToEdit) && (
                    <Button
                        variant="contained"
                        sx={{
                          backgroundColor: '#28a745',
                          color: 'white',
                          fontWeight: 'bold',
                          padding: '0.5rem 1.2rem',
                          borderRadius: '5px',
                          '&:hover': {
                            backgroundColor: '#218838',
                          },
                        }}
                        onClick={() => setShowForm(true)}
                    >
                      Adicionar {label}
                    </Button>
                  )
                }
              </div>
              {loading ? (
                <CircularProgress />
              ) : (
                <>
                  <DynamicTable
                    metadata={prop.metadata}
                    data={subData}
                    keycloak={keycloak}
                    realm={realm}
                    onEdit={handleEdit}
                    onDelete={confirmDelete}
                    onManageSubEntity={() => {}}
                    allowedRolesToEdit={allowedRolesToEdit}
                    allowedRolesToDelete={allowedRolesToDelete}
                    allowedRolesToSubEntity={allowedRolesToSubEntity}
                  />
                  <div style={{ display: "flex", justifyContent: "center", marginTop: "1rem" }}>
                    <Pagination
                      count={totalPages}
                      page={page}
                      onChange={(_, newPage) => {
                        setPage(newPage);
                        loadSubEntities(newPage, search);
                      }}
                      color="primary"
                    />
                  </div>
                </>
              )}
            </>
          )}
        </DialogContent>
      </Dialog>

      {alertMessage && (
          <ErrorDialog
              open={!!alertMessage}
              message={alertMessage}
              onClose={() => setAlertMessage(null)}
          />
      )}

      {confirmingItemId && createPortal(
        <ConfirmDialog
          message="Tem certeza que deseja excluir este item?"
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setConfirmingItemId(null)}
        />,
        document.body
      )}
    </>
  );
};

export default SubEntityButton;
