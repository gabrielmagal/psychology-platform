package br.com.mindhaven.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Marca de que um recebimento já foi lançado no Receita Saúde. O recibo oficial é emitido
 * no programa da Receita Federal; aqui só se controla o que falta lançar.
 */
@Entity
@Table(name = "tax_receipt")
@Getter
@Setter
@NoArgsConstructor
public class TaxReceiptEntity {
    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "launched_at", nullable = false)
    private LocalDateTime launchedAt;

    @Column(name = "launched_by")
    private UUID launchedBy;

    /** Número do recibo gerado pela Receita, se o profissional quiser guardar. */
    @Column(name = "receipt_number", length = 60)
    private String receiptNumber;
}
