package br.com.mindhaven.payment;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma cobrança aberta no provedor de pagamento. Pertence ao módulo de pagamento. */
@Entity
@Table(name = "online_charge")
@Getter
@Setter
@NoArgsConstructor
public class OnlineChargeEntity extends BaseEntity {
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";

    /** O que está sendo pago; o significado é da aplicação (ver {@link PayableSource}). */
    @Column(name = "payable_id", nullable = false)
    private UUID payableId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "gateway_charge_id", nullable = false, length = 100)
    private String gatewayChargeId;

    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "billing_type", length = 30)
    private String billingType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_on")
    private LocalDate paidOn;
}
