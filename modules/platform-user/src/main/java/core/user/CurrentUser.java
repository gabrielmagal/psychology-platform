package core.user;

import core.tenant.TenantIdentifier;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Quem está fazendo a requisição e em qual tenant.
 *
 * <p>O usuário é buscado uma vez só por requisição: os serviços perguntam por ele em quase
 * toda operação, e cada busca custa uma consulta ao banco.
 */
@RequestScoped
public class CurrentUser {
    @Context
    HttpHeaders headers;

    @Context
    SecurityContext securityContext;

    @Inject
    UserLookup lookup;

    private BaseUserEntity user;

    /** O tenant da requisição, já validado. */
    public String tenant() {
        return TenantIdentifier.requireValid(headers.getHeaderString("Tenant"));
    }

    /** O usuário autenticado, como a aplicação o enxerga. */
    public BaseUserEntity get() {
        if (user == null) {
            user = lookup.findByKeycloakId(tenant(), securityContext.getUserPrincipal().getName());
        }
        return user;
    }

    /** O mesmo, já no tipo da entidade de usuário da aplicação. */
    public <U extends BaseUserEntity> U get(Class<U> type) {
        return type.cast(get());
    }

    /** Para os pontos em que não ter cadastro é um erro de acesso, e não um "não encontrado". */
    public <U extends BaseUserEntity> U require(Class<U> type) {
        U found = get(type);
        if (found == null) throw new SecurityException("Usuário logado não está cadastrado na plataforma.");
        return found;
    }
}
