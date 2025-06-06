import EntityCrudPage from "../dynamic/EntityCrudPage";

export default function PaymentCrud({ keycloak, realm }) {
  return (
    <EntityCrudPage
      title="👤 Pagamentos"
      resource="payment"
      keycloak={keycloak}
      realm={realm}
    />
  );
}
