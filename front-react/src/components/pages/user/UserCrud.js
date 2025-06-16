import EntityCrudPage from "../dynamic/EntityCrudPage";
import {Roles} from "../../shared/Roles";

export default function UserCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  return (
    <EntityCrudPage
      title="👥 Usuários"
      resource="user"
      keycloak={keycloak}
      realm={realm}
      onUnauthorized={onUnauthorized}
      onForbidden={onForbidden}
      allowedRolesToEdit={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
      allowedRolesToDelete={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
      allowedRolesToSubEntity={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
    />
  );
}
