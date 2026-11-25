import React from "react";
import { Link } from "react-router-dom";
import { FiFileText } from "react-icons/fi";
import EntityCrudPage from "../dynamic/EntityCrudPage";
import { Roles } from "../../shared/Roles";
import styles from "../dynamic/EntityCrudPage.module.css";

const HIDDEN = ["lastName"];
const FORMATTERS = {
  firstName: (value, item) => [value, item.lastName].filter(Boolean).join(" "),
};

export default function UserCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  return (
    <EntityCrudPage
      title="Pacientes e usuários"
      subtitle="Cadastro de pacientes e profissionais. O prontuário de cada paciente fica a um clique."
      itemName="usuário"
      emptyHint="Cadastre o primeiro paciente para começar a agendar sessões."
      resource="user"
      keycloak={keycloak}
      realm={realm}
      onUnauthorized={onUnauthorized}
      onForbidden={onForbidden}
      // Nome completo numa coluna só: sobra espaço e a tabela cabe sem rolar de lado.
      hideColumns={HIDDEN}
      cellFormatters={FORMATTERS}
      renderExtraAction={(item) =>
        item.userType === "PACIENTE" && (
          <Link className={styles.actionBtn} style={{ gap: 6 }} to={`/prontuario/${item.id}`}>
            <FiFileText aria-hidden="true" /> Prontuário
          </Link>
        )
      }
      allowedRolesToEdit={[Roles.ADMIN, Roles.PROFESSIONAL]}
      allowedRolesToDelete={[Roles.ADMIN, Roles.PROFESSIONAL]}
      allowedRolesToSubEntity={[Roles.ADMIN, Roles.PROFESSIONAL]}
    />
  );
}
