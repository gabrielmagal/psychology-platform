package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.HealthInsuranceEntity;
import br.com.mindhaven.domain.entity.PackageInsuranceEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Convênios: o cadastro (da clínica inteira), os pacotes atendidos por convênio e o
 * acompanhamento do que foi realizado, faturado e recebido de cada operadora.
 *
 * <p>O repasse do convênio não entra no livro-caixa do Carnê-Leão: quem paga é pessoa
 * jurídica, com imposto retido na fonte. Por isso ele é controlado aqui, à parte.
 */
@ApplicationScoped
@Transactional
public class InsuranceService {
    public static final String TO_BILL = "A_FATURAR";
    public static final String BILLED = "FATURADA";
    public static final String PAID = "RECEBIDA";

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    // ---------------------------------------------------------------- cadastro

    public List<Map<String, Object>> list(String tenant, UserEntity user) {
        requireStaff(user);
        dao.defineSchema(tenant);
        Map<UUID, Long> usage = new HashMap<>();
        for (Object[] row : em.createQuery("select l.insuranceId, count(l) from PackageInsuranceEntity l "
                + "group by l.insuranceId", Object[].class).getResultList()) {
            usage.put((UUID) row[0], (Long) row[1]);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HealthInsuranceEntity i : em.createQuery("select i from HealthInsuranceEntity i order by i.active desc, "
                + "lower(i.name)", HealthInsuranceEntity.class).getResultList()) {
            Map<String, Object> row = describe(i);
            row.put("packages", usage.getOrDefault(i.getId(), 0L));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> save(String tenant, UserEntity user, UUID id, String name, String ansCode,
                                    BigDecimal sessionValue, Integer paymentDays, String notes, boolean active) {
        requireStaff(user);
        if (name == null || name.isBlank()) throw BusinessException.invalid("Informe o nome do convênio.");
        if (sessionValue != null && sessionValue.signum() < 0) throw BusinessException.invalid("O valor não pode ser negativo.");
        if (paymentDays != null && (paymentDays < 0 || paymentDays > 365)) {
            throw BusinessException.invalid("O prazo de repasse vai de 0 a 365 dias.");
        }
        dao.defineSchema(tenant);
        HealthInsuranceEntity insurance = id == null ? new HealthInsuranceEntity() : found(id);
        insurance.setName(name.trim());
        insurance.setAnsCode(ansCode);
        insurance.setSessionValue(sessionValue);
        insurance.setPaymentDays(paymentDays);
        insurance.setNotes(notes);
        insurance.setActive(active);
        return describe(em.merge(insurance));
    }

    public void remove(String tenant, UserEntity user, UUID id) {
        requireStaff(user);
        dao.defineSchema(tenant);
        HealthInsuranceEntity insurance = found(id);
        long linked = em.createQuery("select count(l) from PackageInsuranceEntity l where l.insuranceId = :i", Long.class)
                .setParameter("i", id).getSingleResult();
        if (linked > 0) {
            throw BusinessException.conflict("Este convênio tem pacotes vinculados. Desative-o em vez de excluir, "
                    + "para não perder o histórico de faturamento.");
        }
        em.remove(insurance);
    }

    // --------------------------------------------------------- pacote x convênio

    /** Vincula o pacote a um convênio. Sem convênio informado, o pacote volta a ser particular. */
    public void linkPackage(String tenant, UserEntity user, UUID packageId, UUID insuranceId, String cardNumber) {
        requireStaff(user);
        dao.defineSchema(tenant);
        ownedPackage(user, packageId);
        PackageInsuranceEntity link = em.find(PackageInsuranceEntity.class, packageId);
        if (insuranceId == null) {
            if (link != null) em.remove(link);
            return;
        }
        HealthInsuranceEntity insurance = found(insuranceId);
        if (!insurance.isActive()) throw BusinessException.conflict("Este convênio está desativado.");
        if (link == null) {
            link = new PackageInsuranceEntity();
            link.setPackageId(packageId);
        }
        link.setInsuranceId(insuranceId);
        link.setCardNumber(cardNumber);
        em.merge(link);
    }

    /** Pacotes do profissional (ou de todos, para o administrador), com o convênio de cada um. */
    public List<Map<String, Object>> packages(String tenant, UserEntity user) {
        requireStaff(user);
        dao.defineSchema(tenant);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select p from SessionPackageEntity p join fetch p.patient "
                + "where (p.singleSession is null or p.singleSession = false)"
                + (onlyMine ? " and p.professionalId = :me" : "")
                + " order by lower(p.patient.firstName), lower(p.patient.lastName)", SessionPackageEntity.class);
        if (onlyMine) query.setParameter("me", user.getId());
        List<SessionPackageEntity> all = query.getResultList();
        Map<UUID, PackageInsuranceEntity> links = links(all.stream().map(SessionPackageEntity::getId).toList());
        Map<UUID, String> names = names();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SessionPackageEntity p : all) {
            PackageInsuranceEntity link = links.get(p.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("packageId", p.getId());
            row.put("packageTitle", p.getPackageTitle());
            row.put("patientName", fullName(p.getPatient()));
            row.put("insuranceId", link == null ? null : link.getInsuranceId());
            row.put("insuranceName", link == null ? null : names.get(link.getInsuranceId()));
            row.put("cardNumber", link == null ? null : link.getCardNumber());
            rows.add(row);
        }
        return rows;
    }

    /** Nome do convênio de cada pacote informado; pacotes particulares ficam de fora. */
    public Map<UUID, String> namesByPackage(Collection<UUID> packageIds) {
        Map<UUID, String> result = new HashMap<>();
        Map<UUID, PackageInsuranceEntity> links = links(packageIds);
        if (links.isEmpty()) return result;
        Map<UUID, String> names = names();
        links.forEach((packageId, link) -> result.put(packageId, names.get(link.getInsuranceId())));
        return result;
    }

    // ------------------------------------------------------------- faturamento

    /**
     * Sessões realizadas por convênio no período, com a situação de cada uma: a faturar,
     * faturada (aguardando o repasse) ou recebida.
     */
    public Map<String, Object> sessions(String tenant, UserEntity user, LocalDate from, LocalDate to, UUID insuranceId) {
        requireStaff(user);
        dao.defineSchema(tenant);
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = from.plusMonths(1).minusDays(1);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;

        var query = em.createQuery("select s, l from SessionEntity s join fetch s.sessionPackage p join fetch p.patient "
                + "join PackageInsuranceEntity l on l.packageId = p.id where s.status = :done "
                + "and s.dateSession between :from and :to"
                + (onlyMine ? " and p.professionalId = :me" : "")
                + (insuranceId == null ? "" : " and l.insuranceId = :insurance")
                + " order by s.dateSession, s.startTime", Object[].class)
                .setParameter("done", SessionStatus.REALIZADA).setParameter("from", from).setParameter("to", to);
        if (onlyMine) query.setParameter("me", user.getId());
        if (insuranceId != null) query.setParameter("insurance", insuranceId);

        Map<UUID, HealthInsuranceEntity> insurances = new HashMap<>();
        for (HealthInsuranceEntity i : em.createQuery("select i from HealthInsuranceEntity i", HealthInsuranceEntity.class)
                .getResultList()) {
            insurances.put(i.getId(), i);
        }
        Map<UUID, String> professionals = new HashMap<>();

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of(TO_BILL, BILLED, PAID)) {
            totals.put(status, BigDecimal.ZERO);
            counts.put(status, 0);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] pair : query.getResultList()) {
            SessionEntity s = (SessionEntity) pair[0];
            PackageInsuranceEntity link = (PackageInsuranceEntity) pair[1];
            HealthInsuranceEntity insurance = insurances.get(link.getInsuranceId());
            SessionPackageEntity p = s.getSessionPackage();
            BigDecimal amount = amountOf(s, insurance);
            String status = statusOf(s);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", s.getId());
            row.put("date", s.getDateSession().toString());
            row.put("patientName", fullName(p.getPatient()));
            row.put("patientCpf", p.getPatient().getCpf());
            row.put("cardNumber", link.getCardNumber());
            row.put("insuranceId", link.getInsuranceId());
            row.put("insuranceName", insurance == null ? null : insurance.getName());
            row.put("guide", s.getInsuranceGuide());
            row.put("amount", amount);
            row.put("billedAt", s.getInsuranceBilledAt() == null ? null : s.getInsuranceBilledAt().toString());
            row.put("paidAt", s.getInsurancePaidAt() == null ? null : s.getInsurancePaidAt().toString());
            row.put("expectedAt", s.getInsuranceBilledAt() == null || insurance == null || insurance.getPaymentDays() == null
                    ? null : s.getInsuranceBilledAt().plusDays(insurance.getPaymentDays()).toString());
            row.put("status", status);
            if (!onlyMine) {
                row.put("professionalName", professionals.computeIfAbsent(p.getProfessionalId(),
                        id -> fullName(id == null ? null : em.find(UserEntity.class, id))));
            }
            rows.add(row);
            totals.merge(status, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add);
            counts.merge(status, 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("rows", rows);
        result.put("totals", totals);
        result.put("counts", counts);
        return result;
    }

    /**
     * Atualiza os dados de convênio de uma sessão. Só muda o que veio no corpo: assim a tela
     * pode gravar a guia sem mexer nas datas, ou marcar como recebida sem repetir o resto.
     */
    public void updateSession(String tenant, UserEntity user, UUID sessionId, Map<String, Object> body) {
        requireStaff(user);
        dao.defineSchema(tenant);
        SessionEntity session = em.find(SessionEntity.class, sessionId);
        if (session == null || session.getSessionPackage() == null) throw BusinessException.invalid("Sessão não encontrada.");
        ownedPackage(user, session.getSessionPackage().getId());
        if (em.find(PackageInsuranceEntity.class, session.getSessionPackage().getId()) == null) {
            throw BusinessException.conflict("Esta sessão não é de um pacote de convênio.");
        }
        if (body == null) return;
        if (body.containsKey("guide")) session.setInsuranceGuide(text(body.get("guide")));
        if (body.containsKey("amount")) {
            BigDecimal amount = decimal(body.get("amount"));
            if (amount != null && amount.signum() < 0) throw BusinessException.invalid("O valor não pode ser negativo.");
            session.setInsuranceAmount(amount);
        }
        if (body.containsKey("billedAt")) session.setInsuranceBilledAt(date(body.get("billedAt")));
        if (body.containsKey("paidAt")) session.setInsurancePaidAt(date(body.get("paidAt")));
        // Recebida sem data de faturamento: assume que foi faturada no mesmo dia.
        if (session.getInsurancePaidAt() != null && session.getInsuranceBilledAt() == null) {
            session.setInsuranceBilledAt(session.getInsurancePaidAt());
        }
        if (session.getInsuranceBilledAt() != null && session.getInsurancePaidAt() != null
                && session.getInsurancePaidAt().isBefore(session.getInsuranceBilledAt())) {
            throw BusinessException.invalid("O recebimento não pode ser anterior ao faturamento.");
        }
    }

    // --------------------------------------------------------------- auxiliares

    private static String statusOf(SessionEntity s) {
        if (s.getInsurancePaidAt() != null) return PAID;
        return s.getInsuranceBilledAt() != null ? BILLED : TO_BILL;
    }

    private static BigDecimal amountOf(SessionEntity s, HealthInsuranceEntity insurance) {
        if (s.getInsuranceAmount() != null) return s.getInsuranceAmount();
        return insurance == null ? null : insurance.getSessionValue();
    }

    private Map<UUID, PackageInsuranceEntity> links(Collection<UUID> packageIds) {
        Map<UUID, PackageInsuranceEntity> result = new HashMap<>();
        if (packageIds == null || packageIds.isEmpty()) return result;
        for (PackageInsuranceEntity link : em.createQuery("select l from PackageInsuranceEntity l where l.packageId in :ids",
                PackageInsuranceEntity.class).setParameter("ids", packageIds).getResultList()) {
            result.put(link.getPackageId(), link);
        }
        return result;
    }

    private Map<UUID, String> names() {
        Map<UUID, String> names = new HashMap<>();
        for (HealthInsuranceEntity i : em.createQuery("select i from HealthInsuranceEntity i", HealthInsuranceEntity.class)
                .getResultList()) {
            names.put(i.getId(), i.getName());
        }
        return names;
    }

    private HealthInsuranceEntity found(UUID id) {
        HealthInsuranceEntity insurance = id == null ? null : em.find(HealthInsuranceEntity.class, id);
        if (insurance == null) throw BusinessException.invalid("Convênio não encontrado.");
        return insurance;
    }

    private SessionPackageEntity ownedPackage(UserEntity user, UUID packageId) {
        SessionPackageEntity pkg = packageId == null ? null : em.find(SessionPackageEntity.class, packageId);
        if (pkg == null) throw BusinessException.invalid("Pacote não encontrado.");
        if (user.getUserType() == UserType.PROFISSIONAL && !user.getId().equals(pkg.getProfessionalId())) {
            throw new SecurityException("Este pacote é de outro profissional.");
        }
        return pkg;
    }

    private static Map<String, Object> describe(HealthInsuranceEntity i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", i.getId());
        row.put("name", i.getName());
        row.put("ansCode", i.getAnsCode());
        row.put("sessionValue", i.getSessionValue());
        row.put("paymentDays", i.getPaymentDays());
        row.put("notes", i.getNotes());
        row.put("active", i.isActive());
        return row;
    }

    private static void requireStaff(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita à equipe da clínica.");
    }

    private static String fullName(UserEntity user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal decimal(Object value) {
        String text = text(value);
        try {
            return text == null ? null : new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            throw BusinessException.invalid("Número inválido.");
        }
    }

    private static LocalDate date(Object value) {
        String text = text(value);
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.invalid("Data inválida.");
        }
    }
}
