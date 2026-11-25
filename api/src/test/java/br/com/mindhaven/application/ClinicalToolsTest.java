package br.com.mindhaven.application;

import br.com.mindhaven.application.reminder.ReminderService;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.application.service.ClinicalDocumentService;
import br.com.mindhaven.application.service.DocumentService;
import br.com.mindhaven.application.service.InstrumentService;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Instrumentos clínicos, documentos escritos pelo psicólogo e a situação dos lembretes. */
@QuarkusTest
class ClinicalToolsTest {
    @Inject
    GenericDao dao;
    @Inject
    InstrumentService instruments;
    @Inject
    ClinicalDocumentService written;
    @Inject
    DocumentService documents;
    @Inject
    ClinicService clinic;
    @Inject
    ReminderService reminders;

    private record Scenario(String tenant, UserEntity admin, UserEntity professional, UserEntity other, UserEntity patient) {
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
        UserEntity admin = newUser(tenant, "70000000001", "Admin", UserType.ADMINISTRADOR, null);
        UserEntity professional = newUser(tenant, "70000000002", "Dona", UserType.PROFISSIONAL, admin);
        UserEntity other = newUser(tenant, "70000000003", "Outra", UserType.PROFISSIONAL, admin);
        UserEntity patient = newUser(tenant, "70000000004", "Paciente", UserType.PACIENTE, professional);
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        dao.update(tenant, pkg);
        return new Scenario(tenant, admin, professional, other, patient);
    }

    private static Map<String, Object> phq9(int... values) {
        Map<String, Object> answers = new HashMap<>();
        for (int i = 0; i < values.length; i++) answers.put(String.valueOf(i + 1), values[i]);
        return answers;
    }

    @Test
    @DisplayName("PHQ-9: paciente responde sem ver a nota; profissional vê nota, faixa e o alerta do item 9")
    @SuppressWarnings("unchecked")
    void phq9Flow() {
        Scenario s = scenario("l5_phq9");
        UUID p = s.patient().getId();

        List<Map<String, Object>> catalog = instruments.templates(s.tenant(), s.professional());
        assertTrue(catalog.stream().anyMatch(t -> "PHQ9".equals(t.get("code")) && ((List<?>) t.get("items")).size() == 9));
        assertTrue(catalog.stream().anyMatch(t -> "GAD7".equals(t.get("code")) && ((List<?>) t.get("items")).size() == 7));
        assertThrows(SecurityException.class, () -> instruments.templates(s.tenant(), s.patient()));

        assertThrows(SecurityException.class, () -> instruments.apply(s.tenant(), s.other(), p, "PHQ9", null));
        assertThrows(SecurityException.class, () -> instruments.apply(s.tenant(), s.admin(), p, "PHQ9", null));
        assertThrows(BusinessException.class, () -> instruments.apply(s.tenant(), s.professional(), p, "NAOEXISTE", null));
        UUID first = (UUID) instruments.apply(s.tenant(), s.professional(), p, "PHQ9", 7).get("id");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> instruments.apply(s.tenant(), s.professional(), p, "PHQ9", null)).getStatus(), "já há uma pendente");
        assertEquals(1L, clinic.dashboard(s.tenant(), s.patient()).get("pendingForms"));

        Map<String, Object> pending = instruments.responses(s.tenant(), s.patient(), null).getFirst();
        assertNotNull(pending.get("definition"), "o paciente recebe as perguntas");
        assertFalse(pending.containsKey("score"));

        assertThrows(SecurityException.class, () -> instruments.answer(s.tenant(), s.other(), first, phq9(0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertThrows(BusinessException.class, () -> instruments.answer(s.tenant(), s.patient(), first, phq9(1, 1, 1)), "incompleto");
        assertThrows(BusinessException.class, () -> instruments.answer(s.tenant(), s.patient(), first, phq9(1, 1, 1, 1, 1, 1, 1, 1, 9)));
        Map<String, Object> answered = instruments.answer(s.tenant(), s.patient(), first, phq9(2, 2, 1, 2, 1, 1, 1, 1, 1));
        assertFalse(answered.containsKey("score"), "o paciente não vê a pontuação");
        assertFalse(answered.containsKey("band"));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> instruments.answer(s.tenant(), s.patient(), first, phq9(0, 0, 0, 0, 0, 0, 0, 0, 0))).getStatus());

        List<Map<String, Object>> seen = instruments.responses(s.tenant(), s.professional(), p);
        Map<String, Object> done = seen.stream().filter(r -> first.equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals(12, done.get("score"));
        assertEquals("Moderado", done.get("band"));
        assertEquals(27, done.get("maxScore"));
        assertEquals(true, done.get("alert"), "item 9 acima de zero");
        assertEquals("PACIENTE", done.get("answeredBy"));
        assertEquals(1, ((Map<String, Object>) done.get("answers")).get("9"));

        // A repetição semanal já deixou a próxima aplicação marcada para daqui a 7 dias.
        Map<String, Object> next = seen.stream().filter(r -> !first.equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals(LocalDate.now().plusDays(7).toString(), next.get("dueDate"));
        assertEquals(false, next.get("available"));
        assertEquals(0L, clinic.dashboard(s.tenant(), s.patient()).get("pendingForms"), "a próxima ainda não está liberada");

        List<Map<String, Object>> alerts = (List<Map<String, Object>>) clinic.dashboard(s.tenant(), s.professional()).get("formAlerts");
        assertEquals("Paciente Teste", alerts.getFirst().get("patientName"));
        assertTrue(((List<?>) clinic.dashboard(s.tenant(), s.other()).get("formAlerts")).isEmpty());

        // Respondido faz parte do prontuário; pendente pode ser cancelado (e a repetição para).
        assertThrows(BusinessException.class, () -> instruments.cancel(s.tenant(), s.professional(), first));
        assertThrows(SecurityException.class, () -> instruments.cancel(s.tenant(), s.patient(), (UUID) next.get("id")));
        instruments.cancel(s.tenant(), s.professional(), (UUID) next.get("id"));
        assertEquals(1, instruments.responses(s.tenant(), s.admin(), p).size());
        assertThrows(SecurityException.class, () -> instruments.responses(s.tenant(), s.other(), p));
    }

    @Test
    @DisplayName("instrumento do profissional: escala, sim/não e texto; preenchido na sessão pelo profissional")
    @SuppressWarnings("unchecked")
    void customInstrument() {
        Scenario s = scenario("l5_proprio");
        UUID p = s.patient().getId();
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Check-in semanal");
        body.put("instructions", "Como foi a sua semana?");
        body.put("options", List.of("Nada", "Pouco", "Muito"));
        body.put("items", List.of(
                Map.of("text", "Consegui seguir a rotina combinada", "type", "SCALE"),
                Map.of("text", "Tive crise de ansiedade", "type", "YESNO"),
                Map.of("text", "O que mais marcou a semana", "type", "TEXT")));

        assertThrows(SecurityException.class, () -> instruments.saveTemplate(s.tenant(), s.admin(), null, body));
        assertThrows(BusinessException.class, () -> instruments.saveTemplate(s.tenant(), s.professional(), null, Map.of("title", "Vazio")));
        Map<String, Object> saved = instruments.saveTemplate(s.tenant(), s.professional(), null, body);
        String code = saved.get("code").toString();
        assertEquals(2, saved.get("maxScore"));
        assertTrue(instruments.templates(s.tenant(), s.professional()).stream().anyMatch(t -> code.equals(t.get("code"))));
        assertFalse(instruments.templates(s.tenant(), s.other()).stream().anyMatch(t -> code.equals(t.get("code"))));
        assertThrows(SecurityException.class, () -> instruments.apply(s.tenant(), s.other(), p, code, null));

        UUID applied = (UUID) instruments.apply(s.tenant(), s.professional(), p, code, null).get("id");
        Map<String, Object> answers = new HashMap<>();
        answers.put("1", 2);
        answers.put("2", true);
        answers.put("3", "Voltei a caminhar");
        Map<String, Object> done = instruments.answer(s.tenant(), s.professional(), applied, answers);
        assertEquals(2, done.get("score"));
        assertEquals("PROFISSIONAL", done.get("answeredBy"));
        assertNull(done.get("band"));
        assertEquals("Voltei a caminhar", ((Map<String, Object>) done.get("answers")).get("3"));

        // Apagar o modelo não apaga o que já foi respondido com ele.
        instruments.removeTemplate(s.tenant(), s.professional(), (UUID) saved.get("id"));
        assertEquals(1, instruments.responses(s.tenant(), s.professional(), p).size());
    }

    @Test
    @DisplayName("laudo: rascunho só do autor, emissão em PDF numerado e travado, fora do alcance do paciente")
    @SuppressWarnings("unchecked")
    void writtenDocuments() {
        Scenario s = scenario("l5_laudo");
        UUID p = s.patient().getId();

        Map<String, Object> catalog = written.catalog(s.tenant(), s.professional());
        List<Map<String, Object>> kinds = (List<Map<String, Object>>) catalog.get("kinds");
        Map<String, Object> laudo = kinds.stream().filter(k -> "LAUDO".equals(k.get("code"))).findFirst().orElseThrow();
        assertEquals(6, ((List<?>) laudo.get("sections")).size());
        assertThrows(SecurityException.class, () -> written.catalog(s.tenant(), s.admin()));
        assertThrows(SecurityException.class, () -> written.catalog(s.tenant(), s.patient()));

        assertThrows(SecurityException.class, () -> written.save(s.tenant(), s.other(), null, p, "LAUDO", null, Map.of()));
        assertThrows(BusinessException.class, () -> written.save(s.tenant(), s.professional(), null, p, "BILHETE", null, Map.of()));
        Map<String, Object> draft = written.save(s.tenant(), s.professional(), null, p, "LAUDO", "Laudo para a escola",
                Map.of("identification", "Solicitado pela escola, para fins pedagógicos.", "demand", "Queixa de desatenção.",
                        "intruso", "campo que não existe"));
        UUID id = (UUID) draft.get("id");
        assertFalse(((Map<String, Object>) draft.get("sections")).containsKey("intruso"));
        assertThrows(SecurityException.class, () -> written.save(s.tenant(), s.other(), id, p, "LAUDO", null, Map.of()));
        assertThrows(BusinessException.class, () -> written.issue(s.tenant(), s.professional(), id), "faltam seções");

        Map<String, Object> full = new HashMap<>();
        full.put("identification", "Solicitado pela escola, para fins pedagógicos.");
        full.put("demand", "Queixa de desatenção em sala.");
        full.put("procedure", "Quatro entrevistas e observação lúdica.\n\nEntrevista com os responsáveis.");
        full.put("analysis", "Atenção preservada em tarefas de interesse.");
        full.put("conclusion", "Sem indicativos de transtorno; sugere-se acompanhamento.");
        written.save(s.tenant(), s.professional(), id, p, "LAUDO", "Laudo para a escola", full);
        assertEquals(1, ((List<?>) ((Map<String, Object>) written.saveTemplate(s.tenant(), s.professional(), "LAUDO",
                "Modelo escolar", full)).values().stream().toList()).size());
        assertEquals(1, ((List<?>) written.catalog(s.tenant(), s.professional()).get("templates")).size());
        assertTrue(((List<?>) written.catalog(s.tenant(), s.other()).get("templates")).isEmpty());

        Map<String, Object> issued = written.issue(s.tenant(), s.professional(), id);
        assertEquals(true, issued.get("issued"));
        UUID pdfId = (UUID) issued.get("issuedDocumentId");
        assertEquals(pdfId, written.issue(s.tenant(), s.professional(), id).get("issuedDocumentId"), "emitir de novo não gera outro");
        byte[] pdf = documents.pdf(s.tenant(), s.professional(), pdfId).getContent();
        assertEquals("%PDF", new String(pdf, 0, 4));
        assertTrue(documents.list(s.tenant(), s.professional(), p).stream()
                .anyMatch(d -> d.get("title").toString().startsWith("LAUDO PSICOLÓGICO nº 0001/")));

        // Emitido não muda nem some; e o paciente não o alcança pelo aplicativo.
        assertEquals(409, assertThrows(BusinessException.class,
                () -> written.save(s.tenant(), s.professional(), id, p, "LAUDO", "x", full)).getStatus());
        assertThrows(BusinessException.class, () -> written.remove(s.tenant(), s.professional(), id));
        assertTrue(documents.list(s.tenant(), s.patient(), null).isEmpty());
        assertThrows(SecurityException.class, () -> documents.pdf(s.tenant(), s.patient(), pdfId));
        assertThrows(SecurityException.class, () -> documents.pdf(s.tenant(), s.other(), pdfId));

        // Atestado: uma seção só; rascunho pode ser apagado.
        UUID note = (UUID) written.save(s.tenant(), s.professional(), null, p, "ATESTADO", null,
                Map.of("body", "Atesto, para fins de justificativa de falta, que o paciente esteve em atendimento.")).get("id");
        assertEquals(2, written.list(s.tenant(), s.professional(), p).size());
        written.remove(s.tenant(), s.professional(), note);
        assertEquals(1, written.list(s.tenant(), s.professional(), p).size());
    }

    @Test
    @DisplayName("lembretes: a situação mostra os canais; o teste não envia por canal desligado")
    @SuppressWarnings("unchecked")
    void reminderStatus() {
        Map<String, Object> status = reminders.status();
        List<Map<String, Object>> channels = (List<Map<String, Object>>) status.get("channels");
        Map<String, Object> whatsapp = channels.stream().filter(c -> "WhatsApp".equals(c.get("name"))).findFirst().orElseThrow();
        assertEquals(false, whatsapp.get("enabled"));
        assertNotNull(status.get("hoursBefore"));
        Map<String, Object> result = reminders.sendTest("Ana", null, "11999990000").stream()
                .filter(c -> "WhatsApp".equals(c.get("name"))).findFirst().orElseThrow();
        assertEquals("DESLIGADO", result.get("result"));
    }
}
