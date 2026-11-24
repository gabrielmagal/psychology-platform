package br.com.mindhaven.application.reminder;

import core.security.TenantClientRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/** Varre a agenda de cada tenant de tempos em tempos e dispara os lembretes devidos. */
@ApplicationScoped
public class ReminderScheduler {
    private static final Logger LOG = Logger.getLogger(ReminderScheduler.class);

    @Inject
    ReminderService reminderService;

    @Inject
    TenantClientRegistry tenants;

    @ConfigProperty(name = "reminder.enabled", defaultValue = "false")
    boolean enabled;

    // SKIP: se uma passada demorar mais que o intervalo, a seguinte é pulada em vez de
    // rodar em paralelo e mandar o mesmo lembrete duas vezes.
    @Scheduled(every = "{reminder.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "session-reminders")
    void run() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now();
        for (String tenant : tenants.tenants()) {
            try {
                int sent = reminderService.sendDueReminders(tenant, now);
                if (sent > 0) LOG.infof("Lembretes enviados no tenant %s: %d", tenant, sent);
            } catch (Exception e) {
                // Um tenant com problema (schema, banco) não pode parar os lembretes dos outros.
                LOG.errorf(e, "Falha ao enviar lembretes do tenant %s", tenant);
            }
        }
    }
}
