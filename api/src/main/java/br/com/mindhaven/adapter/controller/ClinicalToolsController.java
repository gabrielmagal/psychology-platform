package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.reminder.ReminderService;
import br.com.mindhaven.application.service.ClinicalDocumentService;
import br.com.mindhaven.application.service.InstrumentService;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Instrumentos clínicos (escalas e formulários), documentos escritos pelo psicólogo e a
 * situação dos lembretes automáticos. As regras de acesso estão nos serviços.
 */
@Path("/clinic")
public class ClinicalToolsController {
    @Inject
    InstrumentService instruments;

    @Inject
    ClinicalDocumentService documents;

    @Inject
    ReminderService reminders;

    @Inject
    UserService userService;

    // ------------------------------------------------------------ instrumentos

    @GET
    @Path("/instruments")
    public List<Map<String, Object>> templates() {
        return instruments.templates(tenant(), me());
    }

    @POST
    @Path("/instruments")
    public Map<String, Object> saveTemplate(Map<String, Object> body) {
        return instruments.saveTemplate(tenant(), me(), uuid(body.get("id")), body);
    }

    @DELETE
    @Path("/instruments/{id}")
    public void removeTemplate(@PathParam("id") UUID id) {
        instruments.removeTemplate(tenant(), me(), id);
    }

    @GET
    @Path("/forms")
    public List<Map<String, Object>> responses(@QueryParam("patientId") UUID patientId) {
        return instruments.responses(tenant(), me(), patientId);
    }

    @POST
    @Path("/forms")
    public Map<String, Object> apply(Map<String, Object> body) {
        String repeat = text(body.get("repeatDays"));
        Integer repeatDays;
        try {
            repeatDays = repeat == null ? null : Integer.valueOf(repeat);
        } catch (NumberFormatException e) {
            throw BusinessException.invalid("Repetição inválida.");
        }
        return instruments.apply(tenant(), me(), uuid(body.get("patientId")), text(body.get("templateCode")), repeatDays);
    }

    @PUT
    @Path("/forms/{id}/answers")
    @SuppressWarnings("unchecked")
    public Map<String, Object> answer(@PathParam("id") UUID id, Map<String, Object> body) {
        Object answers = body == null ? null : body.get("answers");
        return instruments.answer(tenant(), me(), id, answers instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
    }

    @DELETE
    @Path("/forms/{id}")
    public void cancel(@PathParam("id") UUID id) {
        instruments.cancel(tenant(), me(), id);
    }

    // ------------------------------------------------- documentos do psicólogo

    @GET
    @Path("/written-documents/catalog")
    public Map<String, Object> catalog() {
        return documents.catalog(tenant(), me());
    }

    @GET
    @Path("/written-documents")
    public List<Map<String, Object>> listDocuments(@QueryParam("patientId") UUID patientId) {
        return documents.list(tenant(), me(), patientId);
    }

    @POST
    @Path("/written-documents")
    public Map<String, Object> saveDocument(Map<String, Object> body) {
        return documents.save(tenant(), me(), uuid(body.get("id")), uuid(body.get("patientId")), text(body.get("kind")),
                text(body.get("title")), sections(body));
    }

    @PUT
    @Path("/written-documents/{id}/issue")
    public Map<String, Object> issue(@PathParam("id") UUID id) {
        return documents.issue(tenant(), me(), id);
    }

    @DELETE
    @Path("/written-documents/{id}")
    public void removeDocument(@PathParam("id") UUID id) {
        documents.remove(tenant(), me(), id);
    }

    @POST
    @Path("/written-documents/templates")
    public Map<String, Object> saveDocumentTemplate(Map<String, Object> body) {
        return documents.saveTemplate(tenant(), me(), text(body.get("kind")), text(body.get("title")), sections(body));
    }

    @DELETE
    @Path("/written-documents/templates/{id}")
    public void removeDocumentTemplate(@PathParam("id") UUID id) {
        documents.removeTemplate(tenant(), me(), id);
    }

    // ---------------------------------------------------------------- lembretes

    @GET
    @Path("/reminders/status")
    public Map<String, Object> reminderStatus() {
        requireStaff();
        return reminders.status();
    }

    /** Lembrete de exemplo para o próprio administrador, pelos canais ligados. */
    @POST
    @Path("/reminders/test")
    public List<Map<String, Object>> reminderTest() {
        UserEntity user = me();
        if (user.getUserType() != UserType.ADMINISTRADOR) {
            throw new SecurityException("O teste de lembretes é feito pelo administrador da clínica.");
        }
        return reminders.sendTest(user.getFirstName(), user.getEmail(), user.getPhoneNumber());
    }

    // --------------------------------------------------------------- auxiliares

    private void requireStaff() {
        if (me().getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita à equipe da clínica.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sections(Map<String, Object> body) {
        return body.get("sections") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

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

    private static UUID uuid(Object value) {
        String text = text(value);
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("Identificador inválido.");
        }
    }
}
