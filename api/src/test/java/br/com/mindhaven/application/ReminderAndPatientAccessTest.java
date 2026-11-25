package br.com.mindhaven.application;

import core.error.BusinessException;
import br.com.mindhaven.application.reminder.ReminderMessage;
import br.com.mindhaven.application.reminder.ReminderService;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.application.usecase.interfaces.PaymentUseCase;
import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lembrete automático por e-mail e acesso do paciente aos próprios pacotes e pagamentos. */
@QuarkusTest
class ReminderAndPatientAccessTest {

    @Inject
    GenericDao dao;

    @Inject
    ClinicService clinic;

    @Inject
    ReminderService reminders;

    @Inject
    MockMailbox mailbox;

    @Inject
    SessionPackageUseCase packages;

    @Inject
    PaymentUseCase payments;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private UserEntity newUser(String tenant, String cpf, String name, UserType type) {
        return dao.update(tenant, UserEntity.builder()
                .cpf(cpf)
                .firstName(name)
                .lastName("Teste")
                .email(name.toLowerCase() + "@exemplo.com")
                .phoneNumber("11" + cpf)
                .birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .userType(type)
                .build());
    }

    private SessionPackageEntity newPackage(String tenant, UserEntity professional, UserEntity patient) {
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Pacote mensal");
        pkg.setPrice(new BigDecimal("400.00"));
        return dao.update(tenant, pkg);
    }

    private UUID scheduleAt(String tenant, UserEntity professional, SessionPackageEntity pkg, LocalDateTime when) {
        return clinic.schedule(tenant, professional, pkg.getId(), when.toLocalDate(),
                when.toLocalTime().withSecond(0).withNano(0), 50, SessionModality.ONLINE,
                "https://meet.exemplo/sala", "Sessão", 1).getFirst();
    }

    @Test
    @DisplayName("lembrete: só a sessão dentro da janela, uma única vez, sem dado clínico")
    void emailReminder() {
        String tenant = "clinica_lembrete";
        UserEntity professional = newUser(tenant, "60000000001", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "60000000002", "Paciente", UserType.PACIENTE);
        SessionPackageEntity pkg = newPackage(tenant, professional, patient);

        // "Agora" fixo ao meio-dia: a janela de 24h não depende da hora em que o teste roda.
        LocalDateTime now = LocalDate.now().plusDays(10).atTime(12, 0);
        UUID soon = scheduleAt(tenant, professional, pkg, now.plusHours(3));
        scheduleAt(tenant, professional, pkg, now.plusHours(30));            // fora da janela
        UUID cancelled = scheduleAt(tenant, professional, pkg, now.plusHours(5));
        clinic.changeStatus(tenant, professional, cancelled, SessionStatus.CANCELADA);

        assertEquals(1, reminders.sendDueReminders(tenant, now));

        List<Mail> sent = mailbox.getMailsSentTo("paciente@exemplo.com");
        assertEquals(1, sent.size());
        Mail mail = sent.getFirst();
        assertTrue(mail.getSubject().startsWith("Lembrete da sua sessão em "));
        assertTrue(mail.getText().contains("Dona Teste"));
        assertTrue(mail.getText().contains("https://meet.exemplo/sala"));
        assertTrue(mail.getText().contains("/" + tenant + "/agenda"));
        assertFalse(mail.getText().contains("Pacote mensal"), "o e-mail não leva dado do atendimento");

        // Segunda passada: a sessão já foi lembrada.
        assertEquals(0, reminders.sendDueReminders(tenant, now));
        assertEquals(1, mailbox.getTotalMessagesSent());

        var agendaItem = clinic.agenda(tenant, professional, now.toLocalDate(), now.toLocalDate()).stream()
                .filter(i -> soon.equals(i.get("id"))).findFirst().orElseThrow();
        assertNotNull(agendaItem.get("remindedAt"));

        // Passada a hora da sessão, não se manda lembrete atrasado.
        assertEquals(0, reminders.sendDueReminders(tenant, now.plusHours(40)));
    }

    @Test
    @DisplayName("paciente vê e consulta só os próprios pacotes; não cria nem altera")
    void patientSeesOnlyOwnPackages() {
        String tenant = "clinica_pacote_paciente";
        UserEntity professional = newUser(tenant, "60000000011", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "60000000012", "Paciente", UserType.PACIENTE);
        UserEntity other = newUser(tenant, "60000000013", "Outro", UserType.PACIENTE);
        SessionPackageEntity mine = newPackage(tenant, professional, patient);
        SessionPackageEntity theirs = newPackage(tenant, professional, other);

        Filter filter = new Filter();
        filter.setFilterParams(new ArrayList<>());
        List<SessionPackageEntity> listed = packages.filteredFindPaged(tenant, patient, filter, 0, 50);
        assertEquals(1, listed.size());
        assertEquals(mine.getId(), listed.getFirst().getId());

        Filter countFilter = new Filter();
        countFilter.setFilterParams(new ArrayList<>());
        assertEquals(1, packages.countFiltered(tenant, patient, countFilter));

        assertEquals(mine.getId(), packages.findById(tenant, patient, mine.getId()).getId());
        assertThrows(SecurityException.class, () -> packages.findById(tenant, patient, theirs.getId()));
        assertThrows(SecurityException.class, () -> packages.update(tenant, patient, mine));
        assertThrows(SecurityException.class, () -> packages.delete(tenant, patient, mine.getId()));

        payment(tenant, mine, "150.00");
        payment(tenant, theirs, "90.00");
        assertEquals(0, new BigDecimal("150.00")
                .compareTo(payments.sumPaidAmountBySessionPackage(tenant, patient, mine.getId())));
        // Pedir os pagamentos do pacote de outro paciente não devolve nada.
        assertTrue(payments.findPaidBySessionPackage(tenant, patient, theirs.getId()).isEmpty());
    }

    @Inject
    br.com.mindhaven.application.usecase.interfaces.SessionUseCase sessions;

    @Test
    @DisplayName("editar pacote ou sessão não apaga sessões, pagamentos nem anotações")
    void editingKeepsChildren() {
        String tenant = "clinica_edicao";
        UserEntity professional = newUser(tenant, "60000000021", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "60000000022", "Paciente", UserType.PACIENTE);
        SessionPackageEntity pkg = newPackage(tenant, professional, patient);
        LocalDateTime when = LocalDate.of(2027, 6, 7).atTime(10, 0);
        UUID sessionId = scheduleAt(tenant, professional, pkg, when);
        scheduleAt(tenant, professional, pkg, when.plusDays(7));
        payment(tenant, pkg, "100.00");

        // O que chega do formulário: só os campos do pacote, com as listas vazias.
        SessionPackageEntity fromForm = new SessionPackageEntity();
        fromForm.setId(pkg.getId());
        fromForm.setPackageTitle("Pacote renomeado");
        fromForm.setTotalSessions(8);
        fromForm.setPrice(new BigDecimal("500.00"));
        packages.update(tenant, professional, fromForm);

        SessionPackageEntity reloaded = packages.findById(tenant, professional, pkg.getId());
        assertEquals("Pacote renomeado", reloaded.getPackageTitle());
        assertEquals(patient.getId(), reloaded.getPatient().getId(), "o paciente do pacote não muda na edição");
        assertEquals(2, clinic.timeline(tenant, professional, patient.getId()).size(), "as sessões continuam lá");
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(payments.sumPaidAmountBySessionPackage(tenant, professional, pkg.getId())));

        br.com.mindhaven.domain.entity.AnnotationEntity note = new br.com.mindhaven.domain.entity.AnnotationEntity();
        note.setMainFeeling("Alívio");
        note.setSignificantEvents("Semana de provas");
        note.setCurrentPhase("Inicial");
        note.setDominantThought("Vou conseguir");
        note.setIntervention("Psicoeducação");
        note.setSession(sessions.findById(tenant, professional, sessionId));
        dao.update(tenant, note);

        br.com.mindhaven.domain.entity.SessionEntity sessionForm = new br.com.mindhaven.domain.entity.SessionEntity();
        sessionForm.setId(sessionId);
        sessionForm.setTitle("Sessão editada");
        sessionForm.setDateSession(when.toLocalDate());
        sessionForm.setStartTime(when.toLocalTime());
        sessions.update(tenant, professional, sessionForm);

        var edited = clinic.timeline(tenant, professional, patient.getId()).stream()
                .filter(i -> sessionId.equals(i.get("id"))).findFirst().orElseThrow();
        assertEquals("Sessão editada", edited.get("title"));
        // A anotação no formato antigo não se perde na edição: aparece dentro do registro novo.
        assertEquals("Psicoeducação", edited.get("interventions"));
        assertEquals("Semana de provas", edited.get("demand"));
        assertTrue(edited.get("observations").toString().contains("Sentimento principal: Alívio"));

        // Mover a sessão para cima de outra continua bloqueado na edição.
        sessionForm.setDateSession(when.plusDays(7).toLocalDate());
        assertThrows(BusinessException.class, () -> sessions.update(tenant, professional, sessionForm));
    }

    private void payment(String tenant, SessionPackageEntity pkg, String amount) {
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setAmount(new BigDecimal(amount));
        payment.setPaid(true);
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentMethod(PaymentMethod.PIX);
        payment.setSessionPackage(pkg);
        dao.update(tenant, payment);
    }

    @Test
    @DisplayName("WhatsApp: telefone brasileiro ganha DDI e o corpo segue o formato de modelo")
    void whatsappStructure() {
        var channel = new br.com.mindhaven.application.reminder.WhatsAppChannelProbe();
        ReminderMessage message = new ReminderMessage("Ana", "ana@exemplo.com", "(11) 98888-7777", "Dona Teste",
                LocalDateTime.of(2027, 3, 1, 14, 0), false, null, "http://localhost:3000/t/agenda");

        assertEquals("5511988887777", channel.number("(11) 98888-7777"));
        assertEquals(null, channel.number("123"));

        var payload = channel.payload(message);
        assertEquals("whatsapp", payload.getString("messaging_product"));
        assertEquals("5511988887777", payload.getString("to"));
        assertEquals("template", payload.getString("type"));
        var parameters = payload.getJsonObject("template").getJsonArray("components")
                .getJsonObject(0).getJsonArray("parameters");
        assertEquals(3, parameters.size());
        assertEquals("01/03/2027 às 14:00", parameters.getJsonObject(1).getString("text"));
    }
}
