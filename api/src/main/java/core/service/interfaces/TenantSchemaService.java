package core.service.interfaces;

public interface TenantSchemaService {

    /**
     * Garante que o schema do tenant exista e esteja migrado.
     *
     * @param schemaName nome do tenant (será validado/normalizado)
     * @return o nome do schema normalizado, pronto para uso em SQL
     */
    String initSchema(String schemaName);
}
