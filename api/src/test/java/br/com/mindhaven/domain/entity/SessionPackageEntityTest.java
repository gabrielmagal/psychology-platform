package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SessionPackageEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        SessionPackageEntity sessionPackageEntity = entityGenerator.getSessionPackageEntity();

        Assertions.assertNotNull(sessionPackageEntity.getPackageTitle());
        Assertions.assertEquals("Pacote de sessões", sessionPackageEntity.getPackageTitle());
        Assertions.assertEquals(10, sessionPackageEntity.getTotalSessions());
    }
}