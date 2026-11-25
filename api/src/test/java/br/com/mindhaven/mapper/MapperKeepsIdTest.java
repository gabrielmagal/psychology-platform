package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.SessionDto;
import br.com.mindhaven.adapter.controller.dto.SessionPackageDto;
import br.com.mindhaven.adapter.controller.dto.UserDto;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * O id vem de BaseEntity e fica fora dos {@code @Builder} das entidades. Quando o MapStruct
 * usava os builders, todo {@code toEntity} perdia o id: editar virava inserir e todo vínculo
 * enviado como {@code {"id": ...}} chegava ao Hibernate como instância transiente.
 */
@QuarkusTest
class MapperKeepsIdTest {
    @Inject
    UserMapper userMapper;

    @Inject
    SessionPackageMapper packageMapper;

    @Inject
    SessionMapper sessionMapper;

    @Test
    @DisplayName("toEntity preserva o id da entidade e dos vínculos")
    void idsSurviveMapping() {
        UUID userId = UUID.randomUUID();
        UserDto user = new UserDto();
        user.setId(userId);
        assertEquals(userId, userMapper.toEntity(user).getId());

        UUID packageId = UUID.randomUUID();
        SessionPackageDto pkg = new SessionPackageDto();
        pkg.setId(packageId);
        pkg.setPatient(user);
        SessionPackageEntity pkgEntity = packageMapper.toEntity(pkg);
        assertEquals(packageId, pkgEntity.getId());
        assertEquals(userId, pkgEntity.getPatient().getId());
        assertNotNull(pkgEntity.getSession(), "coleções ignoradas mantêm o valor inicial");

        UUID sessionId = UUID.randomUUID();
        SessionDto session = new SessionDto();
        session.setId(sessionId);
        session.setSessionPackage(pkg);
        SessionEntity sessionEntity = sessionMapper.toEntity(session);
        assertEquals(sessionId, sessionEntity.getId());
        assertEquals(packageId, sessionEntity.getSessionPackage().getId());
    }
}
