package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.CareService;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.UserEntity;
import core.error.BusinessException;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tarefas, plano de segurança e termos do paciente. As regras de acesso estão em {@link CareService}. */
@Path("/clinic")
public class CareController {
    @Inject
    CareService care;

    @Inject
    UserService userService;

    @GET
    @Path("/tasks")
    public List<Map<String, Object>> tasks(@QueryParam("patientId") UUID patientId) {
        return care.tasks(tenant(), me(), patientId);
    }

    @POST
    @Path("/tasks")
    public Map<String, Object> addTask(Map<String, Object> body) {
        return care.addTask(tenant(), me(), uuid(body.get("patientId")), text(body.get("title")),
                text(body.get("description")), date(text(body.get("dueDate"))));
    }

    @PUT
    @Path("/tasks/{id}/done")
    public void completeTask(@PathParam("id") UUID id, Map<String, Object> body) {
        Map<String, Object> data = body == null ? Map.of() : body;
        care.completeTask(tenant(), me(), id, !"false".equals(String.valueOf(data.get("done"))), text(data.get("note")));
    }

    @DELETE
    @Path("/tasks/{id}")
    public void removeTask(@PathParam("id") UUID id) {
        care.removeTask(tenant(), me(), id);
    }

    @GET
    @Path("/safety-plan")
    public Map<String, Object> safetyPlans(@QueryParam("patientId") UUID patientId) {
        return care.safetyPlans(tenant(), me(), patientId);
    }

    @PUT
    @Path("/safety-plan/{patientId}")
    public void saveSafetyPlan(@PathParam("patientId") UUID patientId, Map<String, Object> body) {
        care.saveSafetyPlan(tenant(), me(), patientId, body);
    }

    @GET
    @Path("/terms")
    public List<Map<String, Object>> terms(@QueryParam("patientId") UUID patientId) {
        return care.terms(tenant(), me(), patientId);
    }

    @POST
    @Path("/terms")
    public Map<String, Object> requestTerm(Map<String, Object> body) {
        return care.requestTerm(tenant(), me(), uuid(body.get("patientId")),
                body.get("kind") == null ? CareService.ONLINE_CARE : text(body.get("kind")));
    }

    @PUT
    @Path("/terms/{id}/sign")
    public Map<String, Object> signTerm(@PathParam("id") UUID id, Map<String, Object> body) {
        return care.signTerm(tenant(), me(), id, body == null ? null : text(body.get("name")));
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

    private static LocalDate date(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.invalid("Data inválida.");
        }
    }
}
