import EntityCrudPage from "../dynamic/EntityCrudPage";

export default function SessionCrud({ keycloak, realm }) {
  return (
    <EntityCrudPage
      title="👤 Sessões"
      resource="session"
      keycloak={keycloak}
      realm={realm}
    />
  );
}
