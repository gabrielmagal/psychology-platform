package core.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Cache de curta duração para o resultado da introspecção de token no Keycloak.
 *
 * <p>Antes, <b>toda</b> requisição da API disparava um POST síncrono para o endpoint de
 * introspecção do Keycloak antes de qualquer trabalho útil. Isso colocava a latência do
 * Keycloak — e a disponibilidade dele — no caminho crítico de cada chamada, e um dashboard
 * que faz meia dúzia de requisições em paralelo virava meia dúzia de idas ao Keycloak.
 *
 * <p>O resultado passa a ser reaproveitado por uma janela curta (padrão 60s), limitada
 * também pelo {@code exp} do próprio token — um token que expira em 10s nunca é servido do
 * cache por 60s. O custo dessa escolha é que uma revogação explícita no Keycloak leva até
 * o TTL para surtir efeito; ajuste {@code keycloak.introspection.cache-ttl-seconds} para
 * zero se o cenário exigir revogação imediata.
 */
@ApplicationScoped
public class TokenIntrospectionCache {

    /** Teto de segurança: acima disso o cache é limpo para não virar vazamento de memória. */
    private static final int MAX_ENTRIES = 10_000;

    @ConfigProperty(name = "keycloak.introspection.cache-ttl-seconds", defaultValue = "60")
    long ttlSeconds;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public record Introspection(String subject, Set<String> roles) {
    }

    private record Entry(Introspection value, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /**
     * Devolve a introspecção em cache ou executa {@code loader} e guarda o resultado.
     *
     * @param tokenExpiresAtEpochSeconds valor do claim {@code exp} devolvido pelo Keycloak
     *                                   (0 se ausente); limita o TTL do cache
     */
    public Introspection get(String tenant, String rawToken, Supplier<Loaded> loader) {
        if (ttlSeconds <= 0) {
            misses.incrementAndGet();
            return loader.get().introspection();
        }

        String key = cacheKey(tenant, rawToken);

        Entry cached = cache.get(key);
        if (cached != null && cached.isValid()) {
            hits.incrementAndGet();
            return cached.value();
        }

        misses.incrementAndGet();
        Loaded loaded = loader.get();
        if (loaded == null || loaded.introspection() == null) {
            cache.remove(key);
            return null;
        }

        if (cache.size() >= MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> !e.getValue().isValid());
            if (cache.size() >= MAX_ENTRIES) cache.clear();
        }

        cache.put(key, new Entry(loaded.introspection(), expiryFor(loaded.tokenExpiresAtEpochSeconds())));
        return loaded.introspection();
    }

    public void invalidate(String tenant, String rawToken) {
        cache.remove(cacheKey(tenant, rawToken));
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    private Instant expiryFor(long tokenExpiresAtEpochSeconds) {
        Instant byTtl = Instant.now().plusSeconds(ttlSeconds);
        if (tokenExpiresAtEpochSeconds <= 0) return byTtl;

        Instant byToken = Instant.ofEpochSecond(tokenExpiresAtEpochSeconds);
        return byToken.isBefore(byTtl) ? byToken : byTtl;
    }

    /** O token nunca é guardado em claro na memória do processo — só o hash dele. */
    private String cacheKey(String tenant, String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((tenant + '\0' + rawToken).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Resultado de uma introspecção nova, com o {@code exp} do token para limitar o TTL. */
    public record Loaded(Introspection introspection, long tokenExpiresAtEpochSeconds) {
    }
}
