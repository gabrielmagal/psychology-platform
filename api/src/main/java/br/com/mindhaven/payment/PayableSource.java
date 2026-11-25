package br.com.mindhaven.payment;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.payment.PaymentGateway.Payer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A ponte entre o módulo de pagamento e o sistema que o usa.
 *
 * <p>O módulo não sabe o que é um "pacote de sessões", um "plano" ou uma "consulta": ele
 * cobra um <em>pagável</em>. Cada sistema implementa esta interface dizendo o que é o
 * pagável, quanto falta pagar, quem paga e quem recebe — e escuta {@link ChargePaid} para
 * registrar o pagamento do seu jeito. É só isso que muda de um sistema para outro.
 */
public interface PayableSource {

    /**
     * @param id          identificador do que está sendo pago
     * @param description texto que aparece na fatura
     * @param amountDue   quanto falta pagar; {@code null} se o valor ainda não foi definido
     * @param receiverId  usuário que recebe (dono da conta no provedor)
     * @param payer       quem paga
     */
    record Payable(UUID id, String description, BigDecimal amountDue, UUID receiverId, Payer payer) {
    }

    /** Pagamento confirmado pelo provedor. Disparado dentro da mesma transação da baixa. */
    record ChargePaid(String tenant, UUID payableId, UUID chargeId, BigDecimal amount, LocalDate paidOn,
                      String billingType) {
    }

    /**
     * Carrega o pagável, já conferindo se o usuário pode vê-lo/pagá-lo.
     *
     * @throws SecurityException se o usuário não tem acesso
     * @return o pagável, ou {@code null} se não existe
     */
    Payable load(String tenant, UserEntity user, UUID payableId);

    /** O mesmo, sem usuário logado — usado no aviso (webhook) do provedor. */
    Payable loadForSystem(String tenant, UUID payableId);
}
