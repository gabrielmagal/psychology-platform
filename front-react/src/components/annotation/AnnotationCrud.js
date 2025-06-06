import EntityCrudPage from "../dynamic/EntityCrudPage";

export default function AnnotationCrud({ keycloak, realm }) {
  return (
    <EntityCrudPage
      title="👤 Anotações"
      resource="annotation"
      keycloak={keycloak}
      realm={realm}
    />
  );
}
