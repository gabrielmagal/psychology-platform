import React, { useEffect, useState } from "react";
import { apiFetch, extractCleanErrorMessage } from "../../../utils/fetchWithAuth";

export default function RetornoPagamentoPage({ keycloak, realm }) {
  const [status, setStatus] = useState(null);
  const [mensagem, setMensagem] = useState("");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const dados = {
        collectionId: params.get("collection_id"),
        paymentId: params.get("payment_id"),
        preferenceId: params.get("preference_id"),
        status: params.get("status"),
        paymentType: params.get("payment_type"),
        merchantOrderId: params.get("merchant_order_id"),
    };

    setStatus(dados.status);

    const enviarParaBackend = async () => {
      try {
        await apiFetch(`/payment/confirm`, { keycloak, realm, method: "POST", body: dados });
        setMensagem("Pagamento confirmado com sucesso!");
      } catch (err) {
        console.error(err);
        setMensagem(extractCleanErrorMessage(err) || "Falha ao confirmar pagamento.");
      }
    };

    enviarParaBackend();
    // eslint-disable-next-line
  }, [keycloak]);

  return (
    <div style={{ textAlign: "center", marginTop: "3rem" }}>
      <h1>Pagamento {status}</h1>
      <p>{mensagem}</p>
    </div>
  );
}
