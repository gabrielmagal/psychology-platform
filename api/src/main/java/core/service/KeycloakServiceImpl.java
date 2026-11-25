package core.service;

import br.com.mindhaven.domain.entity.UserEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.repository.dao.GenericDao;
import core.security.KeycloakClient;
import core.service.interfaces.KeycloakService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class KeycloakServiceImpl implements KeycloakService {

    private static final Logger LOG = Logger.getLogger(KeycloakServiceImpl.class);

    /** Margem de segurança para renovar o token admin antes de ele expirar de fato. */
    private static final Duration TOKEN_REFRESH_SKEW = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    @Inject
    @KeycloakClient
    HttpClient httpClient;

    /** Token admin em cache. Antes era pedido de novo a cada sub-operação. */
    private volatile CachedToken adminToken;

    /** ids de role por (realm, nome) — a lista de roles do realm quase nunca muda. */
    private final Map<String, String> roleIdCache = new ConcurrentHashMap<>();

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /**
     * Criar um usuário disparava quatro autenticações separadas contra o Keycloak: uma para
     * o POST do usuário e mais uma dentro de cada passo de atribuição de role — todas com o
     * mesmo grant de senha e o mesmo resultado. O token agora é reaproveitado até perto do
     * vencimento.
     */
    public String getAdminAccessToken() {
        CachedToken cached = adminToken;
        if (cached != null && cached.isValid()) {
            return cached.value();
        }

        synchronized (this) {
            cached = adminToken;
            if (cached != null && cached.isValid()) {
                return cached.value();
            }

            String body = "grant_type=password" +
                    "&client_id=admin-cli" +
                    "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = send(req, "obter token admin do Keycloak");
            if (response.statusCode() != 200) {
                throw new RuntimeException("Erro ao autenticar admin: " + response.body());
            }

            JsonObject json = new JsonObject(response.body());
            String accessToken = json.getString("access_token");
            long expiresIn = json.getLong("expires_in", 60L);

            adminToken = new CachedToken(accessToken,
                    Instant.now().plusSeconds(expiresIn).minus(TOKEN_REFRESH_SKEW));
            return accessToken;
        }
    }

    public String createUser(String realm, UserEntity user, Set<String> roles) {
        // A senha inicial era o literal "123456" para todo usuário de todo tenant — uma
        // credencial pública, publicada no repositório. Agora é uma senha provisória aleatória,
        // diferente para cada pessoa. Ela é devolvida UMA vez a quem fez o cadastro, para ser
        // entregue ao usuário, e o Keycloak obriga a trocá-la no primeiro acesso. O e-mail de
        // definição de senha continua sendo enviado quando o realm tem SMTP, mas o primeiro
        // acesso não depende mais dele: sem SMTP o usuário ficava criado e sem como entrar.
        String temporaryPassword = generateTemporaryPassword();
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
                    temporaryPassword
            );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getAdminAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = send(request, "criar usuário no Keycloak");
        if (response.statusCode() == 409) {
            // O login é o CPF: o Keycloak recusa CPF ou e-mail que já têm conta. Sem isto o
            // usuário via só um "erro interno".
            throw core.error.BusinessException.conflict(
                    "Já existe um usuário com este CPF ou e-mail. Confira se a pessoa já não está cadastrada.");
        }
        if (response.statusCode() != 201) {
            throw new RuntimeException("Falha ao criar usuário: " + response.body());
        }

        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new RuntimeException("Keycloak não devolveu o Location do usuário criado"));
        String userId = location.substring(location.lastIndexOf('/') + 1);

        for (String role : roles) {
            assignRealmRole(realm, userId, role);
        }

        sendSetPasswordEmail(realm, userId, user.getEmail());
        user.setTemporaryPassword(temporaryPassword);
        return userId;
    }

    /**
     * Troca a senha do usuário por uma provisória nova e a devolve. Serve para quem esqueceu a
     * senha ou nunca recebeu a primeira; a troca continua obrigatória no próximo acesso.
     */
    public String resetTemporaryPassword(String realm, String keycloakUserId, String email) {
        String temporaryPassword = generateTemporaryPassword();
        String payload = new JsonObject()
                .put("type", "password").put("temporary", true).put("value", temporaryPassword).encode();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/reset-password"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getAdminAccessToken())
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = send(request, "gerar senha provisória no Keycloak");
        if (response.statusCode() != 204) {
            throw new RuntimeException("Falha ao gerar senha provisória: " + response.body());
        }
        return temporaryPassword;
    }

    /**
     * Pede ao Keycloak que envie ao e-mail cadastrado o link de definição de senha
     * (required action {@code UPDATE_PASSWORD}).
     *
     * <p>Best effort deliberado: o envio depende de SMTP configurado no realm. Se falhar, o
     * usuário fica criado e válido — um admin pode reenviar o e-mail ou definir a senha pelo
     * console — e o cadastro não é desfeito por causa do e-mail.
     */
    private void sendSetPasswordEmail(String realm, String userId, String email) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId
                            + "/execute-actions-email?lifespan=" + Duration.ofHours(12).toSeconds()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getAdminAccessToken())
                    .PUT(HttpRequest.BodyPublishers.ofString("[\"UPDATE_PASSWORD\"]"))
                    .build();

            HttpResponse<String> response = send(request, "enviar e-mail de definição de senha");
            if (response.statusCode() != 204) {
                LOG.warnf("Keycloak não enviou o e-mail de definição de senha para %s (HTTP %d): %s. " +
                                "Verifique a configuração de SMTP do realm %s.",
                        email, response.statusCode(), response.body(), realm);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Falha ao solicitar e-mail de definição de senha para %s no realm %s.", email, realm);
        }
    }

    /** Sem 0/O, 1/l/I: a senha é lida numa tela e digitada em outra, muitas vezes ditada. */
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /**
     * Senha provisória: 12 caracteres aleatórios em três blocos (ex.: {@code Xk7m-Pq4R-t9Wc}),
     * cerca de 69 bits. Sempre tem maiúscula, minúscula, número e símbolo, para passar nas
     * políticas de senha usuais do realm.
     */
    private String generateTemporaryPassword() {
        while (true) {
            StringBuilder password = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                if (i > 0 && i % 4 == 0) password.append('-');
                password.append(PASSWORD_ALPHABET.charAt(SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length())));
            }
            String value = password.toString();
            if (value.chars().anyMatch(Character::isUpperCase) && value.chars().anyMatch(Character::isLowerCase)
                    && value.chars().anyMatch(Character::isDigit)) {
                return value;
            }
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
            """.formatted(
                    user.getCpf(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName()
            );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getAdminAccessToken())
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = send(request, "atualizar usuário no Keycloak");
        if (response.statusCode() != 204) {
            throw new RuntimeException("Falha ao atualizar usuário: " + response.body());
        }
        updateUserRoles(realm, userId, roles);
    }

    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        genericDao.defineSchema(tenant);

        List<UserEntity> result = em.createQuery("""
            SELECT u FROM UserEntity u
            WHERE u.keycloakId = :keycloakId
        """, UserEntity.class)
                .setParameter("keycloakId", keycloakId)
                .setMaxResults(1)
                .getResultList();

        if (result.isEmpty()) {
            throw new NotFoundException("Usuário com Keycloak ID não encontrado: " + keycloakId);
        }

        return result.getFirst();
    }

    public void deleteUser(String realm, UUID keycloakUserId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + getAdminAccessToken())
                .DELETE()
                .build();

        HttpResponse<String> response = send(request, "deletar usuário do Keycloak");
        // 404: a conta já não existe, que é exatamente o resultado pedido.
        if (response.statusCode() != 204 && response.statusCode() != 404) {
            throw new RuntimeException("Falha ao deletar usuário: " + response.body());
        }
    }

    private void updateUserRoles(String realm, String userId, Set<String> roles) {
        String token = getAdminAccessToken();

        HttpRequest getRolesRequest = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> rolesResponse = send(getRolesRequest, "buscar roles atuais");
        if (rolesResponse.statusCode() != 200) {
            throw new RuntimeException("Falha ao buscar roles atuais: " + rolesResponse.body());
        }

        try {
            List<Map<String, Object>> currentRoles = MAPPER.readValue(rolesResponse.body(), new TypeReference<>() {});
            if (!currentRoles.isEmpty()) {
                String removePayload = MAPPER.writeValueAsString(currentRoles);
                HttpRequest removeRequest = HttpRequest.newBuilder()
                        .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .method("DELETE", HttpRequest.BodyPublishers.ofString(removePayload))
                        .build();
                send(removeRequest, "remover roles atuais");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao sincronizar roles do usuário", e);
        }

        for (String roleName : roles) {
            assignRealmRole(realm, userId, roleName);
        }
    }

    private void assignRealmRole(String realm, String userId, String roleName) {
        String adminToken = getAdminAccessToken();
        String roleId = resolveRoleId(realm, roleName, adminToken);

        JsonArray rolePayloadArr = new JsonArray()
                .add(new JsonObject().put("id", roleId).put("name", roleName));

        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.ofString(rolePayloadArr.encode(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResp = send(post, "atribuir role " + roleName);
        if (postResp.statusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
            throw new RuntimeException("Falha ao atribuir role " + roleName + ": " + postResp.body());
        }
    }

    /** As roles do realm são estáveis; listar todas elas a cada atribuição era desperdício. */
    private String resolveRoleId(String realm, String roleName, String adminToken) {
        return roleIdCache.computeIfAbsent(realm + '\0' + roleName, key -> {
            HttpRequest rolesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/roles?first=0&max=100"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();

            HttpResponse<String> rolesResp = send(rolesRequest, "listar roles do realm");
            if (rolesResp.statusCode() != 200) {
                throw new RuntimeException("Não foi possível listar roles: " + rolesResp.body());
            }

            JsonArray rolesArr = new JsonArray(rolesResp.body());
            for (int i = 0; i < rolesArr.size(); i++) {
                JsonObject role = rolesArr.getJsonObject(i);
                if (roleName.equals(role.getString("name"))) {
                    return role.getString("id");
                }
            }
            throw new RuntimeException("Role não encontrada: " + roleName);
        });
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrompido ao " + action, e);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao " + action, e);
        }
    }
}
