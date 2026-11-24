package br.com.mindhaven.application;

import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agenda, prontuário, financeiro, consentimento e diário — com a visibilidade de cada perfil. */
@QuarkusTest
class ClinicServiceTest {

    @Inject
    GenericDao dao;

    @Inject
    ClinicService clinic;

    private record Scenario(String tenant, UserEntity psychologist, UserEntity intruder, UserEntity patient,
                            SessionPackageEntity pkg) {
    }

    private UserEntity newUser(String tenant, String cpf, String name, UserType type) {
        return dao.update(tenant, UserEntity.builder()
                .cpf(cpf)
                .firstName(name)
                .lastName("Teste")
                .email(cpf + "@exemplo.com")
                .phoneNumber("11" + cpf)
                .birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .userType(type)
                .build());
    }

    /** Cada teste usa um tenant (schema) próprio: não há estado compartilhado entre eles. */
    private Scenario scenario(String tenant) {
        UserEntity psychologist = newUser(tenant, "70000000001", "Dona", UserType.PSICOLOGO);
        UserEntity intruder = newUser(tenant, "70000000002", "Intruso", UserType.PSICOLOGO);
        UserEntity patient = newUser(tenant, "70000000003", "Paciente", UserType.PACIENTE);

        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setPsychologistId(psychologist.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Pacote mensal");
        pkg.setTotalSessions(4);
        pkg.setPrice(new BigDecimal("400.00"));
        pkg = dao.update(tenant, pkg);
        return new Scenario(tenant, psychologist, intruder, patient, pkg);
    }

    private List<UUID> schedule(Scenario s, UserEntity who, LocalDate date, String time, int weeks) {
        return clinic.schedule(s.tenant(), who, s.pkg().getId(), date, LocalTime.parse(time), 50,
                SessionModality.ONLINE, "https://meet.exemplo/sala", "Sessão", weeks);
    }

    @Test
    @DisplayName("agenda: repetição semanal, conflito de horário e visibilidade por perfil")
    void agendaFlow() {
        Scenario s = scenario("clinica_agenda");
        LocalDate monday = LocalDate.of(2027, 3, 1);

        List<UUID> created = schedule(s, s.psychologist(), monday, "14:00", 3);
        assertEquals(3, created.size());

        // 14:30 cai dentro da sessão das 14:00–14:50.
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> schedule(s, s.psychologist(), monday, "14:30", 1));
        assertEquals(409, conflict.getStatus());
        // 14:50 começa exatamente quando a outra termina: cabe.
        assertEquals(1, schedule(s, s.psychologist(), monday, "14:50", 1).size());

        assertThrows(SecurityException.class, () -> schedule(s, s.intruder(), monday, "09:00", 1));
        assertThrows(SecurityException.class, () -> schedule(s, s.patient(), monday, "09:00", 1));

        LocalDate end = monday.plusWeeks(3);
        assertEquals(4, clinic.agenda(s.tenant(), s.psychologist(), monday, end).size());
        assertTrue(clinic.agenda(s.tenant(), s.intruder(), monday, end).isEmpty());

        List<Map<String, Object>> patientView = clinic.agenda(s.tenant(), s.patient(), monday, end);
        assertEquals(4, patientView.size());
        assertEquals("Dona Teste", patientView.getFirst().get("psychologistName"));
        assertEquals("14:00", patientView.getFirst().get("startTime"));
        assertFalse(patientView.getFirst().containsKey("patientPhone"));
    }

    @Test
    @DisplayName("sessão: confirmação pelo paciente, situação pelo psicólogo e avaliação")
    void sessionLifecycle() {
        Scenario s = scenario("clinica_ciclo");
        LocalDate day = LocalDate.of(2027, 4, 5);
        UUID sessionId = schedule(s, s.psychologist(), day, "10:00", 1).getFirst();

        assertThrows(BusinessException.class, () -> clinic.rate(s.tenant(), s.patient(), sessionId, 5, "ótima"));
        assertThrows(SecurityException.class, () -> clinic.confirm(s.tenant(), s.intruder(), sessionId));

        clinic.confirm(s.tenant(), s.patient(), sessionId);
        assertNotNull(clinic.agenda(s.tenant(), s.psychologist(), day, day).getFirst().get("confirmedAt"));

        assertThrows(SecurityException.class,
                () -> clinic.changeStatus(s.tenant(), s.patient(), sessionId, SessionStatus.REALIZADA));
        clinic.changeStatus(s.tenant(), s.psychologist(), sessionId, SessionStatus.REALIZADA);
        assertEquals("REALIZADA", clinic.agenda(s.tenant(), s.psychologist(), day, day).getFirst().get("status"));

        clinic.rate(s.tenant(), s.patient(), sessionId, 4, "boa");
        clinic.rate(s.tenant(), s.patient(), sessionId, 5, "mudei de ideia");
        assertEquals(Boolean.TRUE, clinic.agenda(s.tenant(), s.patient(), day, day).getFirst().get("rated"));

        Map<String, Object> declaration = clinic.attendance(s.tenant(), s.patient(), sessionId);
        assertEquals("2027-04-05", declaration.get("date"));
        assertThrows(SecurityException.class, () -> clinic.attendance(s.tenant(), s.intruder(), sessionId));

        // Uma sessão cancelada libera o horário.
        clinic.changeStatus(s.tenant(), s.psychologist(), sessionId, SessionStatus.CANCELADA);
        assertEquals(1, schedule(s, s.psychologist(), day, "10:00", 1).size());
    }

    @Test
    @DisplayName("financeiro: saldo a receber considera só pagamentos confirmados")
    void financeSummary() {
        Scenario s = scenario("clinica_financeiro");
        LocalDate today = LocalDate.now();

        PaymentEntity paid = payment(s, "150.00", true, today);
        payment(s, "100.00", false, today);

        Map<String, Object> summary = clinic.finance(s.tenant(), s.psychologist(), null, null);
        assertEquals(0, new BigDecimal("150.00").compareTo((BigDecimal) summary.get("received")));
        assertEquals(0, new BigDecimal("250.00").compareTo((BigDecimal) summary.get("outstanding")));
        assertEquals(1, ((List<?>) summary.get("debtors")).size());

        Map<String, Object> other = clinic.finance(s.tenant(), s.intruder(), null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) other.get("received")));
        assertTrue(((List<?>) other.get("debtors")).isEmpty());
        assertThrows(SecurityException.class, () -> clinic.finance(s.tenant(), s.patient(), null, null));

        Map<String, Object> receipt = clinic.receipt(s.tenant(), s.patient(), paid.getId());
        assertEquals("Paciente Teste", ((Map<?, ?>) receipt.get("patient")).get("name"));
        assertThrows(SecurityException.class, () -> clinic.receipt(s.tenant(), s.intruder(), paid.getId()));
    }

    private PaymentEntity payment(Scenario s, String amount, boolean paid, LocalDate date) {
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setAmount(new BigDecimal(amount));
        payment.setPaid(paid);
        payment.setPaymentDate(date);
        payment.setPaymentMethod(PaymentMethod.PIX);
        payment.setSessionPackage(s.pkg());
        return dao.update(s.tenant(), payment);
    }

    @Test
    @DisplayName("prontuário: só quem atende o paciente lê e grava")
    void patientRecordAccess() {
        Scenario s = scenario("clinica_prontuario");
        UUID patientId = s.patient().getId();

        assertNull(clinic.patientRecord(s.tenant(), s.psychologist(), patientId).get("chiefComplaint"));
        clinic.savePatientRecord(s.tenant(), s.psychologist(), patientId, Map.of("chiefComplaint", "Ansiedade"));
        clinic.savePatientRecord(s.tenant(), s.psychologist(), patientId,
                Map.of("chiefComplaint", "Ansiedade generalizada", "therapeuticGoals", "Reduzir crises"));

        Map<String, Object> record = clinic.patientRecord(s.tenant(), s.psychologist(), patientId);
        assertEquals("Ansiedade generalizada", record.get("chiefComplaint"));
        assertEquals("Reduzir crises", record.get("therapeuticGoals"));

        assertThrows(SecurityException.class, () -> clinic.patientRecord(s.tenant(), s.intruder(), patientId));
        assertThrows(SecurityException.class, () -> clinic.patientRecord(s.tenant(), s.patient(), patientId));
        assertThrows(SecurityException.class, () -> clinic.timeline(s.tenant(), s.intruder(), patientId));

        schedule(s, s.psychologist(), LocalDate.of(2027, 5, 3), "08:00", 2);
        assertEquals(2, clinic.timeline(s.tenant(), s.psychologist(), patientId).size());
    }

    @Test
    @DisplayName("LGPD e diário: consentimento, humor do dia e exportação dos próprios dados")
    void consentMoodAndExport() {
        Scenario s = scenario("clinica_lgpd");

        assertEquals(Boolean.FALSE, clinic.consentStatus(s.tenant(), s.patient()).get("accepted"));
        clinic.acceptConsent(s.tenant(), s.patient());
        assertEquals(Boolean.TRUE, clinic.consentStatus(s.tenant(), s.patient()).get("accepted"));
        assertEquals(Boolean.FALSE, clinic.consentStatus(s.tenant(), s.psychologist()).get("accepted"));

        clinic.saveMood(s.tenant(), s.patient(), null, 2, "dia difícil");
        clinic.saveMood(s.tenant(), s.patient(), null, 4, "melhorou");
        List<Map<String, Object>> diary = clinic.moodDiary(s.tenant(), s.patient(), null);
        assertEquals(1, diary.size(), "um registro por dia");
        assertEquals(4, diary.getFirst().get("mood"));

        assertEquals(1, clinic.moodDiary(s.tenant(), s.psychologist(), s.patient().getId()).size());
        assertThrows(SecurityException.class,
                () -> clinic.moodDiary(s.tenant(), s.intruder(), s.patient().getId()));
        assertThrows(SecurityException.class, () -> clinic.saveMood(s.tenant(), s.psychologist(), null, 3, null));
        assertThrows(BusinessException.class, () -> clinic.saveMood(s.tenant(), s.patient(), null, 9, null));

        Map<String, Object> export = clinic.exportMyData(s.tenant(), s.patient());
        assertEquals(1, ((List<?>) export.get("consents")).size());
        assertEquals(1, ((List<?>) export.get("moodDiary")).size());

        Map<String, Object> dashboard = clinic.dashboard(s.tenant(), s.psychologist());
        assertTrue(dashboard.containsKey("today"));
        assertEquals(1, dashboard.get("debtors"));
    }
}
