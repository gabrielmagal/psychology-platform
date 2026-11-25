package br.com.mindhaven.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que a aplicação precisa de um provedor de pagamento online.
 *
 * <p>As regras (quem pode cobrar, quanto falta pagar, quando dar baixa) ficam em
 * {@link OnlinePaymentService} e não conhecem o provedor. Para trocar de provedor, ou para
 * reaproveitar esta base em outro sistema, basta outra implementação desta interface.
 */
public interface PaymentGateway {

    /** Chave de acesso do recebedor e o ambiente (testes ou produção). */
    record Credentials(String apiKey, boolean sandbox) {
    }

    /** Quem vai pagar. */
    record Payer(String name, String cpf, String email, String phone) {
    }

    /** Uma cobrança no provedor. */
    record Charge(String id, String invoiceUrl, String status, BigDecimal value, String billingType,
                  LocalDate paymentDate) {
    }

    /** Nome do provedor, para mensagens e registros. */
    String name();

    /** Confere se a chave funciona. Lança {@link GatewayException} se não. */
    void checkCredentials(Credentials credentials);

    /** Devolve o id do pagador no provedor, cadastrando-o se ainda não existir. */
    String ensureCustomer(Credentials credentials, Payer payer);

    /** Abre uma cobrança em que o pagador escolhe a forma de pagamento. */
    Charge createCharge(Credentials credentials, String customerId, BigDecimal value, LocalDate dueDate,
                        String description, String externalReference, String successUrl);

    Charge getCharge(Credentials credentials, String chargeId);

    /** A situação informada pelo provedor significa que o dinheiro foi pago. */
    boolean isPaid(String status);

    /** Falha ao falar com o provedor, com mensagem que pode ser mostrada ao usuário. */
    class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }

        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
