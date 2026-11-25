package core.user.keycloak;

/**
 * Conta recém-criada: o identificador dela no Keycloak e a senha provisória de primeiro
 * acesso, que só existe aqui — entregue-a a quem cadastrou e não a grave.
 */
public record CreatedAccount(String id, String temporaryPassword) {
}
