package core.user.keycloak;

/** Os dados de uma pessoa que vão para a conta no Keycloak. */
public record KeycloakAccount(String username, String email, String firstName, String lastName) {
}
