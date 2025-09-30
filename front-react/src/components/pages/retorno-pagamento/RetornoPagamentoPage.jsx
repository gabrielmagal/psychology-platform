import React, { useEffect, useState } from "react";

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
        console.log('teste')
      try {
        const res = await fetch(`${process.env.REACT_APP_API_URL}/payment/confirm`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + keycloak.token,
            Tenant: realm,
          },
          body: JSON.stringify(dados),
        });
        if (!res.ok) throw new Error("Erro ao confirmar pagamento");
        const json = await res.json();
        setMensagem("Pagamento confirmado com sucesso!");
      } catch (err) {
        console.error(err);
        setMensagem("Falha ao confirmar pagamento.");
      }
    };

    enviarParaBackend();
  }, [keycloak]);

  return (
    <div style={{ textAlign: "center", marginTop: "3rem" }}>
      <h1>Pagamento {status}</h1>
      <p>{mensagem}</p>
    </div>
  );
}
