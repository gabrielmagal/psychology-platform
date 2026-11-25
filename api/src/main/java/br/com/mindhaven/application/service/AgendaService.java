package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.ScheduleBlockEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.WorkingHoursEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operações da agenda além de agendar e marcar presença: remarcar, cancelamento pelo
 * paciente, evolução, sessão avulsa, horário de atendimento e bloqueios.
 */
@ApplicationScoped
@Transactional
public class AgendaService {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    AgendaRules rules;

    @Inject
    ClinicService clinic;

    @Inject
    PatientAccess access;

    /** Antecedência mínima para o paciente remarcar ou cancelar sozinho (a mesma do contrato). */
    @ConfigProperty(name = "clinic.patient-change-hours", defaultValue = "24")
    int patientChangeHours;

    // ------------------------------------------------------ remarcar e cancelar

    public void reschedule(String tenant, UserEntity user, UUID sessionId, LocalDate date, LocalTime time) {
        if (date == null || time == null) throw BusinessException.invalid("Informe a nova data e o novo horário.");
        SessionEntity session = ownedSession(tenant, user, sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (user.getUserType() == UserType.PACIENTE) {
            requirePatientWindow(session, now, "remarcar");
            if (LocalDateTime.of(date, time).isBefore(now.plusHours(patientChangeHours))) {
                throw BusinessException.conflict("Escolha um horário com pelo menos " + patientChangeHours
                        + " horas de antecedência.");
            }
        }
        rules.check(tenant, session.getSessionPackage().getProfessionalId(), date, time,
                session.getDurationMinutes(), session.getId());
        session.setDateSession(date);
        session.setStartTime(time);
        // Data nova: a confirmação e o lembrete valiam para a data antiga.
        session.setConfirmedAt(null);
        session.setRemindedAt(null);
        if (session.getStatus() != SessionStatus.AGENDADA) {
            session.setStatus(SessionStatus.AGENDADA);
            session.setAttendedAt(null);
        }
    }

    /** O paciente cancela a própria sessão, respeitando a antecedência mínima. */
    public void patientCancel(String tenant, UserEntity user, UUID sessionId) {
        if (user.getUserType() != UserType.PACIENTE) {
            throw new SecurityException("Use a agenda para cancelar a sessão.");
        }
        SessionEntity session = ownedSession(tenant, user, sessionId);
        requirePatientWindow(session, LocalDateTime.now(), "cancelar");
        session.setStatus(SessionStatus.CANCELADA);
    }

    public List<String> freeSlots(String tenant, UserEntity user, UUID sessionId, LocalDate date) {
        SessionEntity session = ownedSession(tenant, user, sessionId);
        LocalDateTime notBefore = user.getUserType() == UserType.PACIENTE
                ? LocalDateTime.now().plusHours(patientChangeHours) : LocalDateTime.now();
        return rules.freeSlots(tenant, session.getSessionPackage().getProfessionalId(), date,
                session.getDurationMinutes(), session.getId(), notBefore);
    }

    private void requirePatientWindow(SessionEntity session, LocalDateTime now, String action) {
        if (session.getStatus() != null && session.getStatus() != SessionStatus.AGENDADA) {
            throw BusinessException.conflict("Só é possível " + action + " uma sessão agendada.");
        }
        if (session.getDateSession() == null || session.getStartTime() == null) return;
        LocalDateTime startsAt = LocalDateTime.of(session.getDateSession(), session.getStartTime());
        if (startsAt.isBefore(now.plusHours(patientChangeHours))) {
            throw BusinessException.conflict("Faltam menos de " + patientChangeHours + " horas para a sessão. Para "
                    + action + " agora, fale diretamente com o profissional.");
        }
    }

    // ---------------------------------------------------------------- evolução

    /**
     * Registro clínico da sessão. O administrador lê (ele responde pela clínica), mas quem
     * escreve é só o profissional que atendeu. As notas privadas são só de quem as escreveu.
     */
    public Map<String, Object> record(String tenant, UserEntity user, UUID sessionId) {
        requireStaff(user);
        SessionEntity session = ownedSession(tenant, user, sessionId);
        boolean author = isAuthor(user, session);
        Map<String, Object> result = SessionRecord.read(session);
        if (author) result.put("privateNotes", session.getPrivateNotes());
        result.put("canEdit", author && session.getRecordSignedAt() == null);
        result.put("canAddAddendum", author && session.getRecordSignedAt() != null);
        return result;
    }

    public Map<String, Object> saveRecord(String tenant, UserEntity user, UUID sessionId, Map<String, Object> body) {
        SessionRecord.write(authoredSession(tenant, user, sessionId), body == null ? Map.of() : body);
        return record(tenant, user, sessionId);
    }

    public Map<String, Object> signRecord(String tenant, UserEntity user, UUID sessionId) {
        SessionRecord.sign(authoredSession(tenant, user, sessionId), LocalDateTime.now());
        return record(tenant, user, sessionId);
    }

    public Map<String, Object> addAddendum(String tenant, UserEntity user, UUID sessionId, String text) {
        SessionRecord.addendum(authoredSession(tenant, user, sessionId), text, LocalDateTime.now());
        return record(tenant, user, sessionId);
    }

    /** Atalho antigo: grava só o campo de evolução, mantendo o resto do registro. */
    public void saveEvolution(String tenant, UserEntity user, UUID sessionId, String evolution) {
        SessionEntity session = authoredSession(tenant, user, sessionId);
        Map<String, Object> current = new LinkedHashMap<>(SessionRecord.read(session));
        current.put("evolution", evolution);
        SessionRecord.write(session, current);
    }

    private SessionEntity authoredSession(String tenant, UserEntity user, UUID sessionId) {
        SessionEntity session = ownedSession(tenant, user, sessionId);
        if (!isAuthor(user, session)) {
            throw new SecurityException("O registro da sessão é escrito pelo profissional que atendeu.");
        }
        return session;
    }

    private static boolean isAuthor(UserEntity user, SessionEntity session) {
        return user.getUserType() == UserType.PROFISSIONAL
                && user.getId().equals(session.getSessionPackage().getProfessionalId());
    }

    // ----------------------------------------------------------- sessão avulsa

    /** Agenda uma sessão sem pacote: cria por baixo um pacote de uma sessão só, com o valor dela. */
    public List<UUID> scheduleSingle(String tenant, UserEntity user, UUID patientId, BigDecimal price, LocalDate date,
                                     LocalTime time, Integer durationMinutes, SessionModality modality,
                                     String meetingLink) {
        if (user.getUserType() != UserType.PROFISSIONAL) {
            throw new SecurityException("A sessão avulsa é agendada pelo profissional que vai atender.");
        }
        if (patientId == null) throw BusinessException.invalid("Escolha o paciente.");
        if (date == null || time == null) throw BusinessException.invalid("Informe a data e o horário.");
        if (price != null && price.signum() < 0) throw BusinessException.invalid("O valor não pode ser negativo.");
        dao.defineSchema(tenant);
        UserEntity patient = em.find(UserEntity.class, patientId);
        if (patient == null || patient.getUserType() != UserType.PACIENTE) {
            throw BusinessException.invalid("Paciente não encontrado.");
        }
        access.requireAssociated(tenant, user, patient);
        // Confere a agenda antes de criar o pacote, para não deixar pacote órfão se o horário não servir.
        rules.check(tenant, user.getId(), date, time, durationMinutes, null);

        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(user.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Sessão avulsa de " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        pkg.setTotalSessions(1);
        pkg.setPrice(price);
        pkg.setSingleSession(true);
        em.persist(pkg);
        em.flush();
        return clinic.schedule(tenant, user, pkg.getId(), date, time, durationMinutes, modality, meetingLink,
                "Sessão avulsa", 1);
    }

    // ------------------------------------------- horário de atendimento e bloqueios

    public Map<String, Object> settings(String tenant, UserEntity user, LocalDate from, LocalDate to) {
        requireStaff(user);
        dao.defineSchema(tenant);
        List<Map<String, Object>> hours = new ArrayList<>();
        for (WorkingHoursEntity h : rules.workingHours(user.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekday", h.getWeekday());
            row.put("startTime", h.getStartTime().format(HHMM));
            row.put("endTime", h.getEndTime().format(HHMM));
            hours.add(row);
        }
        LocalDateTime start = (from == null ? LocalDate.now().minusDays(1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now().plusYears(1) : to.plusDays(1)).atStartOfDay();
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (ScheduleBlockEntity b : em.createQuery(
                        "select b from ScheduleBlockEntity b where b.ownerId = :o and b.endAt > :start and b.startAt < :end "
                                + "order by b.startAt", ScheduleBlockEntity.class)
                .setParameter("o", user.getId()).setParameter("start", start).setParameter("end", end).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("startAt", b.getStartAt().toString());
            row.put("endAt", b.getEndAt().toString());
            row.put("reason", b.getReason());
            blocks.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workingHours", hours);
        result.put("blocks", blocks);
        result.put("patientChangeHours", patientChangeHours);
        return result;
    }

    /** Substitui o horário de atendimento inteiro. Lista vazia = sem restrição de horário. */
    public void saveWorkingHours(String tenant, UserEntity user, List<Map<String, Object>> intervals) {
        requireStaff(user);
        dao.defineSchema(tenant);
        List<WorkingHoursEntity> parsed = new ArrayList<>();
        for (Map<String, Object> item : intervals == null ? List.<Map<String, Object>>of() : intervals) {
            int weekday;
            LocalTime start;
            LocalTime end;
            try {
                weekday = Integer.parseInt(String.valueOf(item.get("weekday")));
                start = LocalTime.parse(String.valueOf(item.get("startTime")));
                end = LocalTime.parse(String.valueOf(item.get("endTime")));
            } catch (RuntimeException e) {
                throw BusinessException.invalid("Horário de atendimento em formato inválido.");
            }
            if (weekday < 1 || weekday > 7) throw BusinessException.invalid("Dia da semana inválido.");
            if (!end.isAfter(start)) throw BusinessException.invalid("O fim do atendimento deve ser depois do início.");
            WorkingHoursEntity h = new WorkingHoursEntity();
            h.setOwnerId(user.getId());
            h.setWeekday(weekday);
            h.setStartTime(start);
            h.setEndTime(end);
            parsed.add(h);
        }
        em.createQuery("delete from WorkingHoursEntity h where h.ownerId = :o").setParameter("o", user.getId()).executeUpdate();
        parsed.forEach(em::persist);
    }

    public Map<String, Object> addBlock(String tenant, UserEntity user, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        requireStaff(user);
        if (startAt == null || endAt == null) throw BusinessException.invalid("Informe o início e o fim do bloqueio.");
        if (!endAt.isAfter(startAt)) throw BusinessException.invalid("O fim do bloqueio deve ser depois do início.");
        dao.defineSchema(tenant);
        long sessions = em.createQuery("select count(s) from SessionEntity s join s.sessionPackage p "
                        + "where p.professionalId = :o and s.dateSession between :d1 and :d2 "
                        + "and (s.status is null or s.status = :st)", Long.class)
                .setParameter("o", user.getId()).setParameter("d1", startAt.toLocalDate())
                .setParameter("d2", endAt.toLocalDate()).setParameter("st", SessionStatus.AGENDADA).getSingleResult();
        ScheduleBlockEntity block = new ScheduleBlockEntity();
        block.setOwnerId(user.getId());
        block.setStartAt(startAt);
        block.setEndAt(endAt);
        block.setReason(reason);
        em.persist(block);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", block.getId());
        // Sessões já agendadas no período continuam lá: o aviso é para o profissional remarcar.
        result.put("sessionsInPeriod", sessions);
        return result;
    }

    public void removeBlock(String tenant, UserEntity user, UUID blockId) {
        requireStaff(user);
        dao.defineSchema(tenant);
        ScheduleBlockEntity block = em.find(ScheduleBlockEntity.class, blockId);
        if (block == null) return;
        if (!block.getOwnerId().equals(user.getId())) throw new SecurityException("Este bloqueio é de outro profissional.");
        em.remove(block);
    }

    // --------------------------------------------------------------- auxiliares

    private SessionEntity ownedSession(String tenant, UserEntity user, UUID sessionId) {
        dao.defineSchema(tenant);
        SessionEntity session = em.find(SessionEntity.class, sessionId);
        if (session == null || session.getSessionPackage() == null) {
            throw BusinessException.invalid("Sessão não encontrada.");
        }
        SessionPackageEntity pkg = session.getSessionPackage();
        boolean allowed = switch (user.getUserType()) {
            case ADMINISTRADOR -> true;
            case PROFISSIONAL -> user.getId().equals(pkg.getProfessionalId());
            case PACIENTE -> pkg.getPatient() != null && user.getId().equals(pkg.getPatient().getId());
        };
        if (!allowed) throw new SecurityException("Você não tem acesso a esta sessão.");
        return session;
    }

    private static void requireStaff(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita ao profissional.");
    }
}
