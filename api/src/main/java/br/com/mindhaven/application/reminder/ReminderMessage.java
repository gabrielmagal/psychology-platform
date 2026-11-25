package br.com.mindhaven.application.reminder;

import java.time.LocalDateTime;

/** Tudo o que um canal precisa para avisar o paciente de uma sessão. */
public record ReminderMessage(
        String patientFirstName,
        String patientEmail,
        String patientPhone,
        String professionalName,
        LocalDateTime startsAt,
        boolean online,
        String meetingLink,
        String agendaUrl
) {
}
