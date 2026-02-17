package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

@QuarkusTest
public class PaymentEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        PaymentEntity paymentEntity = entityGenerator.getPaymentEntity();

        Assertions.assertNotNull(paymentEntity.getId());
        Assertions.assertEquals(BigDecimal.valueOf(100.00), paymentEntity.getAmount());
        Assertions.assertEquals(LocalDate.now(), paymentEntity.getPaymentDate());
        Assertions.assertEquals(PaymentMethod.PIX, paymentEntity.getPaymentMethod());
        Assertions.assertTrue(paymentEntity.isPaid());
        Assertions.assertEquals("Campo de observação", paymentEntity.getObservation());
        Assertions.assertEquals("Recibo de pagamento", paymentEntity.getReceipt());
        Assertions.assertEquals("recibo.png", paymentEntity.getReceiptName());
        Assertions.assertEquals("image/png", paymentEntity.getReceiptType());
    }
}