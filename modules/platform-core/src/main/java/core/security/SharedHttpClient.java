package core.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Um único {@link HttpClient} compartilhado para as chamadas ao Keycloak.
 *
 * <p>O código anterior fazia {@code HttpClient.newHttpClient()} dentro de um
 * try-with-resources em cada chamada. Cada instância traz o próprio pool de conexões e o
 * próprio executor de threads, então isso significava criar e destruir threads a cada
 * requisição HTTP da API — além de jogar fora toda conexão TCP/TLS já estabelecida com o
 * Keycloak. Reusando um cliente as conexões ficam vivas (HTTP/2 e keep-alive) e o custo
 * por chamada cai para praticamente zero.
 */
@ApplicationScoped
public class SharedHttpClient {

    @Produces
    @ApplicationScoped
    @KeycloakClient
    public HttpClient keycloakHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
