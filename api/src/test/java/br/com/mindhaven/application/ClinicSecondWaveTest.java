package br.com.mindhaven.application;

import br.com.mindhaven.adapter.controller.dto.UserDto;
import br.com.mindhaven.application.service.AgendaService;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.application.service.DocumentService;
import br.com.mindhaven.application.service.FinanceService;
import br.com.mindhaven.application.service.RecordsService;
import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
import br.com.mindhaven.application.usecase.interfaces.UserUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.crypto.EncryptedString;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import core.validation.DtoValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Segunda leva: cifragem, validação, agenda (regras e remarcação), documentos, caixa, anexos e guarda. */
@QuarkusTest
class ClinicSecondWaveTest {
    @Inject
    GenericDao dao;
    @Inject
    ClinicService clinic;
    @Inject
    AgendaService agenda;
    @Inject
    DocumentService documents;
    @Inject
    FinanceService finance;
    @Inject
    RecordsService records;
    @Inject
    SessionPackageUseCase packages;
    @Inject
    UserUseCase users;
    @Inject
    br.com.mindhaven.application.service.PatientAccess access;

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
        UserEntity admin = newUser(tenant, "40000000001", "Admin", UserType.ADMINISTRADOR, null);
        UserEntity professional = newUser(tenant, "40000000002", "Dona", UserType.PROFISSIONAL, admin);
        UserEntity other = newUser(tenant, "40000000003", "Outra", UserType.PROFISSIONAL, admin);
        UserEntity patient = newUser(tenant, "40000000004", "Paciente", UserType.PACIENTE, professional);
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Pacote mensal");
        pkg.setPrice(new BigDecimal("400.00"));
        return new Scenario(tenant, admin, professional, other, patient, dao.update(tenant, pkg));
    }

    private UUID schedule(Scenario s, LocalDate date, String time) {
        return clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), date, LocalTime.parse(time), 50,
                SessionModality.PRESENCIAL, null, "Sessão", 1).getFirst();
    }

    private static LocalDate nextMonday(int weeksAhead) {
        return LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @Test
    @DisplayName("cifragem: texto vira outro no banco, volta igual, e dado antigo em claro continua legível")
    void encryption() {
        EncryptedString converter = new EncryptedString();
        String stored = converter.convertToDatabaseColumn("Ansiedade generalizada");
        assertTrue(stored.startsWith("enc:v1:"));
        assertFalse(stored.contains("Ansiedade"));
        assertNotEquals(stored, converter.convertToDatabaseColumn("Ansiedade generalizada"), "cada gravação usa um IV novo");
        assertEquals("Ansiedade generalizada", converter.convertToEntityAttribute(stored));
        assertEquals("texto antigo em claro", converter.convertToEntityAttribute("texto antigo em claro"));

        Scenario s = scenario("v2_cifra");
        clinic.savePatientRecord(s.tenant(), s.professional(), s.patient().getId(), Map.of("chiefComplaint", "Luto recente"));
        assertEquals("Luto recente", clinic.patientRecord(s.tenant(), s.professional(), s.patient().getId()).get("chiefComplaint"));
    }

    @Test
    @DisplayName("validação no servidor cita os campos pelo rótulo da tela")
    void serverValidation() {
        UserDto dto = new UserDto();
        dto.setCpf("123");
        dto.setEmail("sem-arroba");
        String problems = String.join(" ", DtoValidator.problems(dto));
        assertTrue(problems.contains("Nome"), problems);
        assertTrue(problems.contains("Telefone"), problems);
        assertTrue(problems.contains("E-mail não é um e-mail válido"), problems);
        assertTrue(problems.contains("CPF deve ter 11 dígitos"), problems);

        dto.setCpf("12345678901");
        dto.setFirstName("Ana");
        dto.setLastName("Silva");
        dto.setEmail("ana@exemplo.com");
        dto.setPhoneNumber("11999990000");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setUserType(UserType.PACIENTE);
        assertTrue(DtoValidator.problems(dto).isEmpty());
    }

    @Test
    @DisplayName("agenda: horário de atendimento, bloqueio, remarcação e a regra das 24h do paciente")
    void agendaRulesAndReschedule() {
        Scenario s = scenario("v2_agenda");
        LocalDate monday = nextMonday(2);

        agenda.saveWorkingHours(s.tenant(), s.professional(), List.of(
                Map.of("weekday", 1, "startTime", "08:00", "endTime", "12:00"),
                Map.of("weekday", 3, "startTime", "14:00", "endTime", "18:00")));
        UUID session = schedule(s, monday, "09:00");
        assertEquals(409, assertThrows(BusinessException.class, () -> schedule(s, monday, "13:00")).getStatus(), "fora do horário");
        assertEquals(409, assertThrows(BusinessException.class, () -> schedule(s, monday.plusDays(1), "09:00")).getStatus(), "terça não atende");

        Map<String, Object> block = agenda.addBlock(s.tenant(), s.professional(), monday.plusDays(2).atTime(14, 0),
                monday.plusDays(2).atTime(16, 0), "Supervisão");
        assertNotNull(block.get("id"));
        BusinessException blocked = assertThrows(BusinessException.class, () -> schedule(s, monday.plusDays(2), "15:00"));
        assertTrue(blocked.getMessage().contains("Supervisão"));

        // Horários livres do dia já descontam a sessão das 09:00.
        List<String> free = agenda.freeSlots(s.tenant(), s.patient(), session, monday);
        assertFalse(free.contains("09:00"));
        assertTrue(free.contains("08:00") || free.contains("09:50") || free.contains("10:40"), free.toString());

        // Paciente remarca com antecedência, dentro do horário de atendimento.
        String target = free.getFirst();
        agenda.reschedule(s.tenant(), s.patient(), session, monday, LocalTime.parse(target));
        assertEquals(target, clinic.agenda(s.tenant(), s.patient(), monday, monday).getFirst().get("startTime"));
        assertThrows(SecurityException.class,
                () -> agenda.reschedule(s.tenant(), s.other(), session, monday, LocalTime.parse("08:00")));

        // Sessão para daqui a poucas horas: o paciente não remarca nem cancela sozinho.
        agenda.saveWorkingHours(s.tenant(), s.professional(), List.of());
        LocalDateTime soon = LocalDateTime.now().plusHours(3);
        UUID tomorrow = schedule(s, soon.toLocalDate(), soon.toLocalTime().withSecond(0).withNano(0).toString());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> agenda.patientCancel(s.tenant(), s.patient(), tomorrow)).getStatus());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> agenda.reschedule(s.tenant(), s.patient(), tomorrow, monday, LocalTime.parse("10:00"))).getStatus());
        // O profissional remarca a qualquer momento.
        agenda.reschedule(s.tenant(), s.professional(), tomorrow, monday.plusWeeks(1), LocalTime.parse("10:00"));

        agenda.patientCancel(s.tenant(), s.patient(), session);
        assertEquals(SessionStatus.CANCELADA.name(), clinic.agenda(s.tenant(), s.patient(), monday, monday).getFirst().get("status"));
    }

    @Test
    @DisplayName("evolução em texto livre e sessão avulsa com valor próprio")
    void evolutionAndSingleSession() {
        Scenario s = scenario("v2_evolucao");
        LocalDate monday = nextMonday(3);
        UUID session = schedule(s, monday, "10:00");

        agenda.saveEvolution(s.tenant(), s.professional(), session, "Paciente relatou melhora do sono.");
        assertThrows(SecurityException.class, () -> agenda.saveEvolution(s.tenant(), s.patient(), session, "x"));
        assertEquals("Paciente relatou melhora do sono.",
                clinic.timeline(s.tenant(), s.professional(), s.patient().getId()).getFirst().get("evolution"));

        List<UUID> single = agenda.scheduleSingle(s.tenant(), s.professional(), s.patient().getId(),
                new BigDecimal("180.00"), monday, LocalTime.parse("15:00"), 50, SessionModality.ONLINE, null);
        assertEquals(1, single.size());
        Map<String, Object> item = clinic.agenda(s.tenant(), s.professional(), monday, monday).stream()
                .filter(i -> single.getFirst().equals(i.get("id"))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, item.get("singleSession"));
        // A avulsa entra no financeiro como saldo a receber, como qualquer pacote com valor.
        BigDecimal outstanding = (BigDecimal) clinic.finance(s.tenant(), s.professional(), null, null).get("outstanding");
        assertEquals(0, new BigDecimal("580.00").compareTo(outstanding));
        assertThrows(SecurityException.class, () -> agenda.scheduleSingle(s.tenant(), s.admin(), s.patient().getId(),
                BigDecimal.TEN, monday, LocalTime.parse("16:00"), 50, null, null));
    }

    @Test
    @DisplayName("documentos: PDF guardado e numerado; recibo do mesmo pagamento não gera outro número")
    void documents() {
        Scenario s = scenario("v2_docs");
        PaymentEntity payment = new PaymentEntity();
        payment.setAmount(new BigDecimal("150.00"));
        payment.setPaid(true);
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentMethod(PaymentMethod.PIX);
        payment.setSessionPackage(s.pkg());
        PaymentEntity saved = dao.update(s.tenant(), payment);

        Map<String, Object> first = documents.issue(s.tenant(), s.professional(), DocumentService.RECEIPT, saved.getId());
        Map<String, Object> again = documents.issue(s.tenant(), s.patient(), DocumentService.RECEIPT, saved.getId());
        assertEquals(first.get("id"), again.get("id"));
        assertTrue(first.get("number").toString().startsWith("0001/"));

        byte[] pdf = documents.pdf(s.tenant(), s.patient(), (UUID) first.get("id")).getContent();
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertThrows(SecurityException.class, () -> documents.pdf(s.tenant(), s.other(), (UUID) first.get("id")));

        Map<String, Object> contract = documents.issue(s.tenant(), s.professional(), DocumentService.CONTRACT, s.patient().getId());
        assertThrows(SecurityException.class,
                () -> documents.issue(s.tenant(), s.patient(), DocumentService.CONTRACT, s.patient().getId()));
        assertThrows(SecurityException.class, () -> documents.pdf(s.tenant(), s.patient(), (UUID) contract.get("id")));

        assertEquals(2, documents.list(s.tenant(), s.professional(), s.patient().getId()).size());
        assertEquals(1, documents.list(s.tenant(), s.patient(), null).size(), "o paciente não vê o contrato na lista");
    }

    @Test
    @DisplayName("livro-caixa: despesas, resultado do mês e exportação em CSV")
    void cashBook() {
        Scenario s = scenario("v2_caixa");
        LocalDate today = LocalDate.now();
        PaymentEntity payment = new PaymentEntity();
        payment.setAmount(new BigDecimal("300.00"));
        payment.setPaid(true);
        payment.setPaymentDate(today);
        payment.setPaymentMethod(PaymentMethod.PIX);
        payment.setSessionPackage(s.pkg());
        dao.update(s.tenant(), payment);

        Map<String, Object> rent = finance.saveExpense(s.tenant(), s.professional(), null, today, "Aluguel da sala",
                "Aluguel", new BigDecimal("120.00"), true);
        finance.saveExpense(s.tenant(), s.professional(), null, today, "Café", null, new BigDecimal("30.00"), false);
        assertThrows(BusinessException.class, () -> finance.saveExpense(s.tenant(), s.professional(), null, today, "",
                null, BigDecimal.ONE, true));
        assertThrows(SecurityException.class, () -> finance.deleteExpense(s.tenant(), s.other(), (UUID) rent.get("id")));

        Map<String, Object> report = finance.yearReport(s.tenant(), s.professional(), today.getYear());
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) report.get("income")));
        assertEquals(0, new BigDecimal("150.00").compareTo((BigDecimal) report.get("expenses")));
        assertEquals(0, new BigDecimal("150.00").compareTo((BigDecimal) report.get("result")));
        @SuppressWarnings("unchecked")
        Map<String, Object> month = ((List<Map<String, Object>>) report.get("months")).get(today.getMonthValue() - 1);
        assertEquals(0, new BigDecimal("180.00").compareTo((BigDecimal) month.get("taxBase")), "só a despesa dedutível abate");

        String csv = finance.yearCsv(s.tenant(), s.professional(), today.getYear());
        assertTrue(csv.contains("Receita;") && csv.contains("300,00"));
        assertTrue(csv.contains("\"Aluguel - Aluguel da sala\"") && csv.contains("120,00"));
        assertTrue(finance.expenses(s.tenant(), s.other(), today, today).isEmpty(), "cada profissional vê o próprio caixa");
    }

    @Test
    @DisplayName("anexos do prontuário e prazo de guarda")
    void attachmentsAndRetention() {
        Scenario s = scenario("v2_guarda");
        UUID patientId = s.patient().getId();
        String content = Base64.getEncoder().encodeToString("laudo".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> file = records.attach(s.tenant(), s.professional(), patientId, "C:\\docs\\laudo.pdf",
                "application/pdf", content, "Laudo neurológico");
        assertEquals("laudo.pdf", file.get("fileName"));
        assertEquals(400, assertThrows(BusinessException.class, () -> records.attach(s.tenant(), s.professional(),
                patientId, "virus.exe", "application/x-msdownload", content, null)).getStatus());
        assertThrows(SecurityException.class, () -> records.attach(s.tenant(), s.other(), patientId, "a.pdf",
                "application/pdf", content, null));

        assertEquals(1, records.attachments(s.tenant(), s.professional(), patientId).size());
        assertEquals("laudo", new String(records.download(s.tenant(), s.professional(), (UUID) file.get("id")).content(),
                StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> records.download(s.tenant(), s.other(), (UUID) file.get("id")));
        records.removeAttachment(s.tenant(), s.professional(), (UUID) file.get("id"));
        assertTrue(records.attachments(s.tenant(), s.professional(), patientId).isEmpty());

        // Sem atendimento não há prontuário a guardar.
        records.requireDisposable(s.tenant(), patientId);
        schedule(s, nextMonday(4), "09:00");
        BusinessException kept = assertThrows(BusinessException.class, () -> records.requireDisposable(s.tenant(), patientId));
        assertTrue(kept.getMessage().contains("guardado até"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) records.retentionReport(s.tenant(), s.admin()).get("patients");
        assertEquals(1, rows.size());
        assertEquals(Boolean.FALSE, rows.getFirst().get("disposable"));
        assertThrows(SecurityException.class, () -> records.retentionReport(s.tenant(), s.professional()));
    }

    @Test
    @DisplayName("visibilidade: o profissional vê quem atende e quem a clínica cadastrou; admin não cria pacote")
    void visibility() {
        Scenario s = scenario("v2_visao");
        UserEntity byClinic = newUser(s.tenant(), "40000000010", "DaClinica", UserType.PACIENTE, s.admin());
        UserEntity ofOther = newUser(s.tenant(), "40000000011", "DaOutra", UserType.PACIENTE, s.other());

        Filter filter = new Filter();
        filter.setFilterParams(new ArrayList<>());
        List<UUID> visible = users.filteredFindPaged(s.tenant(), s.professional(), filter, 0, 50).stream()
                .map(UserEntity::getId).toList();
        assertTrue(visible.contains(s.patient().getId()), "o que ele cadastrou");
        assertFalse(visible.contains(byClinic.getId()), "cadastrado pelo administrador, ainda sem vínculo");
        assertTrue(visible.contains(s.professional().getId()), "ele mesmo");
        assertFalse(visible.contains(ofOther.getId()), "paciente de outra profissional");
        assertFalse(visible.contains(s.other().getId()), "outra profissional");

        // Sem vínculo, não abre pacote nem prontuário de paciente de outra profissional.
        SessionPackageEntity notMine = new SessionPackageEntity();
        notMine.setPatient(ofOther);
        assertThrows(SecurityException.class, () -> packages.save(s.tenant(), s.professional(), notMine));
        assertThrows(SecurityException.class, () -> clinic.patientRecord(s.tenant(), s.professional(), ofOther.getId()));

        // O paciente passa a ser atendido também por ela: cadastra pelo CPF e o cadastro é compartilhado.
        UserEntity sameCpf = UserEntity.builder().cpf(ofOther.getCpf()).firstName("Qualquer").lastName("Nome")
                .email("x@exemplo.com").phoneNumber("11900000000").birthDate(LocalDate.of(1990, 1, 1))
                .userType(UserType.PACIENTE).build();
        assertEquals(ofOther.getId(), users.save(s.tenant(), s.professional(), sameCpf).getId());

        // Prontuário separado: cada profissional só vê o que ela mesma escreveu.
        clinic.savePatientRecord(s.tenant(), s.other(), ofOther.getId(), Map.of("chiefComplaint", "Da outra"));
        clinic.savePatientRecord(s.tenant(), s.professional(), ofOther.getId(), Map.of("chiefComplaint", "Minha"));
        assertEquals("Minha", clinic.patientRecord(s.tenant(), s.professional(), ofOther.getId()).get("chiefComplaint"));
        assertEquals("Da outra", clinic.patientRecord(s.tenant(), s.other(), ofOther.getId()).get("chiefComplaint"));
        // O administrador vê as duas, só para leitura.
        Map<String, Object> adminView = clinic.patientRecord(s.tenant(), s.admin(), ofOther.getId());
        assertEquals(2, ((List<?>) adminView.get("records")).size());
        assertThrows(SecurityException.class,
                () -> clinic.savePatientRecord(s.tenant(), s.admin(), ofOther.getId(), Map.of("chiefComplaint", "x")));
        // Cadastro compartilhado não pode ser apagado por quem o criou.
        assertEquals(409, assertThrows(BusinessException.class,
                () -> users.delete(s.tenant(), s.other(), ofOther.getId())).getStatus());
        Filter again = new Filter();
        again.setFilterParams(new ArrayList<>());
        assertTrue(users.filteredFindPaged(s.tenant(), s.professional(), again, 0, 50).stream()
                .anyMatch(u -> u.getId().equals(ofOther.getId())));
        assertEquals(ofOther.getId(), users.findById(s.tenant(), s.professional(), ofOther.getId()).getId());

        // Foto de perfil guardada no banco.
        String png = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        access.savePhoto(s.tenant(), s.patient().getId(), png);
        assertEquals(png, access.photo(s.tenant(), s.patient().getId()));
        assertEquals(1, access.photos(s.tenant(), List.of(s.patient().getId(), s.other().getId())).size());

        SessionPackageEntity byAdmin = new SessionPackageEntity();
        byAdmin.setProfessionalId(s.professional().getId());
        byAdmin.setPatient(byClinic);
        assertThrows(SecurityException.class, () -> packages.save(s.tenant(), s.admin(), byAdmin));
    }
}
