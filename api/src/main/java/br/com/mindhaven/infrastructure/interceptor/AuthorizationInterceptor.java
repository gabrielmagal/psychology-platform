package br.com.mindhaven.infrastructure.interceptor;

import core.notes.IRequiredRoles;
import br.com.mindhaven.adapter.controller.dto.ClientSecretDto;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
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

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static br.com.mindhaven.Main.hashClientSecret;

@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class AuthorizationInterceptor implements ContainerRequestFilter {

    @Context
    HttpHeaders headers;

    @Context
    ResourceInfo resourceInfo;

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        String tenant = headers.getHeaderString("Tenant");
        String token = headers.getHeaderString("Authorization");

        if (Objects.nonNull(token) && Objects.nonNull(tenant) && hashClientSecret.containsKey(tenant)) {
            String introspectUrl = keycloakUrl + "/realms/" + tenant + "/protocol/openid-connect/token/introspect";
            ClientSecretDto clientSecret = hashClientSecret.get(tenant);

            String requestBody = "token=" + URLEncoder.encode(token.replace("Bearer ", ""), StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(clientSecret.client, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret.secret, StandardCharsets.UTF_8);

            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(introspectUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> httpResponse = null;
                try (var httpClient = HttpClient.newHttpClient()) {
                    httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                    if (httpResponse.statusCode() != 200) {
                        String MSG_SEM_PERMISSAO = "Você não está autorizado a acessar este recurso.";
                        requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_SEM_PERMISSAO));
                        return;
                    }

                    JsonObject json = new JsonObject(httpResponse.body());
                    if (!json.getBoolean("active", false)) {
                        String MSG_TOKEN_INATIVO = "Você não está autorizado a acessar este recurso. Token inativo.";
                        requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_TOKEN_INATIVO));
                        return;
                    }

                    String keycloakId = json.getString("sub");
                    requestContext.setProperty("keycloakId", keycloakId);

                    Set<String> userRoles = new HashSet<>();
                    JsonObject realmAccess = json.getJsonObject("realm_access");
                    if (realmAccess != null && realmAccess.containsKey("roles")) {
                        realmAccess.getJsonArray("roles").forEach(role -> {
                            if (role instanceof String) userRoles.add((String) role);
                            else userRoles.add(role.toString());
                        });
                    }

                    SecurityContext securityContext = new SecurityContext() {
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
                    };
                    requestContext.setSecurityContext(securityContext);

                    Method method = resourceInfo.getResourceMethod();
                    Class<?> resourceClass = resourceInfo.getResourceClass();

                    IRequiredRoles methodRoles = method.getAnnotation(IRequiredRoles.class);
                    IRequiredRoles classRoles = resourceClass.getAnnotation(IRequiredRoles.class);

                    List<String> required = new ArrayList<>();
                    if (classRoles != null) required.addAll(Arrays.asList(classRoles.value()));
                    if (methodRoles != null) required.addAll(Arrays.asList(methodRoles.value()));

                    if (!required.isEmpty()) {
                        boolean autorizado = required.stream().anyMatch(userRoles::contains);
                        if (!autorizado) {
                            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                                    .entity("Acesso negado. Roles exigidas: " + required)
                                    .build());
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                String MSG_ERRO_INESPERADO = "Você não está autorizado a acessar este recurso. Erro inesperado.";
                requestContext.abortWith(returnAuthorization(Response.Status.INTERNAL_SERVER_ERROR, MSG_ERRO_INESPERADO));
            }
        } else {
            String MSG_SEM_TOKEN = "Você não está autorizado a acessar este recurso. Nenhum token ou tenant válidos foram informados.";
            requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_SEM_TOKEN));
        }
    }

    private Response returnAuthorization(Response.Status statusCode, String msg) {
        return Response.status(statusCode).entity(msg).build();
    }
}