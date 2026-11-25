package br.com.mindhaven.payment;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Provedor de pagamento em memória: substitui o Asaas nos testes. */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeGateway implements PaymentGateway {
    public static final String VALID_KEY = "chave-valida-1234";

    private final Map<String, Charge> charges = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger created = new AtomicInteger();

    /** Via método: o teste recebe um proxy do CDI, e campo público de proxy não reflete o bean. */
    public int createdCount() {
        return created.get();
    }

    @Override
    public String name() {
        return "Asaas";
    }

    @Override
    public void checkCredentials(Credentials credentials) {
        if (!VALID_KEY.equals(credentials.apiKey())) {
            throw new GatewayException("O Asaas recusou a chave de API.");
        }
    }

    @Override
    public String ensureCustomer(Credentials credentials, Payer payer) {
        return "cus_" + payer.cpf();
    }

    @Override
    public Charge createCharge(Credentials credentials, String customerId, BigDecimal value, LocalDate dueDate,
                               String description, String externalReference, String successUrl) {
        String id = "pay_" + sequence.incrementAndGet();
        Charge charge = new Charge(id, "https://sandbox.asaas.com/i/" + id, "PENDING", value, "UNDEFINED", null);
        charges.put(id, charge);
        created.incrementAndGet();
        return charge;
    }

    @Override
    public Charge getCharge(Credentials credentials, String chargeId) {
        Charge charge = charges.get(chargeId);
        if (charge == null) throw new GatewayException("Cobrança não encontrada.");
        return charge;
    }

    @Override
    public boolean isPaid(String status) {
        return "RECEIVED".equals(status) || "CONFIRMED".equals(status);
    }

    /** Simula o paciente pagando a fatura por Pix. */
    public void pay(String chargeId) {
        Charge c = charges.get(chargeId);
        charges.put(chargeId, new Charge(c.id(), c.invoiceUrl(), "RECEIVED", c.value(), "PIX", LocalDate.now()));
    }
}
