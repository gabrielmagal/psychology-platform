package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment")
@BatchSize(size = 32)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
public class PaymentEntity extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date")
    private LocalDate paymentDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "paid")
    private boolean paid;

    @Column(name = "observation")
    private String observation;

    // Comprovante em base64: documento do paciente, e volumoso demais para ser copiado
    // inteiro a cada alteração do pagamento.
    @ISensitive
    @Column(name = "receipt", columnDefinition = "TEXT")
    private String receipt;

    @Column(name = "receipt_name")
    private String receiptName;

    @Column(name = "receipt_type")
    private String receiptType;

    @ManyToOne
    @JoinColumn(name = "session_package_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
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