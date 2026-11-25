package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.AgendaService;
import br.com.mindhaven.application.service.DocumentService;
import br.com.mindhaven.application.service.FinanceService;
import br.com.mindhaven.application.service.RecordsService;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.IssuedDocumentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import core.error.BusinessException;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints do consultório, segunda leva: remarcação, evolução, sessão avulsa, horário de
 * atendimento, documentos em PDF, livro-caixa, anexos e prazo de guarda. Quem pode o quê
 * está nos serviços; aqui só se converte a entrada.
 */
@Path("/clinic")
public class ClinicExtrasController {
    @Inject
    AgendaService agenda;

    @Inject
    DocumentService documents;

    @Inject
    FinanceService finance;

    @Inject
    RecordsService records;

    @Inject
    UserService userService;

    // ------------------------------------------------------------------ agenda

    @PUT
    @Path("/agenda/{id}/reschedule")
    public void reschedule(@PathParam("id") UUID id, Map<String, Object> body) {
        agenda.reschedule(tenant(), me(), id, date(text(body.get("date"))), time(text(body.get("startTime"))));
    }

    @PUT
    @Path("/agenda/{id}/patient-cancel")
    public void patientCancel(@PathParam("id") UUID id) {
        agenda.patientCancel(tenant(), me(), id);
    }

    @GET
    @Path("/agenda/{id}/free-slots")
    public List<String> freeSlots(@PathParam("id") UUID id, @QueryParam("date") String date) {
        LocalDate day = date(date);
        if (day == null) throw BusinessException.invalid("Informe a data.");
        return agenda.freeSlots(tenant(), me(), id, day);
    }

    @GET
    @Path("/agenda/{id}/evolution")
    public Map<String, Object> evolution(@PathParam("id") UUID id) {
        return agenda.evolution(tenant(), me(), id);
    }

    @PUT
    @Path("/agenda/{id}/evolution")
    public void saveEvolution(@PathParam("id") UUID id, Map<String, Object> body) {
        agenda.saveEvolution(tenant(), me(), id, text(body.get("evolution")));
    }

    @POST
    @Path("/agenda/single")
    public Map<String, Object> scheduleSingle(Map<String, Object> body) {
        List<UUID> ids = agenda.scheduleSingle(tenant(), me(),
                uuid(body.get("patientId")), decimal(body.get("price")),
                date(text(body.get("date"))), time(text(body.get("startTime"))),
                integer(body.get("durationMinutes")),
                body.get("modality") == null ? null : SessionModality.valueOf(text(body.get("modality"))),
                text(body.get("meetingLink")));
        return Map.of("created", ids);
    }

    @GET
    @Path("/agenda-settings")
    public Map<String, Object> settings(@QueryParam("from") String from, @QueryParam("to") String to) {
        return agenda.settings(tenant(), me(), date(from), date(to));
    }

    @PUT
    @Path("/agenda-settings/working-hours")
    public void saveWorkingHours(List<Map<String, Object>> intervals) {
        agenda.saveWorkingHours(tenant(), me(), intervals);
    }

    @POST
    @Path("/agenda-settings/blocks")
    public Map<String, Object> addBlock(Map<String, Object> body) {
        return agenda.addBlock(tenant(), me(), dateTime(text(body.get("startAt"))), dateTime(text(body.get("endAt"))),
                text(body.get("reason")));
    }

    @DELETE
    @Path("/agenda-settings/blocks/{id}")
    public void removeBlock(@PathParam("id") UUID id) {
        agenda.removeBlock(tenant(), me(), id);
    }

    // -------------------------------------------------------------- documentos

    @POST
    @Path("/documents")
    public Map<String, Object> issue(Map<String, Object> body) {
        return documents.issue(tenant(), me(), text(body.get("type")), uuid(body.get("referenceId")));
    }

    @GET
    @Path("/documents")
    public List<Map<String, Object>> listDocuments(@QueryParam("patientId") UUID patientId) {
        return documents.list(tenant(), me(), patientId);
    }

    @GET
    @Path("/documents/{id}/pdf")
    public Response pdf(@PathParam("id") UUID id) {
        IssuedDocumentEntity document = documents.pdf(tenant(), me(), id);
        String fileName = document.getDocType().toLowerCase() + "-" + String.format("%04d-%d", document.getDocNumber(),
                document.getDocYear()) + ".pdf";
        return Response.ok(document.getContent(), "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                .build();
    }

    // -------------------------------------------------------------- livro-caixa

    @GET
    @Path("/expenses")
    public List<Map<String, Object>> expenses(@QueryParam("from") String from, @QueryParam("to") String to) {
        LocalDate start = date(from) == null ? LocalDate.now().withDayOfMonth(1) : date(from);
        LocalDate end = date(to) == null ? start.plusMonths(1).minusDays(1) : date(to);
        return finance.expenses(tenant(), me(), start, end);
    }

    @POST
    @Path("/expenses")
    public Map<String, Object> saveExpense(Map<String, Object> body) {
        return finance.saveExpense(tenant(), me(), uuid(body.get("id")), date(text(body.get("date"))),
                text(body.get("description")), text(body.get("category")), decimal(body.get("amount")),
                !"false".equals(String.valueOf(body.get("deductible"))));
    }

    @DELETE
    @Path("/expenses/{id}")
    public void deleteExpense(@PathParam("id") UUID id) {
        finance.deleteExpense(tenant(), me(), id);
    }

    @GET
    @Path("/finance/year")
    public Map<String, Object> yearReport(@QueryParam("year") Integer year) {
        return finance.yearReport(tenant(), me(), year == null ? LocalDate.now().getYear() : year);
    }

    @GET
    @Path("/finance/year.csv")
    public Response yearCsv(@QueryParam("year") Integer year) {
        int target = year == null ? LocalDate.now().getYear() : year;
        return Response.ok(finance.yearCsv(tenant(), me(), target), "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"livro-caixa-" + target + ".csv\"")
                .build();
    }

    // ------------------------------------------------------------------ anexos

    @GET
    @Path("/patient-record/{patientId}/attachments")
    public List<Map<String, Object>> attachments(@PathParam("patientId") UUID patientId) {
        return records.attachments(tenant(), me(), patientId);
    }

    @POST
    @Path("/patient-record/{patientId}/attachments")
    public Map<String, Object> attach(@PathParam("patientId") UUID patientId, Map<String, Object> body) {
        return records.attach(tenant(), me(), patientId, text(body.get("fileName")), text(body.get("contentType")),
                text(body.get("content")), text(body.get("description")));
    }

    @GET
    @Path("/attachments/{id}")
    public Response download(@PathParam("id") UUID id) {
        RecordsService.Download file = records.download(tenant(), me(), id);
        return Response.ok(file.content(), file.contentType() == null ? "application/octet-stream" : file.contentType())
                .header("Content-Disposition", "attachment; filename=\"" + file.fileName().replace("\"", "") + "\"")
                .build();
    }

    @DELETE
    @Path("/attachments/{id}")
    public void removeAttachment(@PathParam("id") UUID id) {
        records.removeAttachment(tenant(), me(), id);
    }

    // ---------------------------------------------------------------- retenção

    @GET
    @Path("/retention")
    public Map<String, Object> retention() {
        return records.retentionReport(tenant(), me());
    }

    // --------------------------------------------------------------- auxiliares

    private String tenant() {
        return userService.getTenant();
    }

    private UserEntity me() {
        UserEntity user = userService.getCurrentLoggedUser();
        if (user == null) throw new SecurityException("Usuário logado não está cadastrado na plataforma.");
        return user;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        return number == null ? null : number.intValue();
    }

    private static BigDecimal decimal(Object value) {
        String text = text(value);
        if (text == null) return null;
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            throw BusinessException.invalid("Número inválido.");
        }
    }

    private static UUID uuid(Object value) {
        String text = text(value);
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("Identificador inválido.");
        }
    }

    private static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.invalid("Data inválida.");
        }
    }

    private static LocalTime time(String value) {
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.invalid("Horário inválido.");
        }
    }

    private static LocalDateTime dateTime(String value) {
        try {
            return value == null ? null : LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.invalid("Data e hora inválidas.");
        }
    }
}
