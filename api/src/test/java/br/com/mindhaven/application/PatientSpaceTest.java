package br.com.mindhaven.application;

import br.com.mindhaven.application.service.AgendaService;
import br.com.mindhaven.application.service.CareService;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tarefas, plano de segurança, termo de atendimento online, sono e intercorrências técnicas. */
@QuarkusTest
class PatientSpaceTest {
    @Inject
    GenericDao dao;
    @Inject
    CareService care;
    @Inject
    ClinicService clinic;
    @Inject
    AgendaService agenda;

    private record Scenario(String tenant, UserEntity admin, UserEntity professional, UserEntity other,
                            UserEntity patient, UserEntity stranger, SessionPackageEntity pkg) {
    }

    private UserEntity newUser(String tenant, String cpf, String name, UserType type, UserEntity registeredBy) {
        return dao.update(tenant, UserEntity.builder()
                .cpf(cpf).firstName(name).lastName("Teste").email(name.toLowerCase() + "@exemplo.com")
                .phoneNumber("11" + cpf).birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .registeredByKeycloakId(registeredBy == null ? "sistema" : registeredBy.getKeycloakId())
                .emergencyContactName(type == UserType.PACIENTE ? "Mãe" : null)
                .emergencyContactPhone(type == UserType.PACIENTE ? "11988887777" : null)
                .userType(type).crp(type == UserType.PROFISSIONAL ? "06/123456" : null).build());
    }

    private Scenario scenario(String tenant) {
        UserEntity admin = newUser(tenant, "60000000001", "Admin", UserType.ADMINISTRADOR, null);
        UserEntity professional = newUser(tenant, "60000000002", "Dona", UserType.PROFISSIONAL, admin);
        UserEntity other = newUser(tenant, "60000000003", "Outra", UserType.PROFISSIONAL, admin);
        UserEntity patient = newUser(tenant, "60000000004", "Paciente", UserType.PACIENTE, professional);
        UserEntity stranger = newUser(tenant, "60000000005", "Estranho", UserType.PACIENTE, other);
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Acompanhamento");
        return new Scenario(tenant, admin, professional, other, patient, stranger, dao.update(tenant, pkg));
    }

    @Test
    @DisplayName("tarefas: o profissional passa, o paciente conclui e conta como foi")
    void tasks() {
        Scenario s = scenario("l4_tarefas");
        UUID p = s.patient().getId();

        assertThrows(SecurityException.class, () -> care.addTask(s.tenant(), s.admin(), p, "x", null, null));
        assertThrows(SecurityException.class, () -> care.addTask(s.tenant(), s.other(), p, "x", null, null), "não atende o paciente");
        assertThrows(SecurityException.class, () -> care.addTask(s.tenant(), s.patient(), p, "x", null, null));
        assertThrows(BusinessException.class, () -> care.addTask(s.tenant(), s.professional(), p, " ", null, null));
        UUID late = (UUID) care.addTask(s.tenant(), s.professional(), p, "Diário do sono", "Anotar a hora de deitar",
                LocalDate.now().minusDays(1)).get("id");
        care.addTask(s.tenant(), s.professional(), p, "Caminhada de 20 minutos", null, null);

        List<Map<String, Object>> mine = care.tasks(s.tenant(), s.patient(), null);
        assertEquals(2, mine.size());
        Map<String, Object> first = mine.stream().filter(t -> late.equals(t.get("id"))).findFirst().orElseThrow();
        assertEquals("Anotar a hora de deitar", first.get("description"));
        assertEquals(true, first.get("overdue"));
        assertEquals("Dona Teste", first.get("professionalName"));
        assertTrue(care.tasks(s.tenant(), s.stranger(), p).isEmpty(), "paciente só vê as próprias, mesmo pedindo outro id");
        assertThrows(SecurityException.class, () -> care.tasks(s.tenant(), s.other(), p));

        assertThrows(SecurityException.class, () -> care.completeTask(s.tenant(), s.professional(), late, true, null));
        assertThrows(SecurityException.class, () -> care.completeTask(s.tenant(), s.stranger(), late, true, null));
        care.completeTask(s.tenant(), s.patient(), late, true, "Consegui em 5 dos 7 dias");
        Map<String, Object> done = care.tasks(s.tenant(), s.professional(), p).stream()
                .filter(t -> late.equals(t.get("id"))).findFirst().orElseThrow();
        assertEquals(true, done.get("done"));
        assertEquals(false, done.get("overdue"));
        assertEquals("Consegui em 5 dos 7 dias", done.get("patientNote"));
        assertEquals(1L, care.patientSummary(s.tenant(), s.patient()).get("openTasks"));
        assertEquals(1L, clinic.dashboard(s.tenant(), s.patient()).get("openTasks"));

        assertThrows(SecurityException.class, () -> care.removeTask(s.tenant(), s.patient(), late));
        care.removeTask(s.tenant(), s.professional(), late);
        assertEquals(1, care.tasks(s.tenant(), s.admin(), p).size());
    }

    @Test
    @DisplayName("plano de segurança: escrito pelo profissional, lido pelo paciente com o contato de emergência")
    @SuppressWarnings("unchecked")
    void safetyPlan() {
        Scenario s = scenario("l4_plano");
        UUID p = s.patient().getId();

        assertTrue(((List<?>) care.safetyPlans(s.tenant(), s.patient(), null).get("plans")).isEmpty());
        assertThrows(SecurityException.class, () -> care.saveSafetyPlan(s.tenant(), s.patient(), p, Map.of("coping", "x")));
        assertThrows(SecurityException.class, () -> care.saveSafetyPlan(s.tenant(), s.admin(), p, Map.of("coping", "x")));
        assertThrows(SecurityException.class, () -> care.saveSafetyPlan(s.tenant(), s.other(), p, Map.of("coping", "x")));

        care.saveSafetyPlan(s.tenant(), s.professional(), p, Map.of(
                "warningSigns", "Ficar sem dormir\nIsolar-se", "coping", "Respiração 4-7-8", "supportPeople", "Irmã Ana — 11 91111-2222"));
        care.saveSafetyPlan(s.tenant(), s.professional(), p, Map.of(
                "warningSigns", "Ficar sem dormir", "coping", "Respiração 4-7-8\nBanho morno"));

        Map<String, Object> seen = care.safetyPlans(s.tenant(), s.patient(), null);
        List<Map<String, Object>> plans = (List<Map<String, Object>>) seen.get("plans");
        assertEquals(1, plans.size(), "salvar de novo atualiza o mesmo plano");
        assertEquals("Respiração 4-7-8\nBanho morno", plans.getFirst().get("coping"));
        assertNull(plans.getFirst().get("supportPeople"), "campo apagado na edição fica vazio");
        assertEquals("Mãe", seen.get("emergencyContactName"));
        assertEquals(false, seen.get("canEdit"));
        assertEquals(true, care.safetyPlans(s.tenant(), s.professional(), p).get("canEdit"));
        assertEquals(1, ((List<?>) care.safetyPlans(s.tenant(), s.admin(), p).get("plans")).size());
        assertThrows(SecurityException.class, () -> care.safetyPlans(s.tenant(), s.other(), p));
        assertTrue(((List<?>) care.safetyPlans(s.tenant(), s.stranger(), p).get("plans")).isEmpty());
        assertEquals(true, care.patientSummary(s.tenant(), s.patient()).get("hasSafetyPlan"));
    }

    @Test
    @DisplayName("termo de atendimento online: pedido uma vez, aceito pelo paciente com o nome completo")
    void onlineCareTerm() {
        Scenario s = scenario("l4_termo");
        UUID p = s.patient().getId();

        assertThrows(SecurityException.class, () -> care.requestTerm(s.tenant(), s.other(), p, CareService.ONLINE_CARE));
        assertThrows(SecurityException.class, () -> care.requestTerm(s.tenant(), s.admin(), p, CareService.ONLINE_CARE));
        assertThrows(BusinessException.class, () -> care.requestTerm(s.tenant(), s.professional(), p, "OUTRO"));
        Map<String, Object> term = care.requestTerm(s.tenant(), s.professional(), p, CareService.ONLINE_CARE);
        UUID id = (UUID) term.get("id");
        assertEquals(id, care.requestTerm(s.tenant(), s.professional(), p, CareService.ONLINE_CARE).get("id"), "não duplica");
        String text = term.get("content").toString();
        assertTrue(text.contains("Paciente Teste") && text.contains("600.000.000-04") && text.contains("CRP 06/123456"), text);
        assertTrue(text.contains("188") && text.contains("192"));
        assertEquals(1L, care.patientSummary(s.tenant(), s.patient()).get("pendingTerms"));

        assertThrows(SecurityException.class, () -> care.signTerm(s.tenant(), s.professional(), id, "Dona Teste"));
        assertThrows(SecurityException.class, () -> care.signTerm(s.tenant(), s.stranger(), id, "Estranho Teste"));
        assertThrows(BusinessException.class, () -> care.signTerm(s.tenant(), s.patient(), id, "Paciente"));
        Map<String, Object> signed = care.signTerm(s.tenant(), s.patient(), id, "Paciente Teste");
        assertEquals(true, signed.get("signed"));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> care.signTerm(s.tenant(), s.patient(), id, "Paciente Teste")).getStatus());

        Map<String, Object> seen = care.terms(s.tenant(), s.professional(), p).getFirst();
        assertEquals("Paciente Teste", seen.get("signedName"));
        assertEquals(0L, care.patientSummary(s.tenant(), s.patient()).get("pendingTerms"));
        assertThrows(SecurityException.class, () -> care.terms(s.tenant(), s.other(), p));
    }

    @Test
    @DisplayName("diário: sono junto do humor; sessão online: intercorrências técnicas no registro")
    void sleepAndTechnicalIssues() {
        Scenario s = scenario("l4_sono");
        clinic.saveMood(s.tenant(), s.patient(), LocalDate.now(), 4, "Dia bom", new BigDecimal("6.5"), 3);
        Map<String, Object> entry = clinic.moodDiary(s.tenant(), s.professional(), s.patient().getId()).getFirst();
        assertEquals(0, new BigDecimal("6.5").compareTo((BigDecimal) entry.get("sleepHours")));
        assertEquals(3, entry.get("sleepQuality"));
        assertThrows(BusinessException.class,
                () -> clinic.saveMood(s.tenant(), s.patient(), LocalDate.now(), 4, null, new BigDecimal("30"), null));
        assertThrows(BusinessException.class,
                () -> clinic.saveMood(s.tenant(), s.patient(), LocalDate.now(), 4, null, null, 9));
        // Sem sono informado continua valendo.
        clinic.saveMood(s.tenant(), s.patient(), LocalDate.now().minusDays(1), 2, null);

        UUID session = clinic.schedule(s.tenant(), s.professional(), s.pkg().getId(), LocalDate.now().plusDays(10),
                LocalTime.parse("09:00"), 50, SessionModality.ONLINE, null, "Sessão", 1).getFirst();
        Map<String, Object> body = new HashMap<>();
        body.put("demand", "Relato da semana");
        body.put("technicalIssues", "Conexão caiu aos 20 min; retomada em 3 min pela mesma sala.");
        Map<String, Object> saved = agenda.saveRecord(s.tenant(), s.professional(), session, body);
        assertEquals("ONLINE", saved.get("modality"));
        assertEquals("Conexão caiu aos 20 min; retomada em 3 min pela mesma sala.", saved.get("technicalIssues"));
        // Cliente antigo, que não conhece o campo, não o apaga.
        agenda.saveRecord(s.tenant(), s.professional(), session, Map.of("demand", "Relato revisto"));
        assertFalse(agenda.record(s.tenant(), s.professional(), session).get("technicalIssues") == null);
    }
}
