package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.SessionUseCase;
import br.com.mindhaven.domain.entity.ConsentRecordEntity;
import br.com.mindhaven.domain.entity.MoodEntryEntity;
import br.com.mindhaven.domain.entity.PatientRecordEntity;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras do dia a dia do consultório que não são CRUD: agenda, prontuário, documentos,
 * financeiro, consentimento (LGPD) e acompanhamento do paciente.
 *
 * <p>Todos os métodos recebem o tenant e o usuário logado e aplicam a regra de visibilidade
 * aqui dentro: administrador vê tudo, profissional vê só os próprios pacotes/pacientes e
 * paciente vê só o que é dele. As respostas são mapas simples, com datas já em texto, para
 * não depender do serializador JSON em uso.
 */
@ApplicationScoped
@Transactional
public class ClinicService {
    /** Versão do termo de consentimento em vigor. Mudou o texto, muda a versão. */
    public static final String CONSENT_VERSION = "1.0";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_OCCURRENCES = 52;

    /** De quanto em quanto tempo a sessão se repete. */
    public enum Frequency {
        WEEKLY, BIWEEKLY, MONTHLY;

        LocalDate occurrence(LocalDate first, int index) {
            return switch (this) {
                case WEEKLY -> first.plusWeeks(index);
                case BIWEEKLY -> first.plusWeeks(2L * index);
                case MONTHLY -> first.plusMonths(index);
            };
        }
    }

    /** O que foi agendado e as datas puladas (com o motivo), quando se pediu para pular conflitos. */
    public record ScheduleResult(List<UUID> created, List<Map<String, Object>> skipped, String meetingLink) {
    }

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    SessionUseCase sessionUseCase;

    @Inject
    PatientAccess access;

    @Inject
    AgendaRules rules;

    @Inject
    InsuranceService insurances;

    @Inject
    MeetingRooms meetingRooms;

    @Inject
    CareService care;

    @Inject
    InstrumentService instruments;

    // ------------------------------------------------------------------ agenda

    public List<Map<String, Object>> agenda(String tenant, UserEntity user, LocalDate from, LocalDate to) {
        dao.defineSchema(tenant);
        if (from == null) from = LocalDate.now();
        if (to == null) to = from.plusDays(6);

        TypedQuery<SessionEntity> query = em.createQuery(
                "select s from SessionEntity s join fetch s.sessionPackage p join fetch p.patient "
                        + "where s.dateSession between :from and :to" + scope(user, "p")
                        + " order by s.dateSession, s.startTime", SessionEntity.class)
                .setParameter("from", from)
                .setParameter("to", to);
        bindScope(query, user);
        List<SessionEntity> sessions = query.getResultList();

        boolean isPatient = user.getUserType() == UserType.PACIENTE;
        Set<UUID> rated = isPatient ? reviews.reviewedContexts(tenant, user.getId(), REVIEW_CONTEXT) : Set.of();
        Map<UUID, String> professionalNames = new HashMap<>();
        Map<UUID, String> insuranceNames = insurances.namesByPackage(
                sessions.stream().map(s -> s.getSessionPackage().getId()).distinct().toList());

        List<Map<String, Object>> items = new ArrayList<>();
        for (SessionEntity s : sessions) {
            SessionPackageEntity p = s.getSessionPackage();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("date", text(s.getDateSession()));
            item.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(HHMM));
            item.put("durationMinutes", s.getDurationMinutes() == null ? 50 : s.getDurationMinutes());
            item.put("status", statusOf(s).name());
            item.put("modality", s.getModality() == null ? SessionModality.PRESENCIAL.name() : s.getModality().name());
            item.put("meetingLink", s.getMeetingLink());
            item.put("title", s.getTitle());
            item.put("confirmedAt", text(s.getConfirmedAt()));
            item.put("remindedAt", text(s.getRemindedAt()));
            item.put("packageId", p.getId());
            item.put("packageTitle", p.getPackageTitle());
            item.put("singleSession", Boolean.TRUE.equals(p.getSingleSession()));
            item.put("seriesId", s.getSeriesId());
            item.put("insuranceName", insuranceNames.get(p.getId()));
            if (!isPatient) {
                item.put("insuranceGuide", s.getInsuranceGuide());
                item.put("hasRecord", !SessionRecord.isEmpty(s));
                item.put("recordSigned", s.getRecordSignedAt() != null);
                item.put("riskAlert", Boolean.TRUE.equals(s.getRiskAlert()));
            }
            item.put("professionalId", p.getProfessionalId());
            item.put("patientId", p.getPatient().getId());
            item.put("patientName", fullName(p.getPatient()));
            if (isPatient) {
                item.put("professionalName", professionalNames.computeIfAbsent(
                        p.getProfessionalId(), id -> fullName(id == null ? null : em.find(UserEntity.class, id))));
                item.put("rated", rated.contains(s.getId()));
            } else {
                item.put("patientPhone", p.getPatient().getPhoneNumber());
            }
            items.add(item);
        }
        return items;
    }

    /** Cria a sessão e, se pedido, as repetições semanais. Para na primeira que conflitar. */
    public List<UUID> schedule(String tenant, UserEntity user, UUID packageId, LocalDate date, LocalTime startTime,
                               Integer durationMinutes, SessionModality modality, String meetingLink, String title,
                               int repeatWeeks) {
        return schedule(tenant, user, packageId, date, startTime, durationMinutes, modality, meetingLink, title,
                Frequency.WEEKLY, repeatWeeks, false).created();
    }

    /**
     * Cria a sessão e as repetições (semanal, quinzenal ou mensal), todas na mesma série.
     *
     * <p>Com {@code skipConflicts}, a data que não cabe na agenda (feriado bloqueado, outro
     * paciente no horário) é pulada e devolvida com o motivo; sem ele, o primeiro conflito
     * cancela o agendamento inteiro. Sessão online sem link ganha uma sala de vídeo, a mesma
     * para a série toda.
     */
    public ScheduleResult schedule(String tenant, UserEntity user, UUID packageId, LocalDate date, LocalTime startTime,
                                   Integer durationMinutes, SessionModality modality, String meetingLink, String title,
                                   Frequency frequency, int occurrences, boolean skipConflicts) {
        requireStaff(user);
        if (packageId == null) throw core.error.BusinessException.invalid("Informe o pacote de sessões.");
        if (date == null || startTime == null) throw core.error.BusinessException.invalid("Informe a data e o horário.");
        int total = Math.max(1, Math.min(occurrences, MAX_OCCURRENCES));
        Frequency every = frequency == null ? Frequency.WEEKLY : frequency;
        int duration = durationMinutes == null ? 50 : durationMinutes;
        SessionModality mode = modality == null ? SessionModality.PRESENCIAL : modality;
        String link = mode == SessionModality.ONLINE ? meetingRooms.linkOrNew(meetingLink) : null;
        UUID seriesId = total > 1 ? UUID.randomUUID() : null;

        dao.defineSchema(tenant);
        SessionPackageEntity pkg = em.find(SessionPackageEntity.class, packageId);
        UUID professionalId = pkg == null ? null : pkg.getProfessionalId();

        List<UUID> created = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            LocalDate day = every.occurrence(date, index);
            if (skipConflicts) {
                String reason = rules.reason(tenant, professionalId, day, startTime, duration, null);
                if (reason != null) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", day.toString());
                    row.put("reason", reason);
                    skipped.add(row);
                    continue;
                }
            }
            SessionPackageEntity packageRef = new SessionPackageEntity();
            packageRef.setId(packageId);

            SessionEntity session = new SessionEntity();
            session.setSessionPackage(packageRef);
            session.setDateSession(day);
            session.setStartTime(startTime);
            session.setDurationMinutes(duration);
            session.setModality(mode);
            session.setMeetingLink(link);
            session.setTitle(title == null || title.isBlank() ? "Sessão" : title);
            session.setStatus(SessionStatus.AGENDADA);
            session.setSeriesId(seriesId);
            created.add(sessionUseCase.save(tenant, user, session).getId());
        }
        if (created.isEmpty()) {
            throw core.error.BusinessException.conflict("Nenhuma das datas cabe na agenda. "
                    + (skipped.isEmpty() ? "" : "Primeiro motivo: " + skipped.getFirst().get("reason")));
        }
        return new ScheduleResult(created, skipped, link);
    }

    public void changeStatus(String tenant, UserEntity user, UUID sessionId, SessionStatus status) {
        requireStaff(user);
        SessionEntity session = ownedSession(tenant, user, sessionId);
        session.setStatus(status);
        if (status == SessionStatus.REALIZADA && session.getAttendedAt() == null) {
            session.setAttendedAt(LocalDateTime.now());
        }
        if (status != SessionStatus.REALIZADA) {
            session.setAttendedAt(null);
        }
    }

    /** O paciente confirma que vai comparecer. */
    public void confirm(String tenant, UserEntity user, UUID sessionId) {
        SessionEntity session = ownedSession(tenant, user, sessionId);
        if (statusOf(session) != SessionStatus.AGENDADA) {
            throw core.error.BusinessException.conflict("Só é possível confirmar uma sessão agendada.");
        }
        session.setConfirmedAt(LocalDateTime.now());
    }

    public void markReminded(String tenant, UserEntity user, UUID sessionId) {
        requireStaff(user);
        ownedSession(tenant, user, sessionId).setRemindedAt(LocalDateTime.now());
    }

    /** Contexto das avaliações de sessão no módulo de avaliações. */
    public static final String REVIEW_CONTEXT = "SESSION";

    @Inject
    br.com.mindhaven.review.ReviewService reviews;

    public void rate(String tenant, UserEntity user, UUID sessionId, int rating, String comment) {
        rate(tenant, user, sessionId, rating, comment, null, false);
    }

    /**
     * O paciente avalia uma sessão realizada. A nota é do profissional que atendeu; o
     * comentário só pode ir a público se o paciente autorizar (e depois a clínica aprovar).
     */
    public void rate(String tenant, UserEntity user, UUID sessionId, int rating, String comment,
                     Boolean recommend, boolean allowPublicComment) {
        if (user.getUserType() != UserType.PACIENTE) {
            throw new SecurityException("Só o paciente avalia a sessão.");
        }
        if (rating < 1 || rating > 5) throw core.error.BusinessException.invalid("A nota vai de 1 a 5.");
        SessionEntity session = ownedSession(tenant, user, sessionId);
        if (statusOf(session) != SessionStatus.REALIZADA) {
            throw core.error.BusinessException.conflict("Só é possível avaliar uma sessão já realizada.");
        }
        UUID professionalId = session.getSessionPackage().getProfessionalId();
        if (professionalId == null) throw core.error.BusinessException.conflict("Esta sessão não tem profissional definido.");
        reviews.submit(tenant, user.getId(), professionalId, REVIEW_CONTEXT, sessionId, rating, recommend,
                comment, allowPublicComment);
    }

    // -------------------------------------------------------------- prontuário

    /**
     * Anamnese do paciente. Cada profissional tem a sua: o mesmo paciente pode ser atendido
     * por mais de um, e o que um escreve o outro não vê. O administrador vê a de todos, só
     * para leitura.
     */
    public Map<String, Object> patientRecord(String tenant, UserEntity user, UUID patientId) {
        UserEntity patient = accessiblePatient(tenant, user, patientId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patient", patientCard(patient));

        if (user.getUserType() == UserType.ADMINISTRADOR) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (PatientRecordEntity record : em.createQuery(
                            "select r from PatientRecordEntity r where r.patient.id = :p order by r.updatedAt desc",
                            PatientRecordEntity.class).setParameter("p", patientId).getResultList()) {
                Map<String, Object> row = recordFields(record);
                UserEntity author = record.getProfessionalId() == null ? null : em.find(UserEntity.class, record.getProfessionalId());
                row.put("professionalName", author == null ? "Não identificado" : fullName(author));
                records.add(row);
            }
            result.put("readOnly", true);
            result.put("records", records);
            return result;
        }
        result.putAll(recordFields(findRecord(patientId, user.getId())));
        return result;
    }

    private static Map<String, Object> recordFields(PatientRecordEntity record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chiefComplaint", record == null ? null : record.getChiefComplaint());
        fields.put("complaintHistory", record == null ? null : record.getComplaintHistory());
        fields.put("familyHistory", record == null ? null : record.getFamilyHistory());
        fields.put("healthHistory", record == null ? null : record.getHealthHistory());
        fields.put("diagnosticHypothesis", record == null ? null : record.getDiagnosticHypothesis());
        fields.put("therapeuticGoals", record == null ? null : record.getTherapeuticGoals());
        fields.put("observations", record == null ? null : record.getObservations());
        fields.put("updatedAt", record == null ? null : text(record.getUpdatedAt()));
        return fields;
    }

    public void savePatientRecord(String tenant, UserEntity user, UUID patientId, Map<String, String> fields) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("A anamnese é registrada pelo profissional que atende o paciente.");
        }
        UserEntity patient = accessiblePatient(tenant, user, patientId);
        PatientRecordEntity record = findRecord(patientId, user.getId());
        if (record == null) {
            record = new PatientRecordEntity();
            record.setPatient(patient);
            record.setProfessionalId(user.getId());
        }
        record.setChiefComplaint(fields.get("chiefComplaint"));
        record.setComplaintHistory(fields.get("complaintHistory"));
        record.setFamilyHistory(fields.get("familyHistory"));
        record.setHealthHistory(fields.get("healthHistory"));
        record.setDiagnosticHypothesis(fields.get("diagnosticHypothesis"));
        record.setTherapeuticGoals(fields.get("therapeuticGoals"));
        record.setObservations(fields.get("observations"));
        record.setUpdatedAt(LocalDateTime.now());
        em.merge(record);
    }

    /** Linha do tempo do paciente: sessões (mais recente primeiro) com resumo e anotações. */
    public List<Map<String, Object>> timeline(String tenant, UserEntity user, UUID patientId) {
        accessiblePatient(tenant, user, patientId);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;

        TypedQuery<SessionEntity> query = em.createQuery(
                "select s from SessionEntity s join fetch s.sessionPackage p where p.patient.id = :patient"
                        + (onlyMine ? " and p.professionalId = :me" : "")
                        + " order by s.dateSession desc, s.startTime desc", SessionEntity.class)
                .setParameter("patient", patientId);
        if (onlyMine) query.setParameter("me", user.getId());

        List<Map<String, Object>> items = new ArrayList<>();
        for (SessionEntity s : query.getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("date", text(s.getDateSession()));
            item.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(HHMM));
            item.put("status", statusOf(s).name());
            item.put("title", s.getTitle());
            if (!onlyMine) {
                UUID owner = s.getSessionPackage().getProfessionalId();
                item.put("professionalName", owner == null ? null : fullName(em.find(UserEntity.class, owner)));
            }
            item.putAll(SessionRecord.read(s));
            // Nota privada é de quem escreveu: o administrador lê o prontuário, não ela.
            if (user.getId().equals(s.getSessionPackage().getProfessionalId())) {
                item.put("privateNotes", s.getPrivateNotes());
            }
            item.put("packageTitle", s.getSessionPackage().getPackageTitle());
            items.add(item);
        }
        return items;
    }

    // -------------------------------------------------------------- documentos

    /** Dados para o recibo de um pagamento (o front monta a página de impressão). */
    public Map<String, Object> receipt(String tenant, UserEntity user, UUID paymentId) {
        dao.defineSchema(tenant);
        PaymentEntity payment = em.find(PaymentEntity.class, paymentId);
        if (payment == null || payment.getSessionPackage() == null) {
            throw core.error.BusinessException.invalid("Pagamento não encontrado.");
        }
        SessionPackageEntity pkg = payment.getSessionPackage();
        requirePackageAccess(user, pkg);
        if (!payment.isPaid()) throw core.error.BusinessException.conflict("Este pagamento ainda não foi confirmado.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("number", payment.getId().toString().substring(0, 8).toUpperCase());
        result.put("amount", payment.getAmount());
        result.put("paymentDate", text(payment.getPaymentDate()));
        result.put("paymentMethod", payment.getPaymentMethod() == null ? null : payment.getPaymentMethod().name());
        result.put("packageTitle", pkg.getPackageTitle());
        result.put("patient", patientCard(pkg.getPatient()));
        result.put("professional", professionalCard(pkg.getProfessionalId()));
        return result;
    }

    /** Dados para a declaração de comparecimento de uma sessão realizada. */
    public Map<String, Object> attendance(String tenant, UserEntity user, UUID sessionId) {
        SessionEntity session = ownedSession(tenant, user, sessionId);
        if (statusOf(session) != SessionStatus.REALIZADA) {
            throw core.error.BusinessException.conflict("A declaração só existe para sessão realizada.");
        }
        SessionPackageEntity pkg = session.getSessionPackage();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", text(session.getDateSession()));
        result.put("startTime", session.getStartTime() == null ? null : session.getStartTime().format(HHMM));
        result.put("durationMinutes", session.getDurationMinutes() == null ? 50 : session.getDurationMinutes());
        result.put("patient", patientCard(pkg.getPatient()));
        result.put("professional", professionalCard(pkg.getProfessionalId()));
        return result;
    }

    // -------------------------------------------------------------- financeiro

    public Map<String, Object> finance(String tenant, UserEntity user, LocalDate from, LocalDate to) {
        requireStaff(user);
        dao.defineSchema(tenant);
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = from.plusMonths(1).minusDays(1);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        String mine = onlyMine ? " and pay.sessionPackage.professionalId = :me" : "";

        TypedQuery<BigDecimal> receivedQuery = em.createQuery(
                "select coalesce(sum(pay.amount), 0) from PaymentEntity pay where pay.paid = true "
                        + "and pay.paymentDate between :from and :to" + mine, BigDecimal.class)
                .setParameter("from", from).setParameter("to", to);
        if (onlyMine) receivedQuery.setParameter("me", user.getId());
        BigDecimal received = receivedQuery.getSingleResult();

        TypedQuery<Object[]> paidQuery = em.createQuery(
                "select pay.sessionPackage.id, sum(pay.amount) from PaymentEntity pay where pay.paid = true"
                        + mine + " group by pay.sessionPackage.id", Object[].class);
        if (onlyMine) paidQuery.setParameter("me", user.getId());
        Map<UUID, BigDecimal> paidByPackage = new HashMap<>();
        for (Object[] row : paidQuery.getResultList()) {
            paidByPackage.put((UUID) row[0], (BigDecimal) row[1]);
        }

        TypedQuery<SessionPackageEntity> packagesQuery = em.createQuery(
                "select p from SessionPackageEntity p join fetch p.patient where p.price is not null"
                        + (onlyMine ? " and p.professionalId = :me" : ""), SessionPackageEntity.class);
        if (onlyMine) packagesQuery.setParameter("me", user.getId());

        BigDecimal outstanding = BigDecimal.ZERO;
        List<Map<String, Object>> debtors = new ArrayList<>();
        for (SessionPackageEntity p : packagesQuery.getResultList()) {
            BigDecimal paid = paidByPackage.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal balance = p.getPrice().subtract(paid);
            if (balance.signum() <= 0) continue;
            outstanding = outstanding.add(balance);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("packageId", p.getId());
            row.put("packageTitle", p.getPackageTitle());
            row.put("patientName", fullName(p.getPatient()));
            row.put("patientPhone", p.getPatient().getPhoneNumber());
            row.put("price", p.getPrice());
            row.put("paid", paid);
            row.put("balance", balance);
            debtors.add(row);
        }
        debtors.sort((a, b) -> ((BigDecimal) b.get("balance")).compareTo((BigDecimal) a.get("balance")));

        TypedQuery<SessionEntity> sessionsQuery = em.createQuery(
                "select s from SessionEntity s join s.sessionPackage p where s.dateSession between :from and :to"
                        + (onlyMine ? " and p.professionalId = :me" : ""), SessionEntity.class)
                .setParameter("from", from).setParameter("to", to);
        if (onlyMine) sessionsQuery.setParameter("me", user.getId());
        Map<String, Integer> sessionsByStatus = new LinkedHashMap<>();
        for (SessionStatus status : SessionStatus.values()) sessionsByStatus.put(status.name(), 0);
        for (SessionEntity s : sessionsQuery.getResultList()) {
            sessionsByStatus.merge(statusOf(s).name(), 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", text(from));
        result.put("to", text(to));
        result.put("received", received);
        result.put("outstanding", outstanding);
        result.put("debtors", debtors);
        result.put("sessionsByStatus", sessionsByStatus);
        return result;
    }

    // -------------------------------------------------------------------- LGPD

    public Map<String, Object> consentStatus(String tenant, UserEntity user) {
        dao.defineSchema(tenant);
        ConsentRecordEntity last = em.createQuery(
                        "select c from ConsentRecordEntity c where c.user.id = :u and c.version = :v "
                                + "order by c.acceptedAt desc", ConsentRecordEntity.class)
                .setParameter("u", user.getId()).setParameter("v", CONSENT_VERSION)
                .setMaxResults(1).getResultStream().findFirst().orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", CONSENT_VERSION);
        result.put("accepted", last != null);
        result.put("acceptedAt", last == null ? null : text(last.getAcceptedAt()));
        return result;
    }

    public void acceptConsent(String tenant, UserEntity user) {
        dao.defineSchema(tenant);
        ConsentRecordEntity record = new ConsentRecordEntity();
        record.setUser(em.getReference(UserEntity.class, user.getId()));
        record.setVersion(CONSENT_VERSION);
        record.setAcceptedAt(LocalDateTime.now());
        em.persist(record);
    }

    /**
     * Portabilidade (LGPD, art. 18): tudo o que a plataforma guarda sobre o próprio usuário.
     * As notas privadas do profissional não entram — são registro do profissional, não dado
     * fornecido pelo titular; o acesso ao prontuário é pedido ao profissional.
     */
    public Map<String, Object> exportMyData(String tenant, UserEntity user) {
        dao.defineSchema(tenant);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportedAt", text(LocalDateTime.now()));

        Map<String, Object> profile = new LinkedHashMap<>(patientCard(user));
        profile.put("email", user.getEmail());
        profile.put("address", user.getAddress());
        profile.put("occupation", user.getOccupation());
        profile.put("emergencyContactName", user.getEmergencyContactName());
        profile.put("emergencyContactPhone", user.getEmergencyContactPhone());
        profile.put("userType", user.getUserType().name());
        result.put("profile", profile);

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (SessionEntity s : em.createQuery(
                        "select s from SessionEntity s join fetch s.sessionPackage p where p.patient.id = :u "
                                + "order by s.dateSession", SessionEntity.class)
                .setParameter("u", user.getId()).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", text(s.getDateSession()));
            row.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(HHMM));
            row.put("status", statusOf(s).name());
            row.put("package", s.getSessionPackage().getPackageTitle());
            sessions.add(row);
        }
        result.put("sessions", sessions);

        List<Map<String, Object>> payments = new ArrayList<>();
        for (PaymentEntity pay : em.createQuery(
                        "select pay from PaymentEntity pay where pay.sessionPackage.patient.id = :u "
                                + "order by pay.paymentDate", PaymentEntity.class)
                .setParameter("u", user.getId()).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", text(pay.getPaymentDate()));
            row.put("amount", pay.getAmount());
            row.put("paid", pay.isPaid());
            row.put("method", pay.getPaymentMethod() == null ? null : pay.getPaymentMethod().name());
            payments.add(row);
        }
        result.put("payments", payments);
        result.put("moodDiary", moodRows(user.getId(), 3650));

        List<Map<String, Object>> consents = new ArrayList<>();
        for (ConsentRecordEntity c : em.createQuery(
                        "select c from ConsentRecordEntity c where c.user.id = :u order by c.acceptedAt",
                        ConsentRecordEntity.class)
                .setParameter("u", user.getId()).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", c.getVersion());
            row.put("acceptedAt", text(c.getAcceptedAt()));
            consents.add(row);
        }
        result.put("consents", consents);
        return result;
    }

    // ---------------------------------------------------------- diário de humor

    public List<Map<String, Object>> moodDiary(String tenant, UserEntity user, UUID patientId) {
        if (patientId == null || patientId.equals(user.getId())) {
            dao.defineSchema(tenant);
            return moodRows(user.getId(), 90);
        }
        accessiblePatient(tenant, user, patientId);
        return moodRows(patientId, 90);
    }

    public void saveMood(String tenant, UserEntity user, LocalDate date, int mood, String note) {
        saveMood(tenant, user, date, mood, note, null, null);
    }

    /** Registro do dia: humor e, se a pessoa quiser, quanto e como dormiu. */
    public void saveMood(String tenant, UserEntity user, LocalDate date, int mood, String note,
                         BigDecimal sleepHours, Integer sleepQuality) {
        if (sleepHours != null && (sleepHours.signum() < 0 || sleepHours.compareTo(BigDecimal.valueOf(24)) > 0)) {
            throw core.error.BusinessException.invalid("As horas de sono vão de 0 a 24.");
        }
        if (sleepQuality != null && (sleepQuality < 1 || sleepQuality > 5)) {
            throw core.error.BusinessException.invalid("A qualidade do sono vai de 1 a 5.");
        }
        if (user.getUserType() != UserType.PACIENTE) {
            throw new SecurityException("O diário de humor é preenchido pelo paciente.");
        }
        if (mood < 1 || mood > 5) throw core.error.BusinessException.invalid("O humor vai de 1 a 5.");
        dao.defineSchema(tenant);
        LocalDate day = date == null ? LocalDate.now() : date;
        if (day.isAfter(LocalDate.now())) throw core.error.BusinessException.invalid("Não é possível registrar data futura.");

        MoodEntryEntity entry = em.createQuery(
                        "select m from MoodEntryEntity m where m.patient.id = :p and m.entryDate = :d",
                        MoodEntryEntity.class)
                .setParameter("p", user.getId()).setParameter("d", day)
                .getResultStream().findFirst().orElseGet(MoodEntryEntity::new);
        entry.setPatient(em.getReference(UserEntity.class, user.getId()));
        entry.setEntryDate(day);
        entry.setMood(mood);
        entry.setNote(note);
        entry.setSleepHours(sleepHours);
        entry.setSleepQuality(sleepQuality);
        entry.setCreatedAt(LocalDateTime.now());
        em.merge(entry);
    }

    // ------------------------------------------------------------------ painel

    /** Resumo da tela inicial: o dia de hoje e as pendências de cada perfil. */
    public Map<String, Object> dashboard(String tenant, UserEntity user) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> week = agenda(tenant, user, today, today.plusDays(7));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", week.stream().filter(i -> today.toString().equals(i.get("date"))).toList());
        result.put("upcoming", week.stream()
                .filter(i -> !today.toString().equals(i.get("date")))
                .filter(i -> SessionStatus.AGENDADA.name().equals(i.get("status")))
                .limit(8).toList());

        if (user.getUserType() == UserType.PACIENTE) {
            List<Map<String, Object>> past = agenda(tenant, user, today.minusDays(30), today);
            result.put("pendingRatings", past.stream()
                    .filter(i -> SessionStatus.REALIZADA.name().equals(i.get("status")))
                    .filter(i -> !Boolean.TRUE.equals(i.get("rated")))
                    .toList());
            result.putAll(care.patientSummary(tenant, user));
            result.put("pendingForms", instruments.pendingFor(tenant, user));
        } else {
            result.put("unconfirmed", week.stream()
                    .filter(i -> SessionStatus.AGENDADA.name().equals(i.get("status")))
                    .filter(i -> i.get("confirmedAt") == null).count());
            List<Map<String, Object>> past = agenda(tenant, user, today.minusDays(30), today.minusDays(1));
            result.put("toClose", past.stream()
                    .filter(i -> SessionStatus.AGENDADA.name().equals(i.get("status"))).toList());
            result.put("formAlerts", instruments.recentAlerts(tenant, user));
            Map<String, Object> finance = finance(tenant, user, null, null);
            result.put("receivedThisMonth", finance.get("received"));
            result.put("outstanding", finance.get("outstanding"));
            result.put("debtors", ((List<?>) finance.get("debtors")).size());
        }
        return result;
    }

    // --------------------------------------------------------------- auxiliares

    private List<Map<String, Object>> moodRows(UUID patientId, int days) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MoodEntryEntity m : em.createQuery(
                        "select m from MoodEntryEntity m where m.patient.id = :p and m.entryDate >= :since "
                                + "order by m.entryDate desc", MoodEntryEntity.class)
                .setParameter("p", patientId).setParameter("since", LocalDate.now().minusDays(days))
                .getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", text(m.getEntryDate()));
            row.put("mood", m.getMood());
            row.put("note", m.getNote());
            row.put("sleepHours", m.getSleepHours());
            row.put("sleepQuality", m.getSleepQuality());
            rows.add(row);
        }
        return rows;
    }

    private PatientRecordEntity findRecord(UUID patientId, UUID professionalId) {
        return em.createQuery("select r from PatientRecordEntity r where r.patient.id = :p and r.professionalId = :pro",
                        PatientRecordEntity.class)
                .setParameter("p", patientId).setParameter("pro", professionalId)
                .getResultStream().findFirst().orElse(null);
    }

    private static String scope(UserEntity user, String packageAlias) {
        return switch (user.getUserType()) {
            case ADMINISTRADOR -> "";
            case PROFISSIONAL -> " and " + packageAlias + ".professionalId = :me";
            case PACIENTE -> " and " + packageAlias + ".patient.id = :me";
        };
    }

    private static void bindScope(TypedQuery<?> query, UserEntity user) {
        if (user.getUserType() != UserType.ADMINISTRADOR) query.setParameter("me", user.getId());
    }

    private static void requireStaff(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) {
            throw new SecurityException("Ação restrita ao profissional.");
        }
    }

    private static void requirePackageAccess(UserEntity user, SessionPackageEntity pkg) {
        boolean allowed = switch (user.getUserType()) {
            case ADMINISTRADOR -> true;
            case PROFISSIONAL -> user.getId().equals(pkg.getProfessionalId());
            case PACIENTE -> pkg.getPatient() != null && user.getId().equals(pkg.getPatient().getId());
        };
        if (!allowed) throw new SecurityException("Você não tem acesso a este registro.");
    }

    private SessionEntity ownedSession(String tenant, UserEntity user, UUID sessionId) {
        dao.defineSchema(tenant);
        SessionEntity session = em.find(SessionEntity.class, sessionId);
        if (session == null || session.getSessionPackage() == null) {
            throw core.error.BusinessException.invalid("Sessão não encontrada.");
        }
        requirePackageAccess(user, session.getSessionPackage());
        return session;
    }

    /**
     * O prontuário é de quem atende. O administrador vê tudo; o profissional, só os pacientes
     * associados a ele; o próprio paciente não lê o prontuário por aqui.
     */
    private UserEntity accessiblePatient(String tenant, UserEntity user, UUID patientId) {
        requireStaff(user);
        dao.defineSchema(tenant);
        UserEntity patient = em.find(UserEntity.class, patientId);
        if (patient == null) throw core.error.BusinessException.invalid("Paciente não encontrado.");
        access.requireAssociated(tenant, user, patient);
        return patient;
    }

    private Map<String, Object> patientCard(UserEntity patient) {
        Map<String, Object> card = new LinkedHashMap<>();
        if (patient == null) return card;
        card.put("id", patient.getId());
        card.put("name", fullName(patient));
        card.put("cpf", patient.getCpf());
        card.put("phoneNumber", patient.getPhoneNumber());
        card.put("birthDate", text(patient.getBirthDate()));
        card.put("guardianName", patient.getGuardianName());
        return card;
    }

    private Map<String, Object> professionalCard(UUID professionalId) {
        Map<String, Object> card = new LinkedHashMap<>();
        UserEntity professional = professionalId == null ? null : em.find(UserEntity.class, professionalId);
        if (professional == null) return card;
        card.put("id", professional.getId());
        card.put("name", fullName(professional));
        card.put("cpf", professional.getCpf());
        card.put("crp", professional.getCrp());
        card.put("address", professional.getAddress());
        card.put("phoneNumber", professional.getPhoneNumber());
        return card;
    }

    private static SessionStatus statusOf(SessionEntity session) {
        if (session.getStatus() != null) return session.getStatus();
        return session.getAttendedAt() != null ? SessionStatus.REALIZADA : SessionStatus.AGENDADA;
    }

    private static String fullName(UserEntity user) {
        if (user == null) return null;
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String text(Object temporal) {
        return temporal == null ? null : temporal.toString();
    }
}
