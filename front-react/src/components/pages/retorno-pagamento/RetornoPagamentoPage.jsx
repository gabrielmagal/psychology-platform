import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiCheckCircle } from "react-icons/fi";
import { Alert, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { useApi, money } from "../clinic/clinicApi";

/**
 * Para onde o Asaas devolve o paciente depois de pagar. Consulta o provedor — a página
 * nunca acredita nos parâmetros da URL para dar um pagamento como feito.
 */
export default function RetornoPagamentoPage({ keycloak, realm, onUnauthorized, onForbidden }) {
  const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
  const packageId = new URLSearchParams(window.location.search).get("pacote");
  const [result, setResult] = useState(null);
  const [error, setError] = useState(packageId ? null : "Não foi possível identificar o pacote deste pagamento.");

  const check = async () => {
    setError(null);
    setResult(null);
    try {
      setResult(await request(`/online-payment/sync/${packageId}`, { method: "POST" }));
    } catch (err) {
      setError(err);
    }
  };

  useEffect(() => {
    if (packageId) check();
    // eslint-disable-next-line
  }, [packageId]);

  const settled = result && Number(result.balance) === 0;

  return (
    <div className="ui-page" style={{ maxWidth: 640 }}>
      <PageHeader title="Pagamento" />
      {error && <ErrorState error={error} onRetry={packageId ? check : undefined} />}
      {!error && !result && <div className="ui-card"><Skeleton rows={3} /></div>}

      {result && settled && (
        <Alert tone="success" title="Pagamento confirmado" icon={FiCheckCircle}>
          Recebemos {money(result.paid)} e o pacote está quitado. Obrigado!
        </Alert>
      )}
      {result && !settled && (
        <Alert
          tone="info"
          title="Pagamento ainda não confirmado"
          actions={<button className="ui-btn ui-btn--sm" onClick={check}>Verificar de novo</button>}
        >
          O Asaas ainda não registrou o pagamento. Pix costuma confirmar em instantes; boleto pode levar até
          3 dias úteis. Falta pagar {money(result.balance)}.
        </Alert>
      )}

      <div className="ui-toolbar" style={{ marginTop: 20 }}>
        <Link className="ui-btn" to="/sessions-package">Voltar aos pacotes</Link>
      </div>
    </div>
  );
}
