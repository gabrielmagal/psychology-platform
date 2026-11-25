package core.user.keycloak;

import java.util.Set;
import java.util.UUID;

/**
 * Contas de usuário no Keycloak. O realm é o tenant. Não conhece a entidade de usuário de
 * nenhuma aplicação: recebe e devolve só os dados da conta.
 */
public interface KeycloakService {
    String getAdminAccessToken();

    /** Cria a conta com senha provisória, que o Keycloak obriga a trocar no primeiro acesso. */
    CreatedAccount createUser(String realm, KeycloakAccount account, Set<String> roles);

    void updateUser(String realm, String userId, KeycloakAccount account, Set<String> roles);

    /** Apagar uma conta que já não existe não é erro. */
    void deleteUser(String realm, UUID keycloakUserId);

    /** Troca a senha por uma provisória nova e a devolve. */
    String resetTemporaryPassword(String realm, String keycloakUserId);
}
