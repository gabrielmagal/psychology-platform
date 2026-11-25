import React, { useMemo } from "react"
import styles from "./DynamicTable.module.css"
import SubEntityButton from "./SubEntityButton"
import dayjs from 'dayjs'
import { FiEdit2, FiTrash2 } from "react-icons/fi"
import {getUserRoles} from "../../shared/Auth";

// Cor do selo por valor de enumeração; o que não estiver aqui sai no tom neutro de informação.
const ENUM_TONE = {
  REALIZADA: "ui-badge--success",
  FALTA: "ui-badge--danger",
  CANCELADA: "",
  AGENDADA: "ui-badge--info",
  ADMINISTRADOR: "ui-badge--warning",
  PROFISSIONAL: "ui-badge--info",
  PACIENTE: "",
};

const DynamicTable = ({
                        metadata,
                        data,
                        onEdit,
                        onDelete,
                        onManageSubEntity,
                        keycloak,
                        realm,
                        onUnauthorized,
                        onForbidden,
                        renderExtraAction,
                        allowedRolesToEdit = [],
                        allowedRolesToDelete = [],
                        allowedRolesToSubEntity = [],
                        // { campo: (valor, item) => conteúdo } — apresentação própria de uma coluna.
                        cellFormatters = {},
                        // Colunas que a descrição da entidade mostraria, mas esta tela dispensa.
                        hideColumns = []
  }) => {
  const userRoles = getUserRoles(keycloak);
  const hasAnyRole = (roles) => roles.length === 0 || roles.some((r) => userRoles.includes(r));

  // Ambas as listas dependem só da metadata (estática): recalculá-las a cada render, junto
  // com o Object.entries sobre o grafo inteiro, era trabalho repetido a cada digitação ou
  // abertura de diálogo na tela.
  const subEntities = useMemo(() => Object.entries(metadata.properties)
    .filter(([_, meta]) => meta.subEntity)
    .map(([key, meta]) => {
      // Era `value?.ManyToOne`; o backend emite a chave em camelCase (`manyToOne`),
      // então esta busca nunca encontrava nada e sempre caía no fallback.
      const parentField = Object.entries(meta.metadata?.properties || {})
        .find(([_, value]) => value?.manyToOne)?.[0] || key;
      return { key, label: meta.label, parentField, prop: meta }
    }), [metadata]);

  const fields = useMemo(
    () => Object.entries(metadata.properties)
      .filter(([key, meta]) => meta.showInTable && !hideColumns.includes(key)),
    // eslint-disable-next-line
    [metadata, hideColumns.join(",")]
  );

  const renderValue = (item, key) => {
    const value = item[key]
    const meta = metadata.properties[key]

    if (cellFormatters[key]) {
      return cellFormatters[key](value, item)
    }

    if (value && typeof value === "object") {
      const showField = meta.showField;

      if (Array.isArray(showField)) {
        return showField.map(field => value[field]).filter(Boolean).join(" ");
      }

      if (showField && value[showField] != null) {
        return value[showField];
      }

      return value.label || value.name || value.codigo || value.id || "[objeto]";
    }

    // Sem valor: um traço discreto, em vez de "Invalid Date" ou de uma célula em branco.
    if (value === null || value === undefined || value === "") {
      return <span className={styles.empty}>—</span>
    }

    // CPF e telefone chegam só com dígitos: na tela vão com a máscara de leitura.
    if (key === "cpf" && /^\d{11}$/.test(value)) {
      return `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6, 9)}-${value.slice(9)}`
    }
    if (/phone/i.test(key) && /^\d{10,11}$/.test(value)) {
      return `(${value.slice(0, 2)}) ${value.slice(2, value.length - 4)}-${value.slice(-4)}`
    }

    if (meta.type?.toLowerCase() === "localtime") {
      return String(value).slice(0, 5)
    }

    if (meta.type?.toLowerCase() === "bigdecimal") {
      return Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" })
    }

    if (meta.type?.toLowerCase().includes("date")) {
      const isDateTime = meta.type.toLowerCase().includes("time")
      return isDateTime
        ? dayjs(value).format("DD/MM/YYYY HH:mm")
        : dayjs(value).format("DD/MM/YYYY")
    }

    if (meta.type?.toLowerCase() === "boolean") {
      return <span className={`ui-badge ${value ? "ui-badge--success" : ""}`}>{value ? "Sim" : "Não"}</span>;
    }

    if (meta.enum && meta.enumValues && typeof meta.enumValues === "object" && !Array.isArray(meta.enumValues)) {
      return <span className={`ui-badge ${ENUM_TONE[value] || "ui-badge--info"}`}>{meta.enumValues[value] || value}</span>;
    }

    return value ?? ""
  }

  return (
    <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              {fields.map(([key, meta]) => (
                <th key={key}>{meta.label || key}</th>
              ))}
              {
                  (hasAnyRole(allowedRolesToEdit) || hasAnyRole(allowedRolesToDelete) || subEntities.length > 0 || renderExtraAction) && <th className={styles.actionsHeader}>Ações</th>
              }
            </tr>
          </thead>
          <tbody>
            {/* A chave era o índice da linha: ao trocar de página ou remover um registro,
                o React reaproveitava as linhas antigas e o estado dos componentes de ação
                ficava associado ao item errado. O id da entidade é estável. */}
            {data.map((item, index) => (
              <tr key={item.id ?? index}>
                {fields.map(([key]) => (
                  <td key={key} title={typeof item[key] === "string" ? item[key] : undefined}>{renderValue(item, key)}</td>
                ))}
                <td className={styles.actionsTd}>
                  <div className={styles.actionsCell}>
                    {hasAnyRole(allowedRolesToEdit) && (
                        <button onClick={() => onEdit(item)} className={`${styles.editBtn} ${styles.iconBtn}`}
                                title="Editar" aria-label="Editar"><FiEdit2 aria-hidden="true" /></button>
                    )}
                    {hasAnyRole(allowedRolesToDelete) && (
                        <button onClick={() => onDelete(item.id)} className={`${styles.deleteBtn} ${styles.iconBtn}`}
                                title="Excluir" aria-label="Excluir"><FiTrash2 aria-hidden="true" /></button>
                    )}

                    {hasAnyRole(allowedRolesToSubEntity) && subEntities.map(({ key, label, parentField, prop }) => (
                        <SubEntityButton
                            key={key}
                            label={label || `Gerenciar ${key}`}
                            parentId={item.id}
                            parentField={parentField}
                            prop={prop}
                            keycloak={keycloak}
                            realm={realm}
                            onUnauthorized={onUnauthorized}
                            onForbidden={onForbidden}
                            onCreated={() => onManageSubEntity?.(prop, item)}
                            className={styles.subEntityBtn}
                            allowedRolesToEdit={allowedRolesToEdit}
                            allowedRolesToDelete={allowedRolesToDelete}
                            allowedRolesToSubEntity={allowedRolesToSubEntity}
                        />
                    ))}

                    {Array.isArray(renderExtraAction)
                        ? renderExtraAction.map((fn, i) =>
                            typeof fn === "function" && fn(item, i)
                        )
                        : typeof renderExtraAction === "function" && renderExtraAction(item)
                    }
                  </div>
                </td>

              </tr>
            ))}
          </tbody>
        </table>
    </div>
  )
}

export default React.memo(DynamicTable)
