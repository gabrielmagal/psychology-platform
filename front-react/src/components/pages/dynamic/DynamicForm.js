import React, { useState, useEffect } from "react";
import {
  Autocomplete,
  Box,
  TextField,
  MenuItem,
  InputAdornment,
  Dialog,
  DialogTitle,
  DialogContent,
  Button,
} from "@mui/material";

import {
  DatePicker,
  DateTimePicker,
  LocalizationProvider
} from "@mui/x-date-pickers";

import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import "dayjs/locale/pt-br";
import styles from "./DynamicForm.module.css";
import { fetchWithAuth } from "../../../utils/fetchWithAuth";

export default function DynamicForm({
                                      metadata,
                                      initialData = {},
                                      onSubmit,
                                      onClose,
                                      keycloak,
                                      realm,
                                      onUnauthorized,
                                      onForbidden,
                                      isNestedForm = false,
                                      allowedRolesToEdit = [],
                                      allowedRolesToDelete = []
  }) {
  const [form, setForm] = useState({});
  const [autocompleteOptions, setAutocompleteOptions] = useState({});
  const [openOneToOneModals, setOpenOneToOneModals] = useState({});
  const getUserRoles = () => keycloak?.tokenParsed?.realm_access?.roles || [];
  const apiUrl = process.env.REACT_APP_API_URL;

  useEffect(() => {
    setForm(initialData || {});
  }, [initialData]);

  useEffect(() => {
    if (!keycloak?.token || !realm || !metadata?.properties) return;

    Object.entries(metadata.properties).forEach(([key, meta]) => {
      if (meta.manyToOne && meta.relatedType) {
        fetchOptions(meta.relatedType, key);
      }

      if (meta.oneToOne && meta.relatedType && initialData?.[key]?.id) {
        fetchOptions(meta.relatedType, key);
      }
    });
  }, [metadata, keycloak, realm]);

  const hasAnyRole = (roles) => {
    const userRoles = getUserRoles(keycloak);
    return roles.some(role => userRoles.includes(role));
  };

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleAutocompleteChange = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value || null }));
  };

  const handleFileChange = async (key, file) => {
    const base64 = await toBase64(file);
    const byteArray = base64ToByteArray(base64);
    setForm((prev) => ({
      ...prev,
      [key]: byteArray,
      [`${key}Name`]: file.name,
      [`${key}Type`]: file.type
    }));
  };

  const fetchOptions = async (relatedType, key, search = "") => {
    try {
      const res = await fetchWithAuth(`${apiUrl}/${relatedType}?page=0&size=10&search=${encodeURIComponent(search)}`,
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
      const data = await res.json();
      const newOptions = data.content || data || [];
      setAutocompleteOptions((prev) => ({
        ...prev,
        [key]: newOptions,
      }));
    } catch (err) {
      console.error("Erro ao carregar opções para", key, err);
    }
  };

  const serializeForm = (form) => {
    const result = {};
    for (const [key, val] of Object.entries(form)) {
      if (val && typeof val === 'object' && 'id' in val && Object.keys(val).length === 1) {
        result[key] = val;
      } else if (val && typeof val === 'object' && 'id' in val) {
        result[key] = { id: val.id };
      } else {
        result[key] = val;
      }
    }
    return result;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    e.stopPropagation();
    onSubmit(serializeForm(form));
  };

  const handleDateChange = (key, value) => {
    setForm((prev) => ({
      ...prev,
      [key]: value ? value.format("YYYY-MM-DD") : null,
    }));
  };

  const handleDateTimeChange = (key, value) => {
    setForm((prev) => ({
      ...prev,
      [key]: value ? value.format("YYYY-MM-DDTHH:mm:ss") : null,
    }));
  };

  const toBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = (error) => reject(error);
  });

  const base64ToByteArray = (base64) => Array.from(atob(base64)).map(c => c.charCodeAt(0));

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="pt-br">
      <form onSubmit={handleSubmit} className={styles.form}>
        {Object.entries(metadata.properties).map(([key, meta]) => {
          if (key === "id" || meta.showInForm !== true) return null;

          const inputType = meta.type?.toLowerCase().includes("date") ? "date" : "text";
          const value = form[key] ?? "";

          if (meta.type?.toLowerCase().includes("date")) {
              const isDateTime = meta.type?.toLowerCase().includes("time");
              return (
                <div className={styles.formGroup} key={key}>
                  {isDateTime ? (
                    <DateTimePicker
                      label={meta.label || key}
                      value={value ? dayjs(value) : null}
                      onChange={(newValue) => handleDateTimeChange(key, newValue)}
                      slotProps={{ textField: { fullWidth: true, required: meta.required } }}
                    />
                  ) : (
                    <DatePicker
                      label={meta.label || key}
                      value={value ? dayjs(value) : null}
                      onChange={(newValue) => handleDateChange(key, newValue)}
                      slotProps={{ textField: { fullWidth: true, required: meta.required } }}
                    />
                  )}
                </div>
              );
            }

          if (meta.type === ("boolean" || "Boolean")) {
            return (
              <div className={styles.formGroup} key={key}>
                <TextField
                  select
                  label={meta.label || key}
                  name={key}
                  value={form[key] === true ? "true" : form[key] === false ? "false" : ""}
                  onChange={(e) => {
                    const val = e.target.value === "true";
                    setForm({ ...form, [key]: val });
                  }}
                  required={meta.required}
                  fullWidth
                  variant="outlined"
                >
                  <MenuItem value="">Selecione...</MenuItem>
                  <MenuItem value="true">Sim</MenuItem>
                  <MenuItem value="false">Não</MenuItem>
                </TextField>
              </div>
            );
          }

          if (meta.type === "byte[]") {
            return (
              <div className={styles.formGroup} key={key}>
                <label>{meta.label || key}</label>
                <input
                  type="file"
                  onChange={e => {
                    const file = e.target.files?.[0];
                    if (file) handleFileChange(key, file);
                  }}
                  style={{ marginTop: "8px" }}
                />
              </div>
            );
          }

          if (meta.file === true && meta.base64 === true) {
            const handleDownload = () => {
              const base64 = form[key];
              const fileName = form[`${key}Name`] || 'arquivo';
              const fileType = form[`${key}Type`] || 'application/octet-stream';
              if (!base64) return;
              const blob = new Blob([Uint8Array.from(atob(base64), c => c.charCodeAt(0))], { type: fileType });
              const url = window.URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = fileName;
              a.style.display = 'none';
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
              window.URL.revokeObjectURL(url);
            };

            return (
                <Box key={key} className={styles.fileInputWrapper}>
                  <label className={styles.fileInputLabel}>{meta.label || key}</label>
                  <Box display="flex" gap={1} alignItems="center">
                    <Button
                        component="label"
                        variant="outlined"
                        size="small"
                        sx={{
                          minWidth: 0,
                          fontWeight: 500,
                          padding: '2px 12px',
                          borderRadius: '4px',
                          textTransform: 'none'
                        }}
                    >
                      Selecionar Arquivo
                      <input
                          type="file"
                          hidden
                          onChange={async (e) => {
                            const file = e.target.files?.[0];
                            if (!file) return;
                            const reader = new FileReader();
                            reader.readAsDataURL(file);
                            reader.onload = () => {
                              const base64 = reader.result?.toString().split(',')[1] || '';
                              setForm((prev) => ({
                                ...prev,
                                [key]: base64,
                                [`${key}Name`]: file.name,
                                [`${key}Type`]: file.type,
                              }));
                            };
                          }}
                      />
                    </Button>

                    {/* Botão Baixar Arquivo */}
                    {form[`${key}Name`] && form[key] && (
                        <Button
                            variant="contained"
                            size="small"
                            sx={{
                              backgroundColor: '#28a745',
                              color: 'white',
                              fontWeight: 500,
                              padding: '2px 12px',
                              borderRadius: '4px',
                              textTransform: 'none',
                              minWidth: 0,
                              '&:hover': {
                                backgroundColor: '#218838',
                              },
                            }}
                            onClick={handleDownload}
                        >
                          Baixar arquivo
                        </Button>
                    )}

                    {/* Nome do arquivo */}
                    {form[`${key}Name`] && (
                      <span className={styles.fileInfo}>
                        Arquivo atual: <strong>{form[`${key}Name`]}</strong>
                      </span>
                    )}
                  </Box>
                </Box>
            );
          }

          if (meta.photo === true && meta.base64 === true) {
            return (
                <div key={key} className={styles.formGroup}>
                  <label className={styles.label}>{meta.label || key}</label>

                  {form[key] && (
                      <img
                          src={`data:${form[`${key}Type`] || "image/jpeg"};base64,${form[key]}`}
                          alt="Imagem de perfil"
                          style={{
                            width: 120,
                            height: 120,
                            borderRadius: "50%",
                            objectFit: "cover",
                            marginBottom: 12,
                            border: "1px solid #ccc"
                          }}
                      />
                  )}

                  <Button
                      component="label"
                      variant="outlined"
                      sx={{
                        textTransform: "none",
                        fontWeight: 500,
                        padding: "6px 16px",
                      }}
                  >
                    Selecionar Imagem
                    <input
                        type="file"
                        accept="image/*"
                        hidden
                        onChange={async (e) => {
                          const file = e.target.files?.[0];
                          if (!file) return;
                          const reader = new FileReader();
                          reader.readAsDataURL(file);
                          reader.onload = () => {
                            const base64 = reader.result?.toString().split(',')[1] || '';
                            setForm((prev) => ({ ...prev, [key]: base64 }));
                          };
                        }}
                    />
                  </Button>
                </div>
            );
          }

          if (meta.enum) {
            const enumValues = meta.enumValues;
            const isObject = typeof enumValues === "object" && !Array.isArray(enumValues);

            return (
              <div className={styles.formGroup} key={key}>
                <TextField
                  select
                  label={meta.label || key}
                  variant="outlined"
                  fullWidth
                  name={key}
                  value={value}
                  onChange={handleChange}
                  required={meta.required}
                >
                  <MenuItem value="">Selecione...</MenuItem>
                  {isObject
                    ? Object.entries(enumValues).map(([enumKey, label]) => (
                        <MenuItem key={enumKey} value={enumKey}>
                          {label}
                        </MenuItem>
                      ))
                    : enumValues.map((val) => (
                        <MenuItem key={val} value={val}>
                          {val}
                        </MenuItem>
                      ))}
                </TextField>
              </div>
            );
          }

          if (meta.manyToOne && meta.relatedType) {
            return (
                <div className={styles.formGroup} key={key}>
                  <div className={styles.inlineAutocomplete}>
                    <div style={{ flexGrow: 1 }}>
                      <Autocomplete
                          options={autocompleteOptions[key] || []}
                          getOptionLabel={(option) => {
                            if (!option) return "";
                            const showField = meta.showField || metadata.properties[key]?.showField || "id";

                            if (Array.isArray(showField)) {
                              return showField.map(f => option[f]).filter(Boolean).join(" ");
                            }

                            return option[showField] ?? option.nome ?? option.label ?? String(option.id ?? "");
                          }}
                          value={form[key] || null}
                          onChange={(_, newValue) => handleAutocompleteChange(key, newValue)}
                          onInputChange={(_, inputValue) => {
                            if (inputValue.length >= 2) {
                              fetchOptions(meta.relatedType, key, inputValue);
                            }
                          }}
                          isOptionEqualToValue={(opt, val) => opt?.id === val?.id}
                          fullWidth
                          renderInput={(params) => (
                              <TextField
                                  {...params}
                                  label={meta.label || key}
                                  variant="outlined"
                                  required={meta.required}
                                  fullWidth
                                  InputLabelProps={{ shrink: true }}
                                  InputProps={{
                                    ...params.InputProps,
                                    endAdornment: (
                                        <InputAdornment position="end">
                                          {params.InputProps.endAdornment}
                                        </InputAdornment>
                                    )
                                  }}
                              />
                          )}
                      />
                    </div>

                    {meta.metadata && (
                        <>
                          <Button
                              type="button"
                              variant="outlined"
                              style={{ height: "56px" }}
                              onClick={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: true }))}
                          >
                            Novo
                          </Button>

                          <Dialog open={!!openOneToOneModals[key]} onClose={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: false }))} maxWidth="sm" fullWidth>
                            <DialogTitle>Novo {meta.label || key}</DialogTitle>
                            <DialogContent style={{ paddingTop: 16 }}>
                              <DynamicForm
                                  metadata={meta.metadata}
                                  initialData={{}}
                                  onSubmit={async (val) => {
                                    try {
                                      const res = await fetchWithAuth(`${apiUrl}/${meta.relatedType}`,
                                          {
                                            method: "POST",
                                            headers: {
                                              Authorization: "Bearer " + keycloak.token,
                                              Tenant: realm,
                                              "Content-Type": "application/json",
                                            },
                                            body: JSON.stringify(val),
                                          },
                                          {
                                            onUnauthorized: () => {},
                                            onForbidden: () => {},
                                          }
                                      );

                                      if (!res.ok) throw new Error("Erro ao criar entidade relacionada");

                                      const created = await res.json();
                                      setForm(prev => ({ ...prev, [key]: created }));
                                      setAutocompleteOptions(prev => ({
                                        ...prev,
                                        [key]: [...(prev[key] || []), created],
                                      }));
                                      setOpenOneToOneModals(prev => ({ ...prev, [key]: false }));
                                    } catch (err) {
                                      console.error("Falha ao criar entidade relacionada:", err);
                                    }
                                  }}
                                  onClose={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: false }))}
                                  keycloak={keycloak}
                                  realm={realm}
                                  isNestedForm={true}
                                  allowedRolesToEdit={allowedRolesToEdit}
                                  allowedRolesToDelete={allowedRolesToDelete}
                              />
                            </DialogContent>
                          </Dialog>
                        </>
                    )}
                  </div>
                </div>
            );
          }

          if (meta.oneToOne && meta.relatedType && meta.metadata) {
            return (
              <div className={styles.formGroup} key={key}>
                <label>{meta.label || key}</label>
                <Button
                  type="button"
                  variant="outlined"
                  style={{ marginTop: "8px", width: "100%" }}
                  onClick={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: true }))}
                >
                  {form[key] ? "Editar" : "Adicionar"} {meta.label || key}
                </Button>

                <Dialog open={!!openOneToOneModals[key]} onClose={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: false }))} maxWidth="sm" fullWidth>
                  <DialogTitle>{form[key] ? "Editar" : "Adicionar"} {meta.label || key}</DialogTitle>
                  <DialogContent style={{ paddingTop: 16 }}>
                    <DynamicForm
                      metadata={meta.metadata}
                      initialData={form[key] || {}}
                      onSubmit={async (val) => {
                        try {
                          const res = await fetchWithAuth(`${apiUrl}/${meta.relatedType}`,
                            {
                              method: "POST",
                              headers: {
                                Authorization: "Bearer " + keycloak.token,
                                Tenant: realm,
                                "Content-Type": "application/json",
                              },
                              body: JSON.stringify(val),
                            },
                            {
                              onUnauthorized,
                              onForbidden
                            }
                          );

                          if (!res.ok) throw new Error("Erro ao criar entidade relacionada");

                          const created = await res.json();
                          setForm(prev => ({ ...prev, [key]: created }));
                          setOpenOneToOneModals(prev => ({ ...prev, [key]: false }));
                        } catch (err) {
                          console.error("Falha ao criar entidade relacionada:", err);
                        }
                      }}
                      onClose={() => setOpenOneToOneModals(prev => ({ ...prev, [key]: false }))}
                      keycloak={keycloak}
                      realm={realm}
                      isNestedForm={true}
                      allowedRolesToEdit={allowedRolesToEdit}
                      allowedRolesToDelete={allowedRolesToDelete}
                    />
                  </DialogContent>
                </Dialog>
              </div>
            );
          }

          if (meta.subEntity && meta.path && form.id) {
            return (
              <div className={styles.formGroup} key={key}>
                <label className={styles.label}>{meta.label || key}</label>
                <Button
                  variant="outlined"
                  color="primary"
                  fullWidth
                  onClick={() => {
                    const resolvedPath = meta.path.replace("{id}", form.id);
                    window.location.href = `${apiUrl}${resolvedPath}`;
                  }}
                >
                  Gerenciar {meta.label || key}
                </Button>
              </div>
            );
          }

          return (
            <div className={styles.formGroup} key={key}>
              <TextField
                id={key}
                name={key}
                label={meta.label || key}
                type={inputType}
                value={value}
                onChange={handleChange}
                required={meta.required || false}
                inputProps={{ maxLength: meta.maxLength || undefined }}
                variant="outlined"
                fullWidth
                InputLabelProps={{ shrink: true }}
              />
            </div>
          );
        })}

        {!isNestedForm && hasAnyRole(allowedRolesToEdit) && (
          <div className={styles.formActions}>
            <Button
              type="submit"
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
            >
              Salvar
            </Button>
            {onClose && (
              <Button
                onClick={onClose}
                variant="outlined"
                sx={{
                  backgroundColor: '#dc3545',
                  color: 'white',
                  fontWeight: 'bold',
                  padding: '0.5rem 1.2rem',
                  borderRadius: '5px',
                  border: 'none',
                  '&:hover': {
                    backgroundColor: '#c82333',
                  },
                }}
              >
                Cancelar
              </Button>
            )}
          </div>
        )}

        {isNestedForm && hasAnyRole(allowedRolesToDelete) && (
          <div style={{ marginTop: "1rem", display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
            <Button
              type="submit"
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
            >
              Salvar
            </Button>
            {onClose && (
              <Button
                onClick={onClose}
                variant="outlined"
                sx={{
                  backgroundColor: '#dc3545',
                  color: 'white',
                  fontWeight: 'bold',
                  padding: '0.5rem 1.2rem',
                  borderRadius: '5px',
                  border: 'none',
                  '&:hover': {
                    backgroundColor: '#c82333',
                  },
                }}
              >
                Cancelar
              </Button>
            )}
          </div>
        )}
      </form>
    </LocalizationProvider>
  );
}
