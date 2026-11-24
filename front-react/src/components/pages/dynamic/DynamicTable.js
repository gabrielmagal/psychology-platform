import React, { useMemo } from "react"
import styles from "./DynamicTable.module.css"
import SubEntityButton from "./SubEntityButton"
import dayjs from 'dayjs'
import {getUserRoles} from "../../shared/Auth";

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
                        allowedRolesToSubEntity = []
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
    () => Object.entries(metadata.properties).filter(([_, meta]) => meta.showInTable),
    [metadata]
  );

  const renderValue = (item, key) => {
    const value = item[key]
    const meta = metadata.properties[key]

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

    if (meta.type?.toLowerCase().includes("date")) {
      const isDateTime = meta.type.toLowerCase().includes("time")
      return isDateTime
        ? dayjs(value).format("DD/MM/YYYY HH:mm")
        : dayjs(value).format("DD/MM/YYYY")
    }

    if (meta.type?.toLowerCase() === "boolean") {
      return value === true ? "Sim" : value === false ? "Não" : "";
    }

    if (meta.enum && meta.enumValues && typeof meta.enumValues === "object" && !Array.isArray(meta.enumValues)) {
      return meta.enumValues[value] || value;
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
                  (hasAnyRole(allowedRolesToEdit) || hasAnyRole(allowedRolesToDelete) || subEntities.length > 0 || renderExtraAction) && <th>Ações</th>
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
                  <td key={key}>{renderValue(item, key)}</td>
                ))}
                <td>
                  <div className={styles.actionsCell}>
                    {hasAnyRole(allowedRolesToEdit) && (
                        <button onClick={() => onEdit(item)} className={styles.editBtn}>Editar</button>
                    )}
                    {hasAnyRole(allowedRolesToDelete) && (
                        <button onClick={() => onDelete(item.id)} className={styles.deleteBtn}>Excluir</button>
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
