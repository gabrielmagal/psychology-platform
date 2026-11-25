package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.ScheduleBlockEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.WorkingHoursEntity;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * As regras de ocupação da agenda de um profissional, num lugar só: outra sessão no mesmo
 * horário, horário de atendimento e bloqueios. Vale para todo caminho que cria ou muda uma
 * sessão — a agenda, o formulário de sessões e a remarcação pelo paciente.
 */
@ApplicationScoped
@Transactional
public class AgendaRules {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEKDAYS = {"", "segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    /** Lança {@link BusinessException} (409) se a sessão não cabe na agenda do profissional. */
    public void check(String tenant, UUID professionalId, LocalDate date, LocalTime start, Integer durationMinutes,
                      UUID ignoreSessionId) {
        if (professionalId == null || date == null || start == null) return;
        String problem = problem(tenant, professionalId, date, start, durationMinutes, ignoreSessionId);
        if (problem != null) throw BusinessException.conflict(problem);
    }

    /** Horários livres do profissional no dia, de acordo com o horário de atendimento. */
    public List<String> freeSlots(String tenant, UUID professionalId, LocalDate date, Integer durationMinutes,
                                  UUID ignoreSessionId, LocalDateTime notBefore) {
        dao.defineSchema(tenant);
        int duration = durationMinutes == null ? 50 : durationMinutes;
        List<WorkingHoursEntity> hours = workingHours(professionalId);
        List<int[]> intervals = new ArrayList<>();
        if (hours.isEmpty()) {
            // Sem horário cadastrado, oferece o horário comercial em dias úteis.
            if (date.getDayOfWeek().getValue() <= 5) intervals.add(new int[]{8 * 60, 18 * 60});
        } else {
            for (WorkingHoursEntity h : hours) {
                if (h.getWeekday() == date.getDayOfWeek().getValue()) {
                    intervals.add(new int[]{minutes(h.getStartTime()), minutes(h.getEndTime())});
                }
            }
        }
        List<String> free = new ArrayList<>();
        for (int[] interval : intervals) {
            for (int at = interval[0]; at + duration <= interval[1]; at += duration) {
                LocalTime start = LocalTime.of(at / 60, at % 60);
                if (notBefore != null && LocalDateTime.of(date, start).isBefore(notBefore)) continue;
                if (problem(tenant, professionalId, date, start, duration, ignoreSessionId) == null) {
                    free.add(start.format(HHMM));
                }
            }
        }
        return free;
    }

    private String problem(String tenant, UUID professionalId, LocalDate date, LocalTime start, Integer durationMinutes,
                           UUID ignoreSessionId) {
        dao.defineSchema(tenant);
        int from = minutes(start);
        int to = from + (durationMinutes == null ? 50 : durationMinutes);

        // 1) Horário de atendimento — só vale se o profissional cadastrou algum.
        List<WorkingHoursEntity> hours = workingHours(professionalId);
        if (!hours.isEmpty()) {
            int weekday = date.getDayOfWeek().getValue();
            boolean anyThatDay = false;
            boolean fits = false;
            for (WorkingHoursEntity h : hours) {
                if (h.getWeekday() != weekday) continue;
                anyThatDay = true;
                if (from >= minutes(h.getStartTime()) && to <= minutes(h.getEndTime())) fits = true;
            }
            if (!anyThatDay) return "O profissional não atende de " + WEEKDAYS[weekday] + ".";
            if (!fits) return "Este horário está fora do horário de atendimento do profissional.";
        }

        // 2) Bloqueios (férias, compromissos).
        LocalDateTime startAt = LocalDateTime.of(date, start);
        LocalDateTime endAt = startAt.plusMinutes(to - from);
        List<ScheduleBlockEntity> blocks = em.createQuery(
                        "select b from ScheduleBlockEntity b where b.ownerId = :o and b.startAt < :end and b.endAt > :start",
                        ScheduleBlockEntity.class)
                .setParameter("o", professionalId).setParameter("start", startAt).setParameter("end", endAt)
                .setMaxResults(1).getResultList();
        if (!blocks.isEmpty()) {
            String reason = blocks.getFirst().getReason();
            return "A agenda está bloqueada neste horário" + (reason == null || reason.isBlank() ? "." : " (" + reason + ").");
        }

        // 3) Outra sessão no mesmo horário. Canceladas não ocupam a agenda.
        List<SessionEntity> sameDay = em.createQuery(
                        "select s from SessionEntity s join s.sessionPackage p where p.professionalId = :o "
                                + "and s.dateSession = :d and s.startTime is not null", SessionEntity.class)
                .setParameter("o", professionalId).setParameter("d", date).getResultList();
        for (SessionEntity other : sameDay) {
            if (other.getId().equals(ignoreSessionId) || other.getStatus() == SessionStatus.CANCELADA) continue;
            int otherFrom = minutes(other.getStartTime());
            int otherTo = otherFrom + (other.getDurationMinutes() == null ? 50 : other.getDurationMinutes());
            if (from < otherTo && otherFrom < to) {
                return "Já existe uma sessão neste horário (começa às " + other.getStartTime().format(HHMM) + ").";
            }
        }
        return null;
    }

    public List<WorkingHoursEntity> workingHours(UUID professionalId) {
        return em.createQuery("select h from WorkingHoursEntity h where h.ownerId = :o order by h.weekday, h.startTime",
                WorkingHoursEntity.class).setParameter("o", professionalId).getResultList();
    }

    private static int minutes(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }
}
