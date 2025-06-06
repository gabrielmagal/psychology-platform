import styles from "./DynamicTable.module.css"
import SubEntityButton from "./SubEntityButton"
import dayjs from 'dayjs'

const DynamicTable = ({ metadata, data, onEdit, onDelete, onManageSubEntity, keycloak, realm, renderExtraAction }) => {
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
      if (meta.showField && value[meta.showField] != null) {
        return value[meta.showField]
      }
      return value.label || value.name || value.codigo || value.id || "[objeto]"
    }

    if (meta.type?.toLowerCase().includes("date")) {
      const isDateTime = meta.type.toLowerCase().includes("time")
      return isDateTime
        ? dayjs(value).format("DD/MM/YYYY HH:mm")
        : dayjs(value).format("DD/MM/YYYY")
    }

    if (meta.enum && meta.enumValues && typeof meta.enumValues === "object" && !Array.isArray(meta.enumValues)) {
      return meta.enumValues[value] || value;
    }

    return value ?? ""
  }

  return (
    <table className={styles.table}>
      <thead>
        <tr>
          {fields.map(([key, meta]) => (
            <th key={key}>{meta.label || key}</th>
          ))}
          <th>Ações</th>
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
                <button onClick={() => onEdit(item)} className={styles.editBtn}>Editar</button>
                <button onClick={() => onDelete(item.id)} className={styles.deleteBtn}>Excluir</button>
                {subEntities.map(({ key, label, parentField, prop }) => (
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
  )
}

export default DynamicTable
