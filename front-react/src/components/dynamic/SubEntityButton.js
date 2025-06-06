import { useState } from "react";
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
import ConfirmDialog from "../shared/ConfirmDialog";

const SubEntityButton = ({
  label,
  parentId,
  prop,
  keycloak,
  realm,
  onCreated,
  className,
}) => {
  const [open, setOpen] = useState(false);
  const [subData, setSubData] = useState([]);
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
      const json = await res.json();
      const content = json.content || [];
      setSubData(content);
      setTotalPages(json.totalPages || Math.ceil((json.totalElements || content.length) / pageSize));
    } catch (err) {
      console.error("Erro ao buscar subentidades:", err);
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

      if (!res.ok) {
        const error = await res.text();
        throw new Error(error || "Erro ao salvar subentidade.");
      }

      setShowForm(false);
      setEditingItem(null);
      await loadSubEntities(page, search);
      if (onCreated) onCreated();
    } catch (err) {
      console.error(err);
      alert("Erro ao salvar subentidade.");
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

      if (!res.ok) throw new Error("Erro ao excluir subentidade");

      await loadSubEntities(page, search);
      setConfirmingItemId(null);
    } catch (err) {
      console.error(err);
      alert("Erro ao excluir subentidade.");
    }
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
