package br.com.mindhaven.infrastructure.interceptor;

import core.security.TenantClientRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Smoke test do caminho de autenticação: garante que o filtro está registrado e que os
 * clients por tenant vêm da configuração (antes vinham de um HashMap estático no código).
 */
@QuarkusTest
class AuthorizationInterceptorTest {

    @Inject
    TenantClientRegistry tenantClients;

    @Test
    @DisplayName("requisição sem token é barrada antes de tocar o banco")
    void rejectsRequestWithoutToken() {
        given()
                .header("Tenant", "barueri")
                .when().get("/api/user")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("requisição sem tenant é barrada")
    void rejectsRequestWithoutTenant() {
        given()
                .header("Authorization", "Bearer qualquer-coisa")
                .when().get("/api/user")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("tenant com payload de injeção é barrado sem chegar ao Keycloak nem ao banco")
    void rejectsMaliciousTenantHeader() {
        given()
                .header("Tenant", "public; DROP SCHEMA public CASCADE; --")
                .header("Authorization", "Bearer qualquer-coisa")
                .when().get("/api/user")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("tenant desconhecido é barrado")
    void rejectsUnknownTenant() {
        given()
                .header("Tenant", "tenant_inexistente")
                .header("Authorization", "Bearer qualquer-coisa")
                .when().get("/api/user")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("clients por tenant são lidos da configuração")
    void loadsTenantClientsFromConfiguration() {
        var barueri = tenantClients.find("barueri");
        assertNotNull(barueri);
        assertEquals("backend", barueri.client);

        // A busca é insensível a maiúsculas, como a normalização do header Tenant.
        assertNotNull(tenantClients.find("BARUERI"));
        assertNull(tenantClients.find("nao_configurado"));
    }
}
