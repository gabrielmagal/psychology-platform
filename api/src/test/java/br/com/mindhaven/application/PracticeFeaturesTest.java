package br.com.mindhaven.application;

import br.com.mindhaven.application.service.AgendaService;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.application.service.FinanceService;
import br.com.mindhaven.application.service.InsuranceService;
import br.com.mindhaven.application.service.MeetingRooms;
import br.com.mindhaven.application.service.WaitlistService;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repetição de sessões, sala de vídeo, convênios, Receita Saúde e lista de espera. */
@QuarkusTest
class PracticeFeaturesTest {
    @Inject
    GenericDao dao;
    @Inject
    ClinicService clinic;
    @Inject
    AgendaService agenda;
    @Inject
    FinanceService finance;
    @Inject
    InsuranceService insurances;
    @Inject
    WaitlistService waitlist;

    private record Scenario(String tenant, UserEntity admin, UserEntity professional, UserEntity other,
                            UserEntity patient, SessionPackageEntity pkg) {
    }

    private UserEntity newUser(String tenant, String cpf, String name, UserType type, UserEntity registeredBy) {
        return dao.update(tenant, UserEntity.builder()
                .cpf(cpf).firstName(name).lastName("Teste").email(name.toLowerCase() + "@exemplo.com")
                .phoneNumber("11" + cpf).birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .registeredByKeycloakId(registeredBy == null ? "sistema" : registeredBy.getKeycloakId())
                .userType(type).crp(type == UserType.PROFISSIONAL ? "06/123456" : null).build());
    }

    private Scenario scenario(String tenant) {
        UserEntity admin = newUser(tenant, "50000000001", "Admin", UserType.ADMINISTRADOR, null);
        UserEntity professional = newUser(tenant, "50000000002", "Dona", UserType.PROFISSIONAL, admin);
        UserEntity other = newUser(tenant, "50000000003", "Outra", UserType.PROFISSIONAL, admin);
        UserEntity patient = newUser(tenant, "50000000004", "Paciente", UserType.PACIENTE, professional);
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Acompanhamento");
        pkg.setPrice(new BigDecimal("400.00"));
        return new Scenario(tenant, admin, professional, other, patient, dao.update(tenant, pkg));
    }

    private static LocalDate nextMonday(int weeksAhead) {
        return LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private ClinicService.ScheduleResult repeat(Scenario s, LocalDate date, String time, ClinicService.Frequency frequency,
                                                int occurrences, boolean skipConflicts) {
        return clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), date, LocalTime.parse(time), 50,
                SessionModality.PRESENCIAL, null, "Sessão", frequency, occurrences, skipConflicts);
    }

    @Test
    @DisplayName("repetição: quinzenal e mensal caem nas datas certas e formam uma série")
    void recurrenceDates() {
        Scenario s = scenario("l3_serie");
        LocalDate monday = nextMonday(1);

        ClinicService.ScheduleResult biweekly = repeat(s, monday, "09:00", ClinicService.Frequency.BIWEEKLY, 3, false);
        assertEquals(3, biweekly.created().size());
        List<Map<String, Object>> items = clinic.agenda(s.tenant(), s.professional(), monday, monday.plusWeeks(5));
        assertEquals(List.of(monday.toString(), monday.plusWeeks(2).toString(), monday.plusWeeks(4).toString()),
                items.stream().map(i -> i.get("date")).toList());
        Object series = items.getFirst().get("seriesId");
        assertNotNull(series);
        assertTrue(items.stream().allMatch(i -> series.equals(i.get("seriesId"))));

        LocalDate day = monday.plusDays(1);
        repeat(s, day, "10:00", ClinicService.Frequency.MONTHLY, 2, false);
        assertEquals(1, clinic.agenda(s.tenant(), s.professional(), day.plusMonths(1), day.plusMonths(1)).size());

        // Sessão única não é série.
        repeat(s, monday.plusDays(2), "09:00", ClinicService.Frequency.WEEKLY, 1, false);
        assertNull(clinic.agenda(s.tenant(), s.professional(), monday.plusDays(2), monday.plusDays(2)).getFirst().get("seriesId"));
    }

    @Test
    @DisplayName("repetição: conflito cancela tudo, ou é pulado e informado quando se pede")
    void recurrenceConflicts() {
        Scenario s = scenario("l3_conflito");
        LocalDate monday = nextMonday(1);
        // Um bloqueio na segunda semana.
        agenda.addBlock(s.tenant(), s.professional(), monday.plusWeeks(1).atTime(8, 0), monday.plusWeeks(1).atTime(12, 0), "Feriado");

        assertEquals(409, assertThrows(BusinessException.class,
                () -> repeat(s, monday, "09:00", ClinicService.Frequency.WEEKLY, 3, false)).getStatus());

        ClinicService.ScheduleResult result = repeat(s, monday, "09:00", ClinicService.Frequency.WEEKLY, 3, true);
        assertEquals(2, result.created().size());
        assertEquals(1, result.skipped().size());
        assertEquals(monday.plusWeeks(1).toString(), result.skipped().getFirst().get("date"));
        assertTrue(result.skipped().getFirst().get("reason").toString().contains("Feriado"));
    }

    @Test
    @DisplayName("série: cancelar 'esta e as próximas' poupa as anteriores e as já realizadas")
    void cancelSeries() {
        Scenario s = scenario("l3_cancela");
        LocalDate monday = nextMonday(1);
        List<UUID> ids = repeat(s, monday, "09:00", ClinicService.Frequency.WEEKLY, 4, false).created();
        clinic.changeStatus(s.tenant(), s.professional(), ids.get(2), SessionStatus.REALIZADA);

        assertThrows(SecurityException.class, () -> agenda.cancelSeries(s.tenant(), s.other(), ids.get(1)));
        assertThrows(SecurityException.class, () -> agenda.cancelSeries(s.tenant(), s.patient(), ids.get(1)));
        assertEquals(2, agenda.cancelSeries(s.tenant(), s.professional(), ids.get(1)));

        List<Object> statuses = clinic.agenda(s.tenant(), s.professional(), monday, monday.plusWeeks(4))
                .stream().map(i -> i.get("status")).toList();
        assertEquals(List.of("AGENDADA", "CANCELADA", "REALIZADA", "CANCELADA"), statuses);
    }

    @Test
    @DisplayName("vídeo: sessão online ganha sala própria; link informado é respeitado e validado")
    void meetingRooms() {
        Scenario s = scenario("l3_video");
        LocalDate monday = nextMonday(1);

        ClinicService.ScheduleResult online = clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), monday,
                LocalTime.parse("09:00"), 50, SessionModality.ONLINE, null, "Sessão", ClinicService.Frequency.WEEKLY, 2, false);
        assertTrue(online.meetingLink().startsWith("https://"), online.meetingLink());
        LocalDate second = monday.plusWeeks(1);
        assertEquals(online.meetingLink(), clinic.agenda(s.tenant(), s.patient(), monday, monday).getFirst().get("meetingLink"));
        assertEquals(online.meetingLink(), clinic.agenda(s.tenant(), s.patient(), second, second).getFirst().get("meetingLink"),
                "a série usa a mesma sala");

        ClinicService.ScheduleResult own = clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), monday.plusDays(1),
                LocalTime.parse("09:00"), 50, SessionModality.ONLINE, "https://meet.exemplo.com/minha-sala", "Sessão",
                ClinicService.Frequency.WEEKLY, 1, false);
        assertEquals("https://meet.exemplo.com/minha-sala", own.meetingLink());

        ClinicService.ScheduleResult inPerson = clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), monday.plusDays(2),
                LocalTime.parse("09:00"), 50, SessionModality.PRESENCIAL, "https://meet.exemplo.com/x", "Sessão",
                ClinicService.Frequency.WEEKLY, 1, false);
        assertNull(inPerson.meetingLink(), "presencial não tem link");

        // Link que não é http(s) executaria código no navegador do paciente.
        assertThrows(BusinessException.class, () -> MeetingRooms.clean("javascript:alert(1)"));
        assertThrows(BusinessException.class, () -> clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(),
                monday.plusDays(3), LocalTime.parse("09:00"), 50, SessionModality.ONLINE, "javascript:alert(1)", "Sessão",
                ClinicService.Frequency.WEEKLY, 1, false));

        // Trocar a sala: nova para a série inteira; depois presencial, que apaga o link.
        UUID first = online.created().getFirst();
        String replaced = agenda.updateMeeting(s.tenant(), s.professional(), first, null, null, true, true);
        assertNotEquals(online.meetingLink(), replaced);
        assertEquals(replaced, clinic.agenda(s.tenant(), s.patient(), second, second).getFirst().get("meetingLink"));
        assertThrows(SecurityException.class,
                () -> agenda.updateMeeting(s.tenant(), s.patient(), first, SessionModality.PRESENCIAL, null, false, false));
        assertNull(agenda.updateMeeting(s.tenant(), s.professional(), first, SessionModality.PRESENCIAL, null, false, false));
        assertEquals("PRESENCIAL", clinic.agenda(s.tenant(), s.patient(), monday, monday).getFirst().get("modality"));
    }

    @Test
    @DisplayName("convênio: cadastro, vínculo do pacote e o caminho a faturar > faturada > recebida")
    @SuppressWarnings("unchecked")
    void insuranceFlow() {
        Scenario s = scenario("l3_convenio");
        LocalDate monday = nextMonday(1);

        assertThrows(SecurityException.class, () -> insurances.list(s.tenant(), s.patient()));
        assertThrows(BusinessException.class, () -> insurances.save(s.tenant(), s.admin(), null, " ", null, null, null, null, true));
        UUID insurance = (UUID) insurances.save(s.tenant(), s.admin(), null, "Saúde Total", "123456",
                new BigDecimal("80.00"), 30, null, true).get("id");

        assertThrows(SecurityException.class,
                () -> insurances.linkPackage(s.tenant(), s.other(), s.pkg().getId(), insurance, "999"));
        insurances.linkPackage(s.tenant(), s.professional(), s.pkg().getId(), insurance, "0001234");
        Map<String, Object> linked = insurances.packages(s.tenant(), s.professional()).getFirst();
        assertEquals("Saúde Total", linked.get("insuranceName"));
        assertEquals("0001234", linked.get("cardNumber"));
        assertTrue(insurances.packages(s.tenant(), s.other()).isEmpty(), "outro profissional não vê o pacote");

        List<UUID> ids = repeat(s, monday, "09:00", ClinicService.Frequency.WEEKLY, 2, false).created();
        assertEquals("Saúde Total", clinic.agenda(s.tenant(), s.patient(), monday, monday).getFirst().get("insuranceName"));
        clinic.changeStatus(s.tenant(), s.professional(), ids.getFirst(), SessionStatus.REALIZADA);

        // Só a sessão realizada entra no faturamento, pelo valor do convênio.
        Map<String, Object> report = insurances.sessions(s.tenant(), s.professional(), monday, monday.plusWeeks(2), null);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        assertEquals(1, rows.size());
        assertEquals(InsuranceService.TO_BILL, rows.getFirst().get("status"));
        assertEquals(0, new BigDecimal("80.00").compareTo((BigDecimal) rows.getFirst().get("amount")));

        insurances.updateSession(s.tenant(), s.professional(), ids.getFirst(),
                Map.of("guide", "G-77", "amount", "95,50", "billedAt", monday.plusDays(3).toString()));
        Map<String, Object> billed = ((List<Map<String, Object>>) insurances.sessions(s.tenant(), s.admin(), monday,
                monday.plusWeeks(2), insurance).get("rows")).getFirst();
        assertEquals(InsuranceService.BILLED, billed.get("status"));
        assertEquals("G-77", billed.get("guide"));
        assertEquals(monday.plusDays(33).toString(), billed.get("expectedAt"));
        assertEquals(0, new BigDecimal("95.50").compareTo((BigDecimal) billed.get("amount")));

        assertThrows(BusinessException.class, () -> insurances.updateSession(s.tenant(), s.professional(), ids.getFirst(),
                Map.of("paidAt", monday.toString())), "recebimento antes do faturamento");
        assertThrows(SecurityException.class, () -> insurances.updateSession(s.tenant(), s.other(), ids.getFirst(),
                Map.of("paidAt", monday.plusDays(40).toString())));
        insurances.updateSession(s.tenant(), s.professional(), ids.getFirst(), Map.of("paidAt", monday.plusDays(40).toString()));
        report = insurances.sessions(s.tenant(), s.professional(), monday, monday.plusWeeks(2), null);
        assertEquals(1, ((Map<String, Integer>) report.get("counts")).get(InsuranceService.PAID));
        assertEquals(0, new BigDecimal("95.50").compareTo(((Map<String, BigDecimal>) report.get("totals")).get(InsuranceService.PAID)));

        // Convênio em uso não é excluído; voltar a particular libera.
        assertEquals(409, assertThrows(BusinessException.class,
                () -> insurances.remove(s.tenant(), s.admin(), insurance)).getStatus());
        insurances.linkPackage(s.tenant(), s.professional(), s.pkg().getId(), null, null);
        assertThrows(BusinessException.class, () -> insurances.updateSession(s.tenant(), s.professional(), ids.getFirst(),
                Map.of("guide", "G-78")), "pacote particular não tem guia");
        insurances.remove(s.tenant(), s.admin(), insurance);
        assertTrue(insurances.list(s.tenant(), s.admin()).isEmpty());
    }

    @Test
    @DisplayName("Receita Saúde: recebimentos pendentes de lançamento e a marca de lançado")
    @SuppressWarnings("unchecked")
    void taxReceipts() {
        Scenario s = scenario("l3_receita");
        LocalDate today = LocalDate.now();
        PaymentEntity payment = new PaymentEntity();
        payment.setAmount(new BigDecimal("200.00"));
        payment.setPaid(true);
        payment.setPaymentDate(today);
        payment.setPaymentMethod(PaymentMethod.PIX);
        payment.setSessionPackage(s.pkg());
        UUID paid = dao.update(s.tenant(), payment).getId();

        PaymentEntity open = new PaymentEntity();
        open.setAmount(new BigDecimal("50.00"));
        open.setPaid(false);
        open.setPaymentDate(today);
        open.setPaymentMethod(PaymentMethod.PIX);
        open.setSessionPackage(s.pkg());
        UUID unpaid = dao.update(s.tenant(), open).getId();

        Map<String, Object> report = finance.taxReceipts(s.tenant(), s.professional(), today.getYear());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        assertEquals(1, rows.size(), "só o que foi recebido");
        assertEquals("50000000004", rows.getFirst().get("patientCpf"));
        assertEquals(1, report.get("pending"));
        assertFalse((Boolean) rows.getFirst().get("launched"));
        assertTrue(((List<?>) finance.taxReceipts(s.tenant(), s.other(), today.getYear()).get("rows")).isEmpty());
        assertThrows(SecurityException.class, () -> finance.taxReceipts(s.tenant(), s.patient(), today.getYear()));

        assertThrows(SecurityException.class, () -> finance.markTaxReceipt(s.tenant(), s.other(), paid, true, null));
        assertThrows(SecurityException.class, () -> finance.markTaxReceipt(s.tenant(), s.admin(), paid, true, null));
        assertThrows(BusinessException.class, () -> finance.markTaxReceipt(s.tenant(), s.professional(), unpaid, true, null));
        finance.markTaxReceipt(s.tenant(), s.professional(), paid, true, "RS-2026-001");

        report = finance.taxReceipts(s.tenant(), s.admin(), today.getYear());
        Map<String, Object> row = ((List<Map<String, Object>>) report.get("rows")).getFirst();
        assertTrue((Boolean) row.get("launched"));
        assertEquals("RS-2026-001", row.get("receiptNumber"));
        assertEquals(0, report.get("pending"));
        assertEquals("Dona Teste", row.get("professionalName"));

        finance.markTaxReceipt(s.tenant(), s.professional(), paid, false, null);
        assertEquals(1, finance.taxReceipts(s.tenant(), s.professional(), today.getYear()).get("pending"));
    }

    @Test
    @DisplayName("lista de espera: fila por ordem de chegada, cada profissional com a sua")
    void waitlistFlow() {
        Scenario s = scenario("l3_espera");

        assertThrows(SecurityException.class, () -> waitlist.list(s.tenant(), s.patient(), false));
        assertThrows(BusinessException.class,
                () -> waitlist.add(s.tenant(), s.professional(), null, " ", null, null, null, null, null));
        UUID mine = (UUID) waitlist.add(s.tenant(), s.professional(), null, "Carlos Souza", "11988887777",
                "terça ou quinta à tarde", "ONLINE", null, s.other().getId()).get("id");
        UUID anyone = (UUID) waitlist.add(s.tenant(), s.admin(), null, "Ana Lima", "11977776666", null, null, null, null).get("id");
        UUID theirs = (UUID) waitlist.add(s.tenant(), s.admin(), null, "Bruno Dias", null, null, "PRESENCIAL", null,
                s.other().getId()).get("id");
        // Paciente já cadastrado: nome e telefone vêm do cadastro.
        waitlist.add(s.tenant(), s.professional(), s.patient().getId(), null, null, "manhã", null, null, null);
        assertThrows(SecurityException.class,
                () -> waitlist.add(s.tenant(), s.other(), s.patient().getId(), null, null, null, null, null, null),
                "paciente não associado ao profissional");

        List<Map<String, Object>> own = waitlist.list(s.tenant(), s.professional(), false);
        assertEquals(List.of("Carlos Souza", "Ana Lima", "Paciente Teste"), own.stream().map(r -> r.get("name")).toList());
        assertEquals(s.professional().getId(), own.getFirst().get("professionalId"), "o profissional só põe na própria fila");
        assertEquals("1150000000004", own.get(2).get("phone"));
        assertEquals(4, waitlist.list(s.tenant(), s.admin(), false).size());

        assertThrows(SecurityException.class, () -> waitlist.changeStatus(s.tenant(), s.professional(), theirs, "AGENDADO"));
        assertThrows(BusinessException.class, () -> waitlist.changeStatus(s.tenant(), s.professional(), mine, "QUALQUER"));
        waitlist.changeStatus(s.tenant(), s.professional(), mine, "CONTATADO");
        waitlist.changeStatus(s.tenant(), s.professional(), anyone, "AGENDADO");
        assertEquals(List.of("Carlos Souza", "Paciente Teste"),
                waitlist.list(s.tenant(), s.professional(), false).stream().map(r -> r.get("name")).toList());
        assertEquals(3, waitlist.list(s.tenant(), s.professional(), true).size());

        waitlist.remove(s.tenant(), s.professional(), mine);
        assertEquals(1, waitlist.list(s.tenant(), s.professional(), false).size());
    }
}
