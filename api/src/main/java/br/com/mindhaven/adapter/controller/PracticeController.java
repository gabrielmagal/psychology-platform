package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.FinanceService;
import br.com.mindhaven.application.service.InsuranceService;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.application.service.WaitlistService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints da gestão do consultório: convênios, lista de espera e o controle do Receita
 * Saúde. Quem pode o quê está nos serviços; aqui só se converte a entrada.
 */
@Path("/clinic")
public class PracticeController {
    @Inject
    InsuranceService insurances;

    @Inject
    WaitlistService waitlist;

    @Inject
    FinanceService finance;

    @Inject
    UserService userService;

    // --------------------------------------------------------------- convênios

    @GET
    @Path("/insurances")
    public List<Map<String, Object>> insurances() {
        return insurances.list(tenant(), me());
    }

    @POST
    @Path("/insurances")
    public Map<String, Object> saveInsurance(Map<String, Object> body) {
        return insurances.save(tenant(), me(), uuid(body.get("id")), text(body.get("name")), text(body.get("ansCode")),
                decimal(body.get("sessionValue")), integer(body.get("paymentDays")), text(body.get("notes")),
                !"false".equals(String.valueOf(body.get("active"))));
    }

    @DELETE
    @Path("/insurances/{id}")
    public void removeInsurance(@PathParam("id") UUID id) {
        insurances.remove(tenant(), me(), id);
    }

    @GET
    @Path("/insurance-packages")
    public List<Map<String, Object>> insurancePackages() {
        return insurances.packages(tenant(), me());
    }

    @PUT
    @Path("/insurance-packages/{packageId}")
    public void linkPackage(@PathParam("packageId") UUID packageId, Map<String, Object> body) {
        insurances.linkPackage(tenant(), me(), packageId, uuid(body.get("insuranceId")), text(body.get("cardNumber")));
    }

    @GET
    @Path("/insurance-sessions")
    public Map<String, Object> insuranceSessions(@QueryParam("from") String from, @QueryParam("to") String to,
                                                 @QueryParam("insuranceId") UUID insuranceId) {
        return insurances.sessions(tenant(), me(), date(from), date(to), insuranceId);
    }

    @PUT
    @Path("/insurance-sessions/{sessionId}")
    public void updateInsuranceSession(@PathParam("sessionId") UUID sessionId, Map<String, Object> body) {
        insurances.updateSession(tenant(), me(), sessionId, body);
    }

    // ----------------------------------------------------------- Receita Saúde

    @GET
    @Path("/tax-receipts")
    public Map<String, Object> taxReceipts(@QueryParam("year") Integer year) {
        return finance.taxReceipts(tenant(), me(), year == null ? LocalDate.now().getYear() : year);
    }

    @PUT
    @Path("/tax-receipts/{paymentId}")
    public void markTaxReceipt(@PathParam("paymentId") UUID paymentId, Map<String, Object> body) {
        finance.markTaxReceipt(tenant(), me(), paymentId, !"false".equals(String.valueOf(body.get("launched"))),
                text(body.get("receiptNumber")));
    }

    // ---------------------------------------------------------- lista de espera

    @GET
    @Path("/waitlist")
    public List<Map<String, Object>> waitlist(@QueryParam("all") boolean all) {
        return waitlist.list(tenant(), me(), all);
    }

    @POST
    @Path("/waitlist")
    public Map<String, Object> addToWaitlist(Map<String, Object> body) {
        return waitlist.add(tenant(), me(), uuid(body.get("patientId")), text(body.get("name")), text(body.get("phone")),
                text(body.get("preference")), text(body.get("modality")), text(body.get("notes")),
                uuid(body.get("professionalId")));
    }

    @PUT
    @Path("/waitlist/{id}/status/{status}")
    public void changeWaitlistStatus(@PathParam("id") UUID id, @PathParam("status") String status) {
        waitlist.changeStatus(tenant(), me(), id, status);
    }

    @DELETE
    @Path("/waitlist/{id}")
    public void removeFromWaitlist(@PathParam("id") UUID id) {
        waitlist.remove(tenant(), me(), id);
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

    private static BigDecimal decimal(Object value) {
        String text = text(value);
        try {
            return text == null ? null : new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            throw BusinessException.invalid("Número inválido.");
        }
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        return number == null ? null : number.intValue();
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
}
