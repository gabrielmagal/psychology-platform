package core.service;

import br.com.psicologia.model.UserEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.repository.dao.GenericDao;
import core.service.interfaces.KeycloakService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class KeycloakServiceImpl implements KeycloakService {

    @ConfigProperty(name = "keycloak.url")
    private String keycloakUrl;

    @ConfigProperty(name = "keycloak.user")
    private String username;

    @ConfigProperty(name = "keycloak.password")
    private String password;

    @ConfigProperty(name = "keycloak.realm")
    private String realm;

    @Inject
    private GenericDao genericDao;

    @Inject
    private EntityManager em;

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

            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body().split("\"access_token\":\"")[1].split("\"")[0];
                } else {
                    throw new RuntimeException("Erro ao autenticar admin: " + response.body());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter token admin do Keycloak", e);
        }
    }

    public String createUser(String realm, UserEntity user, Set<String> roles) {
        String payload = """
        {
          "username": "%s",
          "email": "%s",
          "enabled": true,
          "firstName": "%s",
          "lastName": "%s",
          "requiredActions": ["UPDATE_PASSWORD"],
          "credentials": [
            {
              "type": "password",
              "temporary": true,
              "value": "%s"
            }
          ]
        }
        """.formatted(
                    user.getCpf(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    "123456"
            );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getAdminAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 201) {
                    throw new RuntimeException("Falha ao criar usuário: "+ response.body());
                }
                String location = response.headers().firstValue("Location").orElseThrow();
                String userId = location.substring(location.lastIndexOf('/') + 1);

                for (String role : roles) {
                    assignRealmRole(realm, userId, role);
                }
                return userId;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro integrando com Keycloak", e);
        }
    }

    public void updateUser(String realm, String userId, UserEntity user, Set<String> roles) {
        String payload = """
            {
              "username": "%s",
              "email": "%s",
              "enabled": true,
              "firstName": "%s",
              "lastName": "%s"
            }
            """.formatted
                (
                    user.getCpf(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName()
                );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getAdminAccessToken())
                    .PUT(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 204) {
                    throw new RuntimeException("Falha ao atualizar usuário: " + response.body());
                }
                updateUserRoles(realm, userId, roles);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro integrando com Keycloak", e);
        }
    }

    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        genericDao.defineSchema(tenant);

        List<UserEntity> result = em.createQuery("""
            SELECT u FROM UserEntity u
            WHERE u.keycloakId = :keycloakId
        """, UserEntity.class)
                .setParameter("keycloakId", keycloakId)
                .getResultList();

        if (result.isEmpty()) {
            throw new NotFoundException("Usuário com Keycloak ID não encontrado: " + keycloakId);
        }

        return result.getFirst();
    }

    public void deleteUser(String realm, UUID keycloakUserId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId))
                    .header("Authorization", "Bearer " + getAdminAccessToken())
                    .DELETE()
                    .build();

            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 204) {
                    throw new RuntimeException("Falha ao deletar usuário: " + response.body());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar usuário do Keycloak", e);
        }
    }

    private void updateUserRoles(String realm, String userId, Set<String> roles) throws Exception {
        HttpRequest getRolesRequest = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + getAdminAccessToken())
                .GET()
                .build();

        try (var client = HttpClient.newHttpClient()) {
            HttpResponse<String> rolesResponse = client.send(getRolesRequest, HttpResponse.BodyHandlers.ofString());
            if (rolesResponse.statusCode() != 200) {
                throw new RuntimeException("Falha ao buscar roles atuais: " + rolesResponse.body());
            }

            List<Map<String, Object>> currentRoles = new ObjectMapper().readValue(rolesResponse.body(), new TypeReference<>(){});
            if (!currentRoles.isEmpty()) {
                String removePayload = new ObjectMapper().writeValueAsString(currentRoles);
                HttpRequest removeRequest = HttpRequest.newBuilder()
                        .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + getAdminAccessToken())
                        .method("DELETE", HttpRequest.BodyPublishers.ofString(removePayload))
                        .build();
                client.send(removeRequest, HttpResponse.BodyHandlers.ofString());
            }

            for (String roleName : roles) {
                assignRealmRole(realm, userId, roleName);
            }
        }
    }

    private void assignRealmRole(String realm, String userId, String roleName) {
        try {
            String adminToken = getAdminAccessToken();

            HttpRequest rolesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/roles?first=0&max=100"))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();

            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> rolesResp = client.send(rolesRequest, HttpResponse.BodyHandlers.ofString());
                if (rolesResp.statusCode() != 200) {
                    throw new RuntimeException("Não foi possível listar roles: " + rolesResp.body());
                }

                JsonArray rolesArr = new JsonArray(rolesResp.body());
                String roleId = null;
                for (int i = 0; i < rolesArr.size(); i++) {
                    JsonObject role = rolesArr.getJsonObject(i);
                    if (roleName.equals(role.getString("name"))) {
                        roleId = role.getString("id");
                        break;
                    }
                }
                if (roleId == null) {
                    throw new RuntimeException("Role não encontrada: " + roleName);
                }

                JsonArray rolePayloadArr = new JsonArray()
                        .add(new JsonObject().put("id", roleId).put("name", roleName));

                HttpRequest post = HttpRequest.newBuilder()
                        .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + adminToken)
                        .POST(HttpRequest.BodyPublishers.ofString(rolePayloadArr.encode(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> postResp = client.send(post, HttpResponse.BodyHandlers.ofString());
                if (postResp.statusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
                    throw new RuntimeException("Falha ao atribuir role " + roleName + ": " + postResp.body());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atribuir role", e);
        }
    }
}
