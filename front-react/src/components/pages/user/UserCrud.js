import React, { useState } from "react";
import { Link } from "react-router-dom";
import { FiFileText, FiKey } from "react-icons/fi";
import EntityCrudPage from "../dynamic/EntityCrudPage";
import ConfirmDialog from "../../shared/dialog/ConfirmDialog";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import TemporaryPasswordDialog from "./TemporaryPasswordDialog";
import { Roles } from "../../shared/Roles";
import { getUserRoles } from "../../shared/Auth";
import { apiFetch } from "../../../utils/fetchWithAuth";
import styles from "../dynamic/EntityCrudPage.module.css";

const HIDDEN = ["lastName"];
const fullName = (item) => [item.firstName, item.lastName].filter(Boolean).join(" ");
const FORMATTERS = {
  firstName: (value, item) => fullName(item),
};

export default function UserCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  const [password, setPassword] = useState(null);
  const [resetting, setResetting] = useState(null);
  const [error, setError] = useState(null);

  const roles = getUserRoles(keycloak);
  const isAdmin = roles.includes(Roles.ADMIN);
  const isProfessional = roles.includes(Roles.PROFESSIONAL);
  const myKeycloakId = keycloak?.tokenParsed?.sub;

  // Administrador gera senha para qualquer um; o profissional, só para os pacientes dele.
  const canReset = (item) =>
    item.keycloakId !== myKeycloakId && (isAdmin || (isProfessional && item.userType === "PACIENTE"));

  const show = (item, value, created) =>
    setPassword({ name: fullName(item), login: item.cpf, password: value, created });

  const reset = async (item) => {
    setResetting(null);
    try {
      const data = await apiFetch(`/user/${item.id}/temporary-password`,
        { method: "POST", keycloak, realm, onUnauthorized, onForbidden });
      show(item, data.temporaryPassword, false);
    } catch (err) {
      if (err?.name !== "AbortError") setError(err);
    }
  };

  return (
    <>
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
        // O cadastro devolve a senha provisória de primeiro acesso: é mostrada uma vez só.
        onSaved={(saved, created) => {
          if (created && saved?.temporaryPassword) show(saved, saved.temporaryPassword, true);
        }}
        renderExtraAction={[
          (item) =>
            item.userType === "PACIENTE" && (
              <Link key="record" className={styles.actionBtn} style={{ gap: 6 }} to={`/prontuario/${item.id}`}>
                <FiFileText aria-hidden="true" /> Prontuário
              </Link>
            ),
          (item) =>
            canReset(item) && (
              <button key="password" type="button" className={styles.actionBtn} style={{ gap: 6 }}
                      title="Gerar uma senha provisória nova para este usuário"
                      onClick={() => setResetting(item)}>
                <FiKey aria-hidden="true" /> Nova senha
              </button>
            ),
        ]}
        allowedRolesToEdit={[Roles.ADMIN, Roles.PROFESSIONAL]}
        allowedRolesToDelete={[Roles.ADMIN, Roles.PROFESSIONAL]}
        allowedRolesToSubEntity={[Roles.ADMIN, Roles.PROFESSIONAL]}
      />

      {resetting && (
        <ConfirmDialog
          title={`Gerar nova senha para ${fullName(resetting)}?`}
          message="A senha atual deixa de funcionar. Você recebe uma senha provisória para entregar à pessoa, que cria a própria no próximo acesso."
          confirmLabel="Gerar nova senha"
          onConfirm={() => reset(resetting)}
          onCancel={() => setResetting(null)}
        />
      )}

      <TemporaryPasswordDialog data={password} onClose={() => setPassword(null)} />
      <ErrorDialog open={!!error} message={error} onClose={() => setError(null)} />
    </>
  );
}
