package br.com.psicologia.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

@ApplicationScoped
public class KeycloakAdminTokenService {

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @ConfigProperty(name = "keycloak.user")
    String username;

    @ConfigProperty(name = "keycloak.password")
    String password;

    @ConfigProperty(name = "keycloak.realm")
    String realm;

    public String getAdminAccessToken() {
        try {
            String body = "grant_type=password" +
                    "&client_id=admin-cli" +
                    "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                return resp.body().split("\"access_token\":\"")[1].split("\"")[0];
            } else {
                throw new RuntimeException("Erro ao autenticar admin: " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter token admin do Keycloak", e);
        }
    }
}
