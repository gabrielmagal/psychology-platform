import EntityCrudPage from "../dynamic/EntityCrudPage";
import {Roles} from "../../shared/Roles";
import { Link } from "react-router-dom";
import styles from "../dynamic/EntityCrudPage.module.css";

export default function UserCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  return (
    <EntityCrudPage
      title="👥 Usuários"
      resource="user"
      keycloak={keycloak}
      realm={realm}
      onUnauthorized={onUnauthorized}
      onForbidden={onForbidden}
      renderExtraAction={(item) =>
        item.userType === "PACIENTE" && (
          <Link className={styles.actionBtn} style={{ textDecoration: "none" }} to={`/prontuario/${item.id}`}>
            Prontuário
          </Link>
        )
      }
      allowedRolesToEdit={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
      allowedRolesToDelete={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
      allowedRolesToSubEntity={[Roles.ADMIN, Roles.PSYCHOLOGIST]}
    />
  );
}
