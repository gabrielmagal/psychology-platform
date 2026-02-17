package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class MercadoPagoInfoEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        MercadoPagoInfoEntity mercadoPagoInfoEntity = entityGenerator.getMercadoPagoInfoEntity();

        Assertions.assertNotNull(mercadoPagoInfoEntity.getId());
        Assertions.assertEquals("token", mercadoPagoInfoEntity.getAccessToken());
        Assertions.assertEquals("refresh", mercadoPagoInfoEntity.getRefreshToken());
        Assertions.assertEquals("scope", mercadoPagoInfoEntity.getScope());
        Assertions.assertEquals(Integer.MAX_VALUE, mercadoPagoInfoEntity.getExpiresIn());
        Assertions.assertNotNull(mercadoPagoInfoEntity.getTokenCreatedAt());
    }
}