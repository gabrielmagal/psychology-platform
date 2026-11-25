package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Despesa do consultório (aluguel, supervisão, conselho...), para o livro-caixa. */
@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /** Dedutível no Carnê-Leão (despesa necessária à atividade). */
    @Column(name = "deductible")
    private boolean deductible = true;
}
