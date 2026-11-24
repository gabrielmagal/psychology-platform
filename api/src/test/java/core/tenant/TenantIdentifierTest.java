package core.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantIdentifierTest {

    @Test
    @DisplayName("aceita e normaliza os tenants em uso")
    void acceptsKnownTenants() {
        assertEquals("barueri", TenantIdentifier.requireValid("barueri"));
        assertEquals("campinas", TenantIdentifier.requireValid("  Campinas  "));
        assertEquals("doacoesalimenticias", TenantIdentifier.requireValid("DoacoesAlimenticias"));
        assertEquals("petro_doacoes", TenantIdentifier.requireValid("petro_doacoes"));
    }

    @ParameterizedTest
    @DisplayName("rejeita valores que quebrariam o SET search_path")
    @ValueSource(strings = {
            "public; DROP SCHEMA public CASCADE; --",
            "barueri; DELETE FROM user_info",
            "tenant with space",
            "tenant-com-hifen",
            "tenant\"aspas",
            "1tenant",
            "",
            "   ",
    })
    void rejectsUnsafeIdentifiers(String tenant) {
        assertThrows(TenantIdentifier.InvalidTenantException.class,
                () -> TenantIdentifier.requireValid(tenant));
    }

    @Test
    void rejectsNull() {
        assertThrows(TenantIdentifier.InvalidTenantException.class,
                () -> TenantIdentifier.requireValid(null));
    }

    @Test
    @DisplayName("rejeita nome maior que o limite de identificador do Postgres")
    void rejectsTooLongIdentifier() {
        assertThrows(TenantIdentifier.InvalidTenantException.class,
                () -> TenantIdentifier.requireValid("a".repeat(64)));
        assertEquals("a".repeat(63), TenantIdentifier.requireValid("a".repeat(63)));
    }
}
