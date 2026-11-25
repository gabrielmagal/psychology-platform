package core.security;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@QuarkusTest
class TokenIntrospectionCacheTest {

    @Inject
    TokenIntrospectionCache cache;

    private TokenIntrospectionCache.Loaded loaded(String subject, long expEpochSeconds) {
        return new TokenIntrospectionCache.Loaded(
                new TokenIntrospectionCache.Introspection(subject, Set.of("PROFISSIONAL_ROLE")),
                expEpochSeconds);
    }

    private String freshToken() {
        return "token-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("o mesmo token não é reintrospectado dentro da janela do cache")
    void reusesIntrospectionWithinTtl() {
        String token = freshToken();
        AtomicInteger calls = new AtomicInteger();

        var first = cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().plusSeconds(300).getEpochSecond());
        });

        var second = cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().plusSeconds(300).getEpochSecond());
        });

        assertEquals(1, calls.get(), "a segunda requisição deveria ser servida do cache");
        assertSame(first, second);
        assertEquals("sub-1", second.subject());
    }

    @Test
    @DisplayName("tokens diferentes não compartilham entrada")
    void doesNotConfuseDifferentTokens() {
        AtomicInteger calls = new AtomicInteger();

        var a = cache.get("barueri", freshToken(), () -> {
            calls.incrementAndGet();
            return loaded("sub-a", Instant.now().plusSeconds(300).getEpochSecond());
        });
        var b = cache.get("barueri", freshToken(), () -> {
            calls.incrementAndGet();
            return loaded("sub-b", Instant.now().plusSeconds(300).getEpochSecond());
        });

        assertEquals(2, calls.get());
        assertEquals("sub-a", a.subject());
        assertEquals("sub-b", b.subject());
    }

    @Test
    @DisplayName("o mesmo token em tenants distintos é validado separadamente")
    void isolatesByTenant() {
        String token = freshToken();
        AtomicInteger calls = new AtomicInteger();

        cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-barueri", Instant.now().plusSeconds(300).getEpochSecond());
        });
        var campinas = cache.get("campinas", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-campinas", Instant.now().plusSeconds(300).getEpochSecond());
        });

        assertEquals(2, calls.get());
        assertEquals("sub-campinas", campinas.subject());
    }

    @Test
    @DisplayName("o TTL nunca ultrapassa a expiração do próprio token")
    void neverOutlivesTheToken() {
        String token = freshToken();
        AtomicInteger calls = new AtomicInteger();

        // Token que já expirou: não pode ser servido do cache na chamada seguinte,
        // mesmo com o TTL configurado em 60s.
        cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().minusSeconds(1).getEpochSecond());
        });
        cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().plusSeconds(300).getEpochSecond());
        });

        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("invalidação remove a entrada")
    void invalidateDropsEntry() {
        String token = freshToken();
        AtomicInteger calls = new AtomicInteger();

        cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().plusSeconds(300).getEpochSecond());
        });
        cache.invalidate("barueri", token);
        cache.get("barueri", token, () -> {
            calls.incrementAndGet();
            return loaded("sub-1", Instant.now().plusSeconds(300).getEpochSecond());
        });

        assertEquals(2, calls.get());
    }
}
