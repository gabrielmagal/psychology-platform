package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.SessionModality;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints do consultório (agenda, prontuário, documentos, financeiro, LGPD, diário).
 * A regra de quem pode o quê fica em {@link ClinicService}; aqui só se resolve o tenant e o
 * usuário logado e se converte a entrada.
 */
@Path("/clinic")
public class ClinicController {
    @Inject
    ClinicService clinic;

    @Inject
    UserService userService;

    // ------------------------------------------------------------------ agenda

    @GET
    @Path("/agenda")
    public List<Map<String, Object>> agenda(@QueryParam("from") String from, @QueryParam("to") String to) {
        return clinic.agenda(tenant(), me(), date(from), date(to));
    }

    @POST
    @Path("/agenda")
    public Map<String, Object> schedule(Map<String, Object> body) {
        List<UUID> ids = clinic.schedule(tenant(), me(),
                uuid(body.get("sessionPackageId")),
                date(string(body.get("date"))),
                time(string(body.get("startTime"))),
                integer(body.get("durationMinutes")),
                body.get("modality") == null ? null : SessionModality.valueOf(string(body.get("modality"))),
                string(body.get("meetingLink")),
                string(body.get("title")),
                integer(body.get("repeatWeeks")) == null ? 1 : integer(body.get("repeatWeeks")));
        return Map.of("created", ids);
    }

    @PUT
    @Path("/agenda/{id}/status/{status}")
    public void changeStatus(@PathParam("id") UUID id, @PathParam("status") SessionStatus status) {
        clinic.changeStatus(tenant(), me(), id, status);
    }

    @PUT
    @Path("/agenda/{id}/confirm")
    public void confirm(@PathParam("id") UUID id) {
        clinic.confirm(tenant(), me(), id);
    }

    @PUT
    @Path("/agenda/{id}/reminded")
    public void reminded(@PathParam("id") UUID id) {
        clinic.markReminded(tenant(), me(), id);
    }

    @PUT
    @Path("/agenda/{id}/rating")
    public void rate(@PathParam("id") UUID id, Map<String, Object> body) {
        Integer rating = integer(body.get("rating"));
        clinic.rate(tenant(), me(), id, rating == null ? 0 : rating, string(body.get("comment")));
    }

    // -------------------------------------------------------------- prontuário

    @GET
    @Path("/patient-record/{patientId}")
    public Map<String, Object> patientRecord(@PathParam("patientId") UUID patientId) {
        return clinic.patientRecord(tenant(), me(), patientId);
    }

    @PUT
    @Path("/patient-record/{patientId}")
    public void savePatientRecord(@PathParam("patientId") UUID patientId, Map<String, String> body) {
        clinic.savePatientRecord(tenant(), me(), patientId, body);
    }

    @GET
    @Path("/patient-record/{patientId}/timeline")
    public List<Map<String, Object>> timeline(@PathParam("patientId") UUID patientId) {
        return clinic.timeline(tenant(), me(), patientId);
    }

    // -------------------------------------------------------------- documentos

    @GET
    @Path("/document/receipt/{paymentId}")
    public Map<String, Object> receipt(@PathParam("paymentId") UUID paymentId) {
        return clinic.receipt(tenant(), me(), paymentId);
    }

    @GET
    @Path("/document/attendance/{sessionId}")
    public Map<String, Object> attendance(@PathParam("sessionId") UUID sessionId) {
        return clinic.attendance(tenant(), me(), sessionId);
    }

    // -------------------------------------------------------------- financeiro

    @GET
    @Path("/finance")
    public Map<String, Object> finance(@QueryParam("from") String from, @QueryParam("to") String to) {
        return clinic.finance(tenant(), me(), date(from), date(to));
    }

    // -------------------------------------------------------------------- LGPD

    @GET
    @Path("/consent")
    public Map<String, Object> consentStatus() {
        return clinic.consentStatus(tenant(), me());
    }

    @POST
    @Path("/consent")
    public void acceptConsent() {
        clinic.acceptConsent(tenant(), me());
    }

    @GET
    @Path("/my-data")
    public Map<String, Object> exportMyData() {
        return clinic.exportMyData(tenant(), me());
    }

    // ---------------------------------------------------------- diário de humor

    @GET
    @Path("/mood")
    public List<Map<String, Object>> moodDiary(@QueryParam("patientId") UUID patientId) {
        return clinic.moodDiary(tenant(), me(), patientId);
    }

    @POST
    @Path("/mood")
    public void saveMood(Map<String, Object> body) {
        Integer mood = integer(body.get("mood"));
        clinic.saveMood(tenant(), me(), date(string(body.get("date"))), mood == null ? 0 : mood,
                string(body.get("note")));
    }

    // ------------------------------------------------------------------ painel

    @GET
    @Path("/dashboard")
    public Map<String, Object> dashboard() {
        return clinic.dashboard(tenant(), me());
    }

    // --------------------------------------------------------------- auxiliares

    private String tenant() {
        return userService.getTenant();
    }

    private UserEntity me() {
        UserEntity user = userService.getCurrentLoggedUser();
        if (user == null) {
            throw new SecurityException("Usuário logado não está cadastrado na plataforma.");
        }
        return user;
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(Object value) {
        String text = string(value);
        if (text == null) return null;
        try {
            return new java.math.BigDecimal(text).intValue();
        } catch (NumberFormatException e) {
            throw core.error.BusinessException.invalid("Número inválido.");
        }
    }

    private static UUID uuid(Object value) {
        String text = string(value);
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw core.error.BusinessException.invalid("Identificador inválido.");
        }
    }

    private static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw core.error.BusinessException.invalid("Data inválida.");
        }
    }

    private static LocalTime time(String value) {
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw core.error.BusinessException.invalid("Horário inválido.");
        }
    }
}
