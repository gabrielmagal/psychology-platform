package core.tenant;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Valida e normaliza o identificador de tenant recebido no header {@code Tenant}.
 *
 * <p>O nome do tenant é usado diretamente como nome de schema do Postgres em comandos
 * {@code CREATE SCHEMA} / {@code SET search_path}. Como não é possível parametrizar
 * identificadores em SQL, a única defesa contra injeção é validar o valor antes de
 * concatená-lo. Sem essa validação um header como
 * {@code Tenant: public; DROP SCHEMA public CASCADE; --} seria executado no banco.
 *
 * <p>O charset aceito ({@code [a-z_][a-z0-9_]*}) é exatamente o conjunto de identificadores
 * que o Postgres aceita sem aspas — que é como os schemas existentes já foram criados.
 * Identificadores não citados são convertidos para minúsculo pelo Postgres, então
 * normalizamos aqui para que {@code Barueri} e {@code barueri} resolvam para o mesmo schema.
 */
public final class TenantIdentifier {

    private static final Pattern VALID = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private TenantIdentifier() {
    }

    /**
     * @return o tenant normalizado (minúsculo, sem espaços nas pontas)
     * @throws InvalidTenantException se o valor for nulo, vazio ou não for um identificador seguro
     */
    public static String requireValid(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new InvalidTenantException("Header 'Tenant' é obrigatório.");
        }

        String normalized = tenant.trim().toLowerCase(Locale.ROOT);

        if (!VALID.matcher(normalized).matches()) {
            throw new InvalidTenantException(
                    "Tenant inválido. Use apenas letras minúsculas, números e '_' (até 63 caracteres).");
        }

        return normalized;
    }

    public static class InvalidTenantException extends IllegalArgumentException {
        public InvalidTenantException(String message) {
            super(message);
        }
    }
}
