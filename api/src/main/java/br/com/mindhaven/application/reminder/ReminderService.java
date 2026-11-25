package br.com.mindhaven.application.reminder;

import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Envia os lembretes das sessões que estão para começar.
 *
 * <p>Uma sessão entra quando está agendada, tem horário, ainda não foi lembrada e começa
 * dentro da janela de antecedência. Depois que pelo menos um canal entrega, a sessão é
 * marcada ({@code remindedAt}) e não é lembrada de novo — o mesmo campo que o botão manual
 * "Lembrar por WhatsApp" preenche, então os dois não se duplicam.
 */
@ApplicationScoped
public class ReminderService {
    private static final Logger LOG = Logger.getLogger(ReminderService.class);

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    Instance<ReminderChannel> channels;

    @ConfigProperty(name = "reminder.hours-before", defaultValue = "24")
    int hoursBefore;

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:3000")
    String frontendUrl;

    @ConfigProperty(name = "reminder.enabled", defaultValue = "false")
    boolean schedulerEnabled;

    @ConfigProperty(name = "reminder.every", defaultValue = "15m")
    String every;

    /** Como os lembretes automáticos estão configurados, para a tela da administração. */
    public java.util.Map<String, Object> status() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (ReminderChannel channel : channels) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("name", channel.name());
            row.put("enabled", channel.isEnabled());
            list.add(row);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", schedulerEnabled);
        result.put("hoursBefore", hoursBefore);
        result.put("every", every);
        result.put("channels", list);
        return result;
    }

    /**
     * Manda um lembrete de exemplo para o e-mail e o telefone informados, por todos os canais
     * ligados. Serve para conferir a configuração sem esperar uma sessão de verdade.
     */
    public java.util.List<java.util.Map<String, Object>> sendTest(String firstName, String email, String phone) {
        ReminderMessage message = new ReminderMessage(firstName, email, phone, "Profissional de teste",
                LocalDateTime.now().plusHours(hoursBefore), false, null, frontendUrl);
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (ReminderChannel channel : channels) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("name", channel.name());
            if (!channel.isEnabled()) {
                row.put("result", "DESLIGADO");
            } else if (!channel.canReach(message)) {
                row.put("result", "SEM_CONTATO");
            } else {
                try {
                    channel.send(message);
                    row.put("result", "ENVIADO");
                } catch (Exception e) {
                    row.put("result", "FALHOU");
                    row.put("detail", e.getMessage());
                }
            }
            results.add(row);
        }
        return results;
    }

    /** @return quantas sessões foram lembradas nesta passada */
    @Transactional
    public int sendDueReminders(String tenant, LocalDateTime now) {
        List<ReminderChannel> active = channels.stream().filter(ReminderChannel::isEnabled).toList();
        if (active.isEmpty()) return 0;

        dao.defineSchema(tenant);
        LocalDateTime limit = now.plusHours(hoursBefore);
        List<SessionEntity> candidates = em.createQuery(
                        "select s from SessionEntity s join fetch s.sessionPackage p join fetch p.patient "
                                + "where s.remindedAt is null and s.startTime is not null "
                                + "and (s.status is null or s.status = :scheduled) "
                                + "and s.dateSession between :from and :to", SessionEntity.class)
                .setParameter("scheduled", SessionStatus.AGENDADA)
                .setParameter("from", now.toLocalDate())
                .setParameter("to", limit.toLocalDate())
                .getResultList();

        int reminded = 0;
        for (SessionEntity session : candidates) {
            LocalDateTime startsAt = LocalDateTime.of(session.getDateSession(), session.getStartTime());
            if (!startsAt.isAfter(now) || startsAt.isAfter(limit)) continue;

            ReminderMessage message = messageFor(tenant, session, startsAt);
            boolean delivered = false;
            for (ReminderChannel channel : active) {
                if (!channel.canReach(message)) continue;
                try {
                    channel.send(message);
                    delivered = true;
                } catch (Exception e) {
                    // Um canal fora do ar não impede os outros, nem as outras sessões.
                    LOG.warnf(e, "Lembrete por %s falhou (tenant %s, sessão %s)", channel.name(), tenant, session.getId());
                }
            }
            if (delivered) {
                session.setRemindedAt(now);
                reminded++;
            }
        }
        return reminded;
    }

    private ReminderMessage messageFor(String tenant, SessionEntity session, LocalDateTime startsAt) {
        SessionPackageEntity pkg = session.getSessionPackage();
        UserEntity patient = pkg.getPatient();
        UserEntity professional = pkg.getProfessionalId() == null ? null : em.find(UserEntity.class, pkg.getProfessionalId());
        String professionalName = professional == null
                ? "seu profissional"
                : (professional.getFirstName() + " " + professional.getLastName()).trim();
        return new ReminderMessage(
                patient.getFirstName(),
                patient.getEmail(),
                patient.getPhoneNumber(),
                professionalName,
                startsAt,
                session.getModality() == SessionModality.ONLINE,
                session.getMeetingLink(),
                frontendUrl.replaceAll("/+$", "") + "/" + tenant + "/agenda");
    }
}
