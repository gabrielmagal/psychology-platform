package br.com.mindhaven.application.reminder;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailReminderChannel implements ReminderChannel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "reminder.email.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public String name() {
        return "e-mail";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean canReach(ReminderMessage message) {
        return message.patientEmail() != null && message.patientEmail().contains("@");
    }

    @Override
    public void send(ReminderMessage message) {
        String when = message.startsAt().format(WHEN);
        StringBuilder body = new StringBuilder()
                .append("Olá, ").append(message.patientFirstName()).append("!\n\n")
                .append("Este é um lembrete da sua sessão com ").append(message.professionalName())
                .append(" em ").append(when).append(".\n\n");
        if (message.online() && message.meetingLink() != null && !message.meetingLink().isBlank()) {
            body.append("O atendimento é online. Link de acesso: ").append(message.meetingLink()).append("\n\n");
        }
        body.append("Confirme sua presença pela agenda: ").append(message.agendaUrl()).append("\n\n")
                .append("Se não puder comparecer, avise com antecedência.\n");

        // Sem dado clínico: o assunto e o corpo trazem só data, hora e o nome do profissional.
        mailer.send(Mail.withText(message.patientEmail(), "Lembrete da sua sessão em " + when, body.toString()));
    }
}
