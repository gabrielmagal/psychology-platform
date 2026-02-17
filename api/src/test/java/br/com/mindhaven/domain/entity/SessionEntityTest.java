package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SessionEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        SessionEntity sessionEntity = entityGenerator.getSessionEntity();

        Assertions.assertNotNull(sessionEntity.getId());
        Assertions.assertEquals("Título da sessão", sessionEntity.getTitle());
        Assertions.assertEquals("Resumo da sessão", sessionEntity.getSummary());
        Assertions.assertEquals("Notas privadas da sessão", sessionEntity.getPrivateNotes());
        Assertions.assertNotNull(sessionEntity.getAttendedAt());
    }
}