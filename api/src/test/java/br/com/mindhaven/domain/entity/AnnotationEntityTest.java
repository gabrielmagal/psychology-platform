package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AnnotationEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        AnnotationEntity annotationEntity = entityGenerator.getAnnotationEntity();

        Assertions.assertNotNull(annotationEntity.getId());
        Assertions.assertEquals("Sentimento principal", annotationEntity.getMainFeeling());
        Assertions.assertEquals("Eventos significativos", annotationEntity.getSignificantEvents());
        Assertions.assertEquals("Fase atual", annotationEntity.getCurrentPhase());
        Assertions.assertEquals("Pensamento dominante", annotationEntity.getDominantThought());
        Assertions.assertEquals("Intervenção", annotationEntity.getIntervention());
    }
}