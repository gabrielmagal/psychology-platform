package core.security;

import io.smallrye.config.ConfigMapping;

import java.util.Map;

/**
 * Credenciais do client confidencial do Keycloak por tenant.
 *
 * <p>Mapeia propriedades no formato:
 * <pre>
 * tenant.clients.&lt;tenant&gt;.client-id=backend
 * tenant.clients.&lt;tenant&gt;.secret=...
 * </pre>
 *
 * <p>Antes esses pares ficavam num {@code HashMap} estático preenchido na
 * {@code Main}, ou seja, <b>client secrets versionados em código</b> e só alteráveis com
 * recompilação. Vindo da configuração, podem ser sobrescritos por variável de ambiente
 * ({@code TENANT_CLIENTS_<TENANT>_SECRET}) sem tocar no binário, e adicionar um tenant novo
 * deixa de ser um deploy.
 */
@ConfigMapping(prefix = "tenant")
public interface TenantClientsConfig {

    Map<String, ClientConfig> clients();

    interface ClientConfig {
        String clientId();

        String secret();
    }
}
