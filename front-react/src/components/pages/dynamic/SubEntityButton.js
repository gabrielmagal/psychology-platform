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
import {getUserRoles} from "../../shared/Auth";

const SubEntityButton = ({
  label,
  parentId,
  prop,
  keycloak,
  realm,
  onCreated,
  className,
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

  const apiUrl = process.env.REACT_APP_API_URL;
  const pageSize = 10;

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

  const loadSubEntities = async (page = 1, search = "") => {
    try {
      setLoading(true);
      const res = await fetch(
        `${apiUrl}${prop.path || `/${prop.relatedType}`}?${parentField}=${parentId}&page=${page - 1}&size=${pageSize}&search=${encodeURIComponent(search)}`,
        {
          headers: {
            Authorization: keycloak?.token ? `Bearer ${keycloak.token}` : undefined,
            Tenant: realm,
          },
        }
      );

      if (res.ok) {
        const json = await res.json();
        const content = json.content || [];
        setSubData(content);
        setTotalPages(json.totalPages || Math.ceil((json.totalElements || content.length) / pageSize));
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
      setLoading(false);
    }
  };

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
      const res = await fetch(
        `${apiUrl}${prop.path || `/${prop.relatedType}`}${isEdit ? `/${editingItem.id}` : ""}`,
        {
          method: isEdit ? "PUT" : "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: keycloak?.token ? `Bearer ${keycloak.token}` : undefined,
            Tenant: realm,
          },
          body: JSON.stringify(payload),
        }
      );

      if (res.ok) {
        setShowForm(false);
        setEditingItem(null);
        await loadSubEntities(page, search);
        if (onCreated) onCreated();
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

  const handleEdit = (item) => {
    setEditingItem(item);
    setShowForm(true);
  };

  const confirmDelete = (id) => {
    setConfirmingItemId(id);
  };

  const handleDeleteConfirmed = async () => {
    try {
      const res = await fetch(
          `${apiUrl}${prop.path || `/${prop.relatedType}`}/${confirmingItemId}`,
          {
            method: "DELETE",
            headers: {
              Authorization: keycloak?.token ? `Bearer ${keycloak.token}` : undefined,
              Tenant: realm,
            },
          }
      );

      if (!res.ok) {
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

      await loadSubEntities(page, search);
    } catch (err) {
      setAlertMessage(extractCleanErrorMessage(err));
    }
    setConfirmingItemId(null);
  };

  const extractCleanErrorMessage = (err) => {
    if (typeof err?.message === "string") {
      const match = err.message.match(/: (.+)$/);
      return match ? match[1] : err.message.replace(/Error id .*?, /, "");
    }
    return "Erro inesperado.";
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
                    loadSubEntities(1, e.target.value);
                    setPage(1);
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
