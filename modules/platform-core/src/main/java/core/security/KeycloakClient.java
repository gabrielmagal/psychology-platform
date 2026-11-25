package core.security;

import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Qualifica o {@link java.net.http.HttpClient} compartilhado usado para falar com o Keycloak. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface KeycloakClient {
}
