package br.com.mindhaven.payment;

import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.UserEntity;
import core.notes.IPublic;
import core.security.TenantClientRegistry;
import core.tenant.TenantIdentifier;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/online-payment")
public class OnlinePaymentController {
    @Inject
    OnlinePaymentService payments;

    @Inject
    UserService userService;

    @Inject
    TenantClientRegistry tenants;

    @GET
    @Path("/account")
    public Map<String, Object> account() {
        return payments.account(userService.getTenant(), me());
    }

    @PUT
    @Path("/account")
    public void saveAccount(Map<String, Object> body) {
        Object apiKey = body.get("apiKey");
        boolean sandbox = !Boolean.FALSE.equals(body.get("sandbox")) && !"false".equals(String.valueOf(body.get("sandbox")));
        payments.saveAccount(userService.getTenant(), me(), apiKey == null ? null : apiKey.toString(), sandbox);
    }

    @DELETE
    @Path("/account")
    public void removeAccount() {
        payments.removeAccount(userService.getTenant(), me());
    }

    @POST
    @Path("/charge/{packageId}")
    public Map<String, Object> charge(@PathParam("packageId") UUID packageId) {
        return payments.charge(userService.getTenant(), me(), packageId);
    }

    @POST
    @Path("/sync/{packageId}")
    public Map<String, Object> sync(@PathParam("packageId") UUID packageId) {
        return payments.sync(userService.getTenant(), me(), packageId);
    }

    /**
     * Aviso do Asaas. Não tem usuário logado: a prova de origem é o segredo do cabeçalho
     * {@code asaas-access-token}, conferido contra o da conta do profissional da cobrança.
     * Só tenants configurados são aceitos — o nome vem da URL e não pode criar schema.
     */
    @POST
    @Path("/webhook/{tenant}")
    @IPublic
    public Response webhook(@PathParam("tenant") String tenant,
                            @HeaderParam("asaas-access-token") String token,
                            Map<String, Object> body) {
        String validTenant;
        try {
            validTenant = TenantIdentifier.requireValid(tenant);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (tenants.find(validTenant) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Object payment = body == null ? null : body.get("payment");
        Object chargeId = payment instanceof Map<?, ?> map ? map.get("id") : null;
        boolean accepted = payments.webhook(validTenant, token, chargeId == null ? null : chargeId.toString());
        return accepted ? Response.ok().build() : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    private UserEntity me() {
        UserEntity user = userService.getCurrentLoggedUser();
        if (user == null) {
            throw new SecurityException("Usuário logado não está cadastrado na plataforma.");
        }
        return user;
    }
}
