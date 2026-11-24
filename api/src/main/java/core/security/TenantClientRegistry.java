package core.security;

import br.com.mindhaven.adapter.controller.dto.ClientSecretDto;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolve, a partir da configuração, o client Keycloak de cada tenant. */
@ApplicationScoped
public class TenantClientRegistry {

    private static final Logger LOG = Logger.getLogger(TenantClientRegistry.class);

    @Inject
    TenantClientsConfig config;

    private Map<String, ClientSecretDto> clients;

    @PostConstruct
    void init() {
        clients = config.clients().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey().toLowerCase(Locale.ROOT),
                        e -> new ClientSecretDto(e.getValue().clientId(), e.getValue().secret())));

        LOG.infof("Tenants configurados: %s", clients.keySet());
    }

    /** @return o client do tenant, ou {@code null} se o tenant não estiver configurado */
    public ClientSecretDto find(String tenant) {
        if (tenant == null) return null;
        return clients.get(tenant.toLowerCase(Locale.ROOT));
    }

    public Set<String> tenants() {
        return clients.keySet();
    }
}
