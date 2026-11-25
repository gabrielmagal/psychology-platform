import React, { useState } from "react";
import { FiEye } from "react-icons/fi";
import dayjs from "dayjs";
import EntityCrudPage from "../dynamic/EntityCrudPage";
import styles from "../dynamic/EntityCrudPage.module.css";

const ENTITY_LABEL = {
  UserEntity: "Usuário",
  SessionPackageEntity: "Pacote de sessões",
  SessionEntity: "Sessão",
  AnnotationEntity: "Anotação de sessão",
  PaymentEntity: "Pagamento",
  PatientRecordEntity: "Prontuário",
  AsaasAccountEntity: "Conta de recebimento",
};

const ACTION = {
  CREATE: { label: "Criação", tone: "ui-badge--success" },
  UPDATE: { label: "Alteração", tone: "ui-badge--info" },
  DELETE: { label: "Exclusão", tone: "ui-badge--danger" },
};

const FIELD_LABEL = {
  firstName: "Nome", lastName: "Sobrenome", cpf: "CPF", email: "E-mail", phoneNumber: "Telefone",
  birthDate: "Data de nascimento", userType: "Tipo de usuário", crp: "CRP", address: "Endereço",
  occupation: "Profissão", emergencyContactName: "Contato de emergência",
  emergencyContactPhone: "Telefone de emergência", guardianName: "Responsável legal",
  guardianPhone: "Telefone do responsável", keycloakId: "Id de login",
  registeredByKeycloakId: "Cadastrado por (id de login)", packageTitle: "Título", totalSessions: "Total de sessões",
  price: "Valor do pacote", patient: "Paciente (id)", professionalId: "Profissional (id)",
  psychologistId: "Profissional (id)", evolution: "Evolução", singleSession: "Sessão avulsa",
  dateSession: "Data", startTime: "Horário", durationMinutes: "Duração (min)", status: "Situação",
  modality: "Modalidade", meetingLink: "Link do atendimento", title: "Título", summary: "Resumo",
  privateNotes: "Notas privadas", attendedAt: "Presença registrada em", confirmedAt: "Confirmada em",
  remindedAt: "Lembrete enviado em", sessionPackage: "Pacote (id)", session: "Sessão (id)",
  amount: "Valor", paid: "Pago", paymentDate: "Data do pagamento", paymentMethod: "Forma de pagamento",
  paymentId: "Id da cobrança", invoiceUrl: "Link da cobrança", observation: "Observação",
  receipt: "Comprovante", receiptName: "Nome do comprovante", receiptType: "Tipo do comprovante",
  apiKey: "Chave de API", webhookToken: "Token do webhook", sandbox: "Ambiente de testes", ownerId: "Dono (id)",
};

const entityLabel = (name) => ENTITY_LABEL[name] || String(name || "").replace(/Entity$/, "");

function parse(json) {
  if (!json) return {};
  try {
    const value = JSON.parse(json);
    return value && typeof value === "object" ? value : { valor: value };
  } catch {
    return { valor: json };
  }
}

/** Conteúdo sensível é gravado como impressão digital: mostra-se que mudou, não o que era. */
function show(value) {
  if (value === null || value === undefined || value === "") return <span className="ui-muted">vazio</span>;
  if (typeof value === "boolean") return value ? "Sim" : "Não";
  if (typeof value === "string" && value.startsWith("sha256:")) {
    return <span className="ui-badge">conteúdo protegido</span>;
  }
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

function AuditDetails({ item, onClose }) {
  const before = parse(item.oldValue);
  const after = parse(item.newValue);
  const fields = Array.from(new Set([...Object.keys(before), ...Object.keys(after)])).sort();
  const action = ACTION[item.action] || { label: item.action, tone: "" };
  const changed = (key) => JSON.stringify(before[key]) !== JSON.stringify(after[key]);

  return (
    <div className="ui-overlay">
      <div className="ui-modal ui-modal--wide" role="dialog" aria-modal="true" aria-labelledby="audit-title">
        <h2 id="audit-title" className="ui-modal-title">
          {entityLabel(item.entityName)} <span className={`ui-badge ${action.tone}`}>{action.label}</span>
        </h2>

        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 18px", margin: "0 0 18px", fontSize: "0.9rem" }}>
          <dt className="ui-muted">Quando</dt>
          <dd style={{ margin: 0 }}>{dayjs(item.timestamp).format("DD/MM/YYYY [às] HH:mm:ss")}</dd>
          <dt className="ui-muted">Quem (id de login)</dt>
          <dd style={{ margin: 0, wordBreak: "break-all" }}>{item.keycloakUserId || "sistema"}</dd>
          <dt className="ui-muted">Registro (id)</dt>
          <dd style={{ margin: 0, wordBreak: "break-all" }}>{item.entityId}</dd>
        </dl>

        {fields.length === 0 ? (
          <p className="ui-muted">Este registro de auditoria não guardou o conteúdo alterado.</p>
        ) : (
          <div className="ui-table-wrap" style={{ boxShadow: "none" }}>
            <table className="ui-table">
              <thead>
              <tr><th>Campo</th><th>Antes</th><th>Depois</th></tr>
              </thead>
              <tbody>
              {fields.map((key) => (
                <tr key={key} style={changed(key) ? { background: "var(--warning-100)" } : undefined}>
                  <td style={{ fontWeight: 600 }}>{FIELD_LABEL[key] || key}</td>
                  <td style={{ wordBreak: "break-word" }}>{show(before[key])}</td>
                  <td style={{ wordBreak: "break-word" }}>{show(after[key])}</td>
                </tr>
              ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="ui-muted" style={{ marginTop: 12 }}>
          Linhas destacadas mudaram nesta ação. Conteúdo clínico e credenciais nunca são gravados em claro.
        </p>

        <div className="ui-modal-actions">
          <button className="ui-btn" onClick={onClose}>Fechar</button>
        </div>
      </div>
    </div>
  );
}

const FORMATTERS = {
  entityName: (value) => entityLabel(value),
  action: (value) => {
    const action = ACTION[value] || { label: value, tone: "" };
    return <span className={`ui-badge ${action.tone}`}>{action.label}</span>;
  },
};

export default function AuditLogCrud({ keycloak, realm, onUnauthorized, onForbidden }) {
  const [details, setDetails] = useState(null);

  return (
    <>
      <EntityCrudPage
        title="Auditoria"
        subtitle="Quem criou, alterou ou excluiu cada registro. Abra os detalhes para ver o que mudou."
        itemName="registro de auditoria"
        emptyHint="As alterações feitas no sistema passam a ser listadas aqui."
        resource="audit-log"
        keycloak={keycloak}
        realm={realm}
        onUnauthorized={onUnauthorized}
        onForbidden={onForbidden}
        defaultSort="timestamp,desc"
        cellFormatters={FORMATTERS}
        renderExtraAction={(item) => (
          <button className={styles.actionBtn} style={{ gap: 6 }} onClick={() => setDetails(item)}>
            <FiEye aria-hidden="true" /> Ver detalhes
          </button>
        )}
        allowedRolesToEdit={[""]}
        allowedRolesToDelete={[""]}
        allowedRolesToSubEntity={[""]}
      />
      {details && <AuditDetails item={details} onClose={() => setDetails(null)} />}
    </>
  );
}
