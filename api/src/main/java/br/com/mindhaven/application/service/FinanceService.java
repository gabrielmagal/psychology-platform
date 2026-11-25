package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.ExpenseEntity;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Livro-caixa do profissional: despesas, o resumo mês a mês (base do Carnê-Leão) e a
 * exportação para o contador. Cada profissional vê só o próprio caixa.
 */
@ApplicationScoped
@Transactional
public class FinanceService {
    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    public List<Map<String, Object>> expenses(String tenant, UserEntity user, LocalDate from, LocalDate to) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpenseEntity e : em.createQuery("select e from ExpenseEntity e where (:all = true or e.ownerId = :o) "
                        + "and e.expenseDate between :from and :to order by e.expenseDate desc", ExpenseEntity.class)
                .setParameter("all", seesAll(user)).setParameter("o", user.getId()).setParameter("from", from).setParameter("to", to).getResultList()) {
            rows.add(row(e));
        }
        return rows;
    }

    public Map<String, Object> saveExpense(String tenant, UserEntity user, UUID id, LocalDate date, String description,
                                           String category, BigDecimal amount, boolean deductible) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("A despesa é lançada pelo profissional, no próprio livro-caixa.");
        }
        if (date == null) throw BusinessException.invalid("Informe a data da despesa.");
        if (description == null || description.isBlank()) throw BusinessException.invalid("Descreva a despesa.");
        if (amount == null || amount.signum() <= 0) throw BusinessException.invalid("Informe um valor maior que zero.");
        dao.defineSchema(tenant);
        ExpenseEntity expense = id == null ? new ExpenseEntity() : owned(user, id);
        expense.setOwnerId(user.getId());
        expense.setExpenseDate(date);
        expense.setDescription(description.trim());
        expense.setCategory(category == null || category.isBlank() ? null : category.trim());
        expense.setAmount(amount);
        expense.setDeductible(deductible);
        return row(em.merge(expense));
    }

    public void deleteExpense(String tenant, UserEntity user, UUID id) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        em.remove(owned(user, id));
    }

    /**
     * Resumo do ano: receita, despesa e resultado mês a mês, mais os lançamentos. A receita
     * é o que foi <b>recebido</b> em cada mês (regime de caixa), como pede o Carnê-Leão.
     */
    public Map<String, Object> yearReport(String tenant, UserEntity user, int year) {
        requireProfessional(user);
        dao.defineSchema(tenant);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        BigDecimal[] income = zeros();
        BigDecimal[] spent = zeros();
        BigDecimal[] deductible = zeros();

        List<Map<String, Object>> incomeRows = new ArrayList<>();
        for (PaymentEntity p : em.createQuery("select p from PaymentEntity p join fetch p.sessionPackage pkg "
                        + "join fetch pkg.patient where p.paid = true and (:all = true or pkg.professionalId = :o) "
                        + "and p.paymentDate between :from and :to order by p.paymentDate", PaymentEntity.class)
                .setParameter("all", seesAll(user)).setParameter("o", user.getId()).setParameter("from", from).setParameter("to", to).getResultList()) {
            int month = p.getPaymentDate().getMonthValue() - 1;
            income[month] = income[month].add(p.getAmount());
            UserEntity patient = p.getSessionPackage().getPatient();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", p.getPaymentDate().toString());
            row.put("patientName", (patient.getFirstName() + " " + patient.getLastName()).trim());
            row.put("patientCpf", patient.getCpf());
            row.put("method", p.getPaymentMethod() == null ? null : p.getPaymentMethod().name());
            row.put("amount", p.getAmount());
            incomeRows.add(row);
        }

        List<Map<String, Object>> expenseRows = new ArrayList<>();
        for (ExpenseEntity e : em.createQuery("select e from ExpenseEntity e where (:all = true or e.ownerId = :o) "
                        + "and e.expenseDate between :from and :to order by e.expenseDate", ExpenseEntity.class)
                .setParameter("all", seesAll(user)).setParameter("o", user.getId()).setParameter("from", from).setParameter("to", to).getResultList()) {
            int month = e.getExpenseDate().getMonthValue() - 1;
            spent[month] = spent[month].add(e.getAmount());
            if (e.isDeductible()) deductible[month] = deductible[month].add(e.getAmount());
            expenseRows.add(row(e));
        }

        List<Map<String, Object>> months = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO, totalSpent = BigDecimal.ZERO, totalDeductible = BigDecimal.ZERO;
        for (int m = 0; m < 12; m++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m + 1);
            row.put("income", income[m]);
            row.put("expenses", spent[m]);
            row.put("deductible", deductible[m]);
            row.put("result", income[m].subtract(spent[m]));
            // Base do Carnê-Leão no mês: recebido de pessoas físicas menos despesas dedutíveis.
            row.put("taxBase", income[m].subtract(deductible[m]).max(BigDecimal.ZERO));
            months.add(row);
            totalIncome = totalIncome.add(income[m]);
            totalSpent = totalSpent.add(spent[m]);
            totalDeductible = totalDeductible.add(deductible[m]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("months", months);
        result.put("income", totalIncome);
        result.put("expenses", totalSpent);
        result.put("deductible", totalDeductible);
        result.put("result", totalIncome.subtract(totalSpent));
        result.put("incomeRows", incomeRows);
        result.put("expenseRows", expenseRows);
        return result;
    }

    /** Lançamentos do ano em CSV (ponto e vírgula, vírgula decimal): abre direto no Excel em português. */
    @SuppressWarnings("unchecked")
    public String yearCsv(String tenant, UserEntity user, int year) {
        Map<String, Object> report = yearReport(tenant, user, year);
        StringBuilder csv = new StringBuilder("﻿Tipo;Data;Descrição;CPF do pagador;Forma;Dedutível;Valor\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) report.get("incomeRows")) {
            csv.append("Receita;").append(row.get("date")).append(';').append(cell(row.get("patientName"))).append(';')
                    .append(row.get("patientCpf")).append(';').append(cell(row.get("method"))).append(";;")
                    .append(decimal(row.get("amount"))).append('\n');
        }
        for (Map<String, Object> row : (List<Map<String, Object>>) report.get("expenseRows")) {
            csv.append("Despesa;").append(row.get("date")).append(';')
                    .append(cell((row.get("category") == null ? "" : row.get("category") + " - ") + row.get("description")))
                    .append(";;;").append(Boolean.TRUE.equals(row.get("deductible")) ? "Sim" : "Não").append(';')
                    .append(decimal(row.get("amount"))).append('\n');
        }
        return csv.toString();
    }

    private ExpenseEntity owned(UserEntity user, UUID id) {
        ExpenseEntity expense = em.find(ExpenseEntity.class, id);
        if (expense == null) throw BusinessException.invalid("Despesa não encontrada.");
        if (!expense.getOwnerId().equals(user.getId())) throw new SecurityException("Esta despesa é de outro profissional.");
        return expense;
    }

    private static Map<String, Object> row(ExpenseEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("date", e.getExpenseDate().toString());
        row.put("description", e.getDescription());
        row.put("category", e.getCategory());
        row.put("amount", e.getAmount());
        row.put("deductible", e.isDeductible());
        return row;
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] values = new BigDecimal[12];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }

    /** Protege o CSV: aspas no texto e nada que o Excel interprete como fórmula. */
    private static String cell(Object value) {
        String text = value == null ? "" : value.toString().replace("\"", "\"\"");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text + "\"";
    }

    private static String decimal(Object value) {
        return new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** O administrador vê o livro-caixa de todos os profissionais; cada profissional, só o seu. */
    private static boolean seesAll(UserEntity user) {
        return user.getUserType() == UserType.ADMINISTRADOR;
    }

    /** O livro-caixa é de quem atende; o administrador da clínica não tem caixa próprio aqui. */
    private static void requireProfessional(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita ao profissional.");
    }
}
