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
                        renderExtraAction,
                        allowedRolesToEdit = [],
                        allowedRolesToDelete = [],
                        allowedRolesToSubEntity = []
  }) => {
  const userRoles = getUserRoles(keycloak);
  const hasAnyRole = (roles) => roles.length === 0 || roles.some((r) => userRoles.includes(r));

  const subEntities = Object.entries(metadata.properties)
    .filter(([_, meta]) => meta.subEntity)
    .map(([key, meta]) => {
      const parentField = Object.entries(meta.metadata?.properties || {})
        .find(([_, value]) => value?.ManyToOne)?.[0] || key;
      return { key, label: meta.label, parentField, prop: meta }
    });

  const fields = Object.entries(metadata.properties).filter(
    ([_, meta]) => meta.showInTable
  )

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
            {data.map((item, index) => (
              <tr key={index}>
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

export default DynamicTable
