package core.user;

/**
 * Como a aplicação encontra o usuário a partir da conta autenticada. É o único ponto que o
 * módulo pede à aplicação: ela sabe qual é a sua entidade de usuário e o que mais precisa
 * vir junto (foto, vínculos).
 *
 * <p>A implementação mais simples delega para {@link UserAccounts#findByKeycloakId}.
 */
public interface UserLookup {
    /**
     * @return o usuário da conta
     * @throws jakarta.ws.rs.NotFoundException se a conta existe no provedor de identidade mas
     *                                         ainda não tem cadastro neste sistema
     */
    BaseUserEntity findByKeycloakId(String tenant, String keycloakId);
}
