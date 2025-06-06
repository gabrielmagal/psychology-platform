package br.com.psicologia.service;

import br.com.psicologia.controller.dto.ClientSecretDto;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static br.com.psicologia.Main.hashClientSecret;

@Provider
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
public class AuthorizationInterceptorService implements ContainerRequestFilter {
    @Context
    HttpHeaders headers;

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    private final String MSG_SEM_TOKEN = "Você não está autorizado a acessar este recurso. Nenhum token ou tenant válidos foram informados.";
    private final String MSG_ERRO_INESPERADO = "Você não está autorizado a acessar este recurso. Erro inesperado.";
    private final String MSG_SEM_PERMISSAO = "Você não está autorizado a acessar este recurso.";
    private final String MSG_TOKEN_INATIVO = "Você não está autorizado a acessar este recurso. Token inativo.";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var contextUriInfoPath = requestContext.getUriInfo().getPath();

        System.out.println("Interceptando requisição para: " + contextUriInfoPath);

        String tenant = headers.getHeaderString("Tenant");
        String token = headers.getHeaderString("Authorization");

        if (Objects.nonNull(token) && Objects.nonNull(tenant) && hashClientSecret.containsKey(tenant)) {
            String KEYCLOAK_INTROSPECTION_URL = keycloakUrl + "/realms/" + tenant + "/protocol/openid-connect/token/introspect";

            ClientSecretDto clientSecret = hashClientSecret.get(tenant);

            String requestBody = "token=" + URLEncoder.encode(token.replace("Bearer ", ""), StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(clientSecret.client, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret.secret, StandardCharsets.UTF_8);

            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(KEYCLOAK_INTROSPECTION_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (httpResponse.statusCode() == Response.Status.OK.getStatusCode()) {
                    JsonObject jsonObject = new JsonObject(httpResponse.body());
                    if (!jsonObject.getBoolean("active")) {
                        requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_TOKEN_INATIVO));
                    }
                    String keycloakId = jsonObject.getString("sub");
                    requestContext.setProperty("keycloakId", keycloakId);
                }
                else {
                    requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_SEM_PERMISSAO));
                }
            }
            catch (Exception ignored) {
                requestContext.abortWith(returnAuthorization(Response.Status.INTERNAL_SERVER_ERROR, MSG_ERRO_INESPERADO));
            }
        }
        else {
            requestContext.abortWith(returnAuthorization(Response.Status.UNAUTHORIZED, MSG_SEM_TOKEN));
        }
    }

    private Response returnAuthorization(Response.Status statusCode, String msg) {
        return Response.status(statusCode).entity(msg).build();
    }
}