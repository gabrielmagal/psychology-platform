import EntityCrudPage from "../dynamic/EntityCrudPage";

export default function AuditLogCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  return (
    <EntityCrudPage
      title="👥 Auditoria"
      resource="audit-log"
      keycloak={keycloak}
      realm={realm}
      onUnauthorized={onUnauthorized}
      onForbidden={onForbidden}
      allowedRolesToEdit={[]}
      allowedRolesToDelete={[]}
      allowedRolesToSubEntity={[]}
    />
  );
}
