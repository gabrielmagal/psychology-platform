package br.com.mindhaven.infrastructure.interceptor;

import core.notes.IRequiredRoles;
import br.com.mindhaven.adapter.controller.dto.ClientSecretDto;
import core.security.KeycloakClient;
import core.security.TenantClientRegistry;
import core.security.TokenIntrospectionCache;
import core.tenant.TenantIdentifier;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.*;

@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class AuthorizationInterceptor implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthorizationInterceptor.class);

    private static final String MSG_SEM_PERMISSAO = "Você não está autorizado a acessar este recurso.";
    private static final String MSG_TOKEN_INATIVO = "Você não está autorizado a acessar este recurso. Token inativo.";
    private static final String MSG_ERRO_INESPERADO = "Você não está autorizado a acessar este recurso. Erro inesperado.";
    private static final String MSG_SEM_TOKEN = "Você não está autorizado a acessar este recurso. Nenhum token ou tenant válidos foram informados.";

    @Context
    HttpHeaders headers;

    @Context
    ResourceInfo resourceInfo;

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Inject
    @KeycloakClient
    HttpClient httpClient;

    @Inject
    TokenIntrospectionCache introspectionCache;

    @Inject
    TenantClientRegistry tenantClients;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String rawTenant = headers.getHeaderString("Tenant");
        String token = headers.getHeaderString("Authorization");

        String tenant;
        try {
            tenant = TenantIdentifier.requireValid(rawTenant);
        } catch (IllegalArgumentException e) {
            requestContext.abortWith(deny(Response.Status.UNAUTHORIZED, MSG_SEM_TOKEN));
            return;
        }

        ClientSecretDto clientSecret = tenantClients.find(tenant);
        if (token == null || clientSecret == null) {
            requestContext.abortWith(deny(Response.Status.UNAUTHORIZED, MSG_SEM_TOKEN));
            return;
        }

        String bearer = token.replace("Bearer ", "");

        try {
            TokenIntrospectionCache.Introspection introspection =
                    introspectionCache.get(tenant, bearer, () -> introspect(tenant, bearer, clientSecret));

            if (introspection == null) {
                requestContext.abortWith(deny(Response.Status.UNAUTHORIZED, MSG_TOKEN_INATIVO));
                return;
            }

            applySecurityContext(requestContext, introspection);

            List<String> required = requiredRoles();
            if (!required.isEmpty() && required.stream().noneMatch(introspection.roles()::contains)) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity("Acesso negado. Roles exigidas: " + required)
                        .build());
            }
        } catch (UnauthorizedException e) {
            requestContext.abortWith(deny(Response.Status.UNAUTHORIZED, e.getMessage()));
        } catch (Exception e) {
            LOG.error("Falha ao validar token no Keycloak", e);
            requestContext.abortWith(deny(Response.Status.INTERNAL_SERVER_ERROR, MSG_ERRO_INESPERADO));
        }
    }

    private TokenIntrospectionCache.Loaded introspect(String tenant, String bearer, ClientSecretDto clientSecret) {
        String introspectUrl = keycloakUrl + "/realms/" + tenant + "/protocol/openid-connect/token/introspect";

        String requestBody = "token=" + URLEncoder.encode(bearer, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(clientSecret.client, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret.secret, StandardCharsets.UTF_8);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(introspectUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Introspecção interrompida", e);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao consultar o Keycloak", e);
        }

        if (httpResponse.statusCode() != 200) {
            throw new UnauthorizedException(MSG_SEM_PERMISSAO);
        }

        JsonObject json = new JsonObject(httpResponse.body());
        if (!json.getBoolean("active", false)) {
            throw new UnauthorizedException(MSG_TOKEN_INATIVO);
        }

        Set<String> userRoles = new HashSet<>();
        JsonObject realmAccess = json.getJsonObject("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            realmAccess.getJsonArray("roles").forEach(role -> userRoles.add(String.valueOf(role)));
        }

        var introspection = new TokenIntrospectionCache.Introspection(
                json.getString("sub"), Set.copyOf(userRoles));

        return new TokenIntrospectionCache.Loaded(introspection, json.getLong("exp", 0L));
    }

    private void applySecurityContext(ContainerRequestContext requestContext,
                                      TokenIntrospectionCache.Introspection introspection) {
        String keycloakId = introspection.subject();
        Set<String> userRoles = introspection.roles();

        requestContext.setProperty("keycloakId", keycloakId);
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return () -> keycloakId;
            }

            @Override
            public boolean isUserInRole(String role) {
                return userRoles.contains(role);
            }

            @Override
            public boolean isSecure() {
                return requestContext.getUriInfo().getRequestUri().getScheme().equalsIgnoreCase("https");
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    private List<String> requiredRoles() {
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        IRequiredRoles methodRoles = method == null ? null : method.getAnnotation(IRequiredRoles.class);
        IRequiredRoles classRoles = resourceClass == null ? null : resourceClass.getAnnotation(IRequiredRoles.class);

        List<String> required = new ArrayList<>();
        if (classRoles != null) required.addAll(Arrays.asList(classRoles.value()));
        if (methodRoles != null) required.addAll(Arrays.asList(methodRoles.value()));
        return required;
    }

    private Response deny(Response.Status statusCode, String msg) {
        return Response.status(statusCode).entity(msg).build();
    }

    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
        }
    }
}
