package br.com.mindhaven.domain.entity;

import br.com.mindhaven.EntityGenerator;
import br.com.mindhaven.domain.entity.enums.UserType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@QuarkusTest
public class UserEntityTest {
    EntityGenerator entityGenerator = new EntityGenerator();

    @Test
    public void createEntity() {
        UserEntity userEntity = entityGenerator.getUserEntity();

        Assertions.assertNotNull(userEntity.getId());
        Assertions.assertNotNull(userEntity.getRegisteredByKeycloakId());
        Assertions.assertNotNull(userEntity.getKeycloakId());
        Assertions.assertEquals("16941036761", userEntity.getCpf());
        Assertions.assertEquals("Gabriel", userEntity.getFirstName());
        Assertions.assertEquals("Almeida", userEntity.getLastName());
        Assertions.assertEquals("gabrielalmeida@gmail.com", userEntity.getEmail());
        Assertions.assertEquals("41987654321", userEntity.getPhoneNumber());
        Assertions.assertEquals(LocalDate.now(), userEntity.getBirthDate());
        Assertions.assertEquals("teste.png", userEntity.getProfileImage());
        Assertions.assertEquals(UserType.ADMINISTRADOR, userEntity.getUserType());
    }
}