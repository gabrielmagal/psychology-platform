import EntityCrudPage from "../dynamic/EntityCrudPage";

export default function UserCrud({ keycloak, realm }) {
  return (
    <EntityCrudPage
      title="👤 Usuários"
      resource="user"
      keycloak={keycloak}
      realm={realm}
    />
  );
}
