package core.service;

import core.service.interfaces.TenantSchemaService;
import core.tenant.TenantIdentifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TenantSchemaServiceImpl implements TenantSchemaService {

    @Inject
    private DataSource dataSource;

    /** Tenants já criados e migrados. Leitura lock-free — este é o caminho quente. */
    private final Set<String> initializedSchemas = ConcurrentHashMap.newKeySet();

    /** Um lock por tenant, para que a criação de um schema novo não bloqueie os demais. */
    private final Map<String, Object> initLocks = new ConcurrentHashMap<>();

    /**
     * Antes: o método inteiro era {@code synchronized}, então <b>toda</b> operação de banco
     * de <b>todos</b> os tenants passava por um único monitor — um gargalo global que
     * serializava a aplicação inteira mesmo quando o schema já existia.
     *
     * <p>Agora o caminho comum (schema já inicializado) é apenas um lookup em um set
     * concorrente, e o lock é tomado somente na primeira requisição de cada tenant.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public String initSchema(String schemaName) {
        String schema = TenantIdentifier.requireValid(schemaName);

        if (initializedSchemas.contains(schema)) return schema;

        synchronized (initLocks.computeIfAbsent(schema, k -> new Object())) {
            if (initializedSchemas.contains(schema)) return schema;

            try (var connection = dataSource.getConnection();
                 var stmt = connection.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao criar schema: " + schema, e);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .locations("classpath:db/migration/default")
                    // Bases migradas por uma versão anterior do projeto têm no histórico
                    // scripts que já não existem aqui (ex.: V3, V4). Sem isto o Flyway
                    // recusa a base inteira e o tenant não sobe; os scripts novos
                    // continuam sendo aplicados normalmente.
                    .ignoreMigrationPatterns("*:missing", "*:future")
                    .load();

            flyway.migrate();
            initializedSchemas.add(schema);
        }

        return schema;
    }
}
