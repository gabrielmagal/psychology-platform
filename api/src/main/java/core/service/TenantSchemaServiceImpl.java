package core.service;

import core.service.interfaces.TenantSchemaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class TenantSchemaServiceImpl implements TenantSchemaService {

    @Inject
    private DataSource dataSource;

    private final Set<String> initializedSchemas = new HashSet<>();

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public synchronized void initSchema(String schemaName) {
        if (initializedSchemas.contains(schemaName)) return;

        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar schema: " + schemaName, e);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/default")
                .load();

        flyway.migrate();
        initializedSchemas.add(schemaName);
    }
}