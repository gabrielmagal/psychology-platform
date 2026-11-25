package core.user;

import core.repository.dao.GenericDao;
import core.user.keycloak.CreatedAccount;
import core.user.keycloak.KeycloakService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * O ciclo de vida de um usuário com conta: cadastro, alteração, exclusão e senha. É igual em
 * qualquer aplicação, seja qual for a entidade que estende {@link BaseUserEntity}.
 *
 * <p>O que <b>não</b> está aqui, de propósito: quem pode cadastrar quem e quais perfis
 * existem. Isso é regra de cada sistema; ele decide e então chama este serviço, informando
 * os perfis (roles) da conta.
 */
@ApplicationScoped
public class UserAccounts {
    @Inject
    KeycloakService keycloak;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    /**
     * Cria a conta de acesso e o cadastro, nessa ordem, e devolve o usuário com a senha
     * provisória de primeiro acesso (que não é gravada em lugar nenhum).
     *
     * <p>Se o cadastro falhar no banco — CPF repetido, campo obrigatório — a conta recém-criada
     * é desfeita. Sem isso ela ficaria órfã e o login nunca mais poderia ser cadastrado.
     *
     * @param persist como a aplicação grava a sua entidade; recebe o usuário já com a conta
     *                vinculada e devolve o que foi gravado
     */
    public <U extends BaseUserEntity> U create(String tenant, U user, Set<String> roles, String registeredByKeycloakId,
                                               UnaryOperator<U> persist) {
        CreatedAccount account = keycloak.createUser(tenant, user.toAccount(), roles);
        user.setKeycloakId(account.id());
        user.setRegisteredByKeycloakId(registeredByKeycloakId);
        U saved;
        try {
            saved = persist.apply(user);
            // Grava já: o banco só recusaria um dado repetido no fim da transação, tarde demais
            // para desfazer a conta.
            em.flush();
        } catch (RuntimeException e) {
            try {
                keycloak.deleteUser(tenant, UUID.fromString(account.id()));
            } catch (RuntimeException ignored) {
                // o erro que interessa a quem está cadastrando é o original
            }
            throw e;
        }
        saved.setTemporaryPassword(account.temporaryPassword());
        return saved;
    }

    /** Leva para a conta o que mudou no cadastro (nome, e-mail, login) e os perfis. */
    public void sync(String tenant, BaseUserEntity user, Set<String> roles) {
        keycloak.updateUser(tenant, user.getKeycloakId(), user.toAccount(), roles);
    }

    /** Apaga a conta de acesso. Chame depois de remover o cadastro, na mesma transação. */
    public void remove(String tenant, BaseUserEntity user) {
        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank()) return;
        keycloak.deleteUser(tenant, UUID.fromString(user.getKeycloakId()));
    }

    /** Troca a senha por uma provisória nova e a devolve; a troca é exigida no próximo acesso. */
    public String resetPassword(String tenant, BaseUserEntity user) {
        return keycloak.resetTemporaryPassword(tenant, user.getKeycloakId());
    }

    /**
     * O cadastro de uma conta, na entidade de usuário da aplicação.
     *
     * @throws NotFoundException se a conta não tem cadastro neste tenant
     */
    public <U extends BaseUserEntity> U findByKeycloakId(String tenant, String keycloakId, Class<U> type) {
        dao.defineSchema(tenant);
        List<U> result = em.createQuery("select u from " + type.getName() + " u where u.keycloakId = :keycloakId", type)
                .setParameter("keycloakId", keycloakId)
                .setMaxResults(1)
                .getResultList();
        if (result.isEmpty()) {
            throw new NotFoundException("Usuário com Keycloak ID não encontrado: " + keycloakId);
        }
        return result.getFirst();
    }
}
