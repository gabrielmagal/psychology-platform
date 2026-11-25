package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Convênio (plano de saúde) com que a clínica trabalha. O cadastro é da clínica inteira. */
@Entity
@Table(name = "health_insurance")
@Getter
@Setter
@NoArgsConstructor
public class HealthInsuranceEntity extends BaseEntity {
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Registro da operadora na ANS. */
    @Column(name = "ans_code", length = 20)
    private String ansCode;

    /** Valor que o convênio paga por sessão; cada sessão pode ter um valor próprio. */
    @Column(name = "session_value")
    private BigDecimal sessionValue;

    /** Prazo de repasse, em dias, contado do faturamento. */
    @Column(name = "payment_days")
    private Integer paymentDays;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
