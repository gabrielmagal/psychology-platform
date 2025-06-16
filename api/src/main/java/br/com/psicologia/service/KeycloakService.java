package br.com.psicologia.service;

import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class KeycloakService {

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Inject
    KeycloakAdminTokenService keycloakAdminTokenService;

    @Inject
    GenericDao genericDao;

    @Inject
    EntityManager em;

    public String createUser(String realm, UserEntity user, Set<String> roles) {
        //String tempPassword = java.util.UUID.randomUUID().toString();

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
                    //tempPassword
            );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + keycloakAdminTokenService.getAdminAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new RuntimeException("Falha ao criar usuário: "+ response.body());
            }
            String location = response.headers().firstValue("Location").orElseThrow();
            String userId = location.substring(location.lastIndexOf('/') + 1);

            for (String role : roles) {
                assignRealmRole(realm, userId, role);
            }

            return userId;
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
            String adminToken = keycloakAdminTokenService.getAdminAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId))
                    .header("Authorization", "Bearer " + adminToken)
                    .DELETE()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                throw new RuntimeException("Falha ao deletar usuário: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar usuário do Keycloak", e);
        }
    }

    private void assignRealmRole(String realm, String userId, String roleName) {
        try {
            String adminToken = keycloakAdminTokenService.getAdminAccessToken();

            HttpRequest rolesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/roles?first=0&max=100"))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();
            HttpResponse<String> rolesResp = HttpClient.newHttpClient().send(rolesRequest, HttpResponse.BodyHandlers.ofString());
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

            HttpResponse<String> postResp = HttpClient.newHttpClient().send(post, HttpResponse.BodyHandlers.ofString());
            if (postResp.statusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
                throw new RuntimeException("Falha ao atribuir role " + roleName + ": " + postResp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atribuir role", e);
        }
    }
}
