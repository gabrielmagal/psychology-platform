package br.com.psicologia.repository.model;

import br.com.psicologia.enums.PaymentMethod;
import br.com.psicologia.interceptor.AuditListener;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditListener.class)
public class PaymentEntity extends BaseEntity {

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "paid")
    private boolean paid;

    @Column(name = "observation")
    private String observation;

    @Column(name = "receipt", columnDefinition = "TEXT")
    private String receipt;

    @Column(name = "receipt_name", nullable = false)
    private String receiptName;

    @Column(name = "receipt_type", nullable = false)
    private String receiptType;

    @ManyToOne
    @JoinColumn(name = "session_package_id")
    private SessionPackageEntity sessionPackage;

    public PaymentEntity(UUID id, BigDecimal amount, LocalDate paymentDate, PaymentMethod paymentMethod, boolean paid,
                         String observation, String receiptName, String receiptType) {
        setId(id);
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.paid = paid;
        this.observation = observation;
        this.receiptName = receiptName;
        this.receiptType = receiptType;
    }
}