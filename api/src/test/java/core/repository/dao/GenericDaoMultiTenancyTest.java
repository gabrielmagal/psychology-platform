package core.repository.dao;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.service.model.Filter;
import core.service.model.FilterParam;
import core.tenant.TenantIdentifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercita o isolamento por schema e a tradução de filtros contra um banco real (H2).
 */
@QuarkusTest
class GenericDaoMultiTenancyTest {

    @Inject
    GenericDao dao;

    private UserEntity newUser(String cpf, String firstName, UserType type) {
        return UserEntity.builder()
                .cpf(cpf)
                .firstName(firstName)
                .lastName("Sobrenome")
                .email(firstName.toLowerCase() + "@exemplo.com")
                .phoneNumber("11" + cpf)
                .birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .userType(type)
                .build();
    }

    @Test
    @DisplayName("cada tenant enxerga apenas o próprio schema")
    void isolatesDataBetweenTenants() {
        String tenantA = "clinica_a";
        String tenantB = "clinica_b";

        UserEntity savedA = dao.update(tenantA, newUser("11111111111", "Ana", UserType.PSICOLOGO));
        UserEntity savedB = dao.update(tenantB, newUser("22222222222", "Bruno", UserType.PACIENTE));

        assertNotNull(savedA.getId(), "merge deve devolver a entidade com o id gerado");
        assertNotNull(savedB.getId());

        assertEquals(1, dao.count(tenantA, UserEntity.class));
        assertEquals(1, dao.count(tenantB, UserEntity.class));

        // O registro de um tenant não pode ser alcançável a partir do outro.
        assertNotNull(dao.findById(tenantA, savedA.getId(), UserEntity.class));
        assertNull(dao.findById(tenantB, savedA.getId(), UserEntity.class));
        assertNull(dao.findById(tenantA, savedB.getId(), UserEntity.class));
    }

    @Test
    @DisplayName("um header Tenant malicioso não chega ao banco")
    void rejectsSqlInjectionInTenantHeader() {
        assertThrows(TenantIdentifier.InvalidTenantException.class,
                () -> dao.count("public; DROP TABLE user_info; --", UserEntity.class));
    }

    @Test
    @DisplayName("listagem e contagem concordam ao filtrar por campo simples")
    void listAndCountAgreeOnSimpleFieldFilter() {
        String tenant = "clinica_filtro";

        dao.update(tenant, newUser("33333333331", "Carla", UserType.PSICOLOGO));
        dao.update(tenant, newUser("33333333332", "Diego", UserType.PACIENTE));
        dao.update(tenant, newUser("33333333333", "Elisa", UserType.PACIENTE));

        Filter filter = new Filter();
        filter.setFilterParams(List.of(new FilterParam("userType", "PACIENTE")));

        List<UserEntity> found = dao.filteredFindPaged(tenant, filter, 0, 10, UserEntity.class);
        long total = dao.countFiltered(tenant, filter, UserEntity.class);

        // Antes: a listagem ignorava o filtro (devolvia os 3) e a contagem estourava
        // IllegalArgumentException por não saber converter um enum.
        assertEquals(2, found.size());
        assertEquals(2, total);
        assertTrue(found.stream().allMatch(u -> u.getUserType() == UserType.PACIENTE));
    }

    @Test
    @DisplayName("busca textual e paginação")
    void searchAndPaging() {
        String tenant = "clinica_busca";

        dao.update(tenant, newUser("44444444441", "Fabiana", UserType.PACIENTE));
        dao.update(tenant, newUser("44444444442", "Fabricio", UserType.PACIENTE));
        dao.update(tenant, newUser("44444444443", "Gustavo", UserType.PACIENTE));

        Filter filter = new Filter();
        filter.setFilterParams(List.of(new FilterParam("search", "fab")));

        assertEquals(2, dao.countFiltered(tenant, filter, UserEntity.class));

        List<UserEntity> firstPage = dao.filteredFindPaged(tenant, filter, 0, 1, UserEntity.class);
        List<UserEntity> secondPage = dao.filteredFindPaged(tenant, filter, 1, 1, UserEntity.class);

        assertEquals(1, firstPage.size());
        assertEquals(1, secondPage.size());
        assertNotEquals(firstPage.getFirst().getId(), secondPage.getFirst().getId());
    }

    @Test
    @DisplayName("filtro vazio ou desconhecido não quebra a consulta")
    void ignoresBlankAndUnknownFilterParams() {
        String tenant = "clinica_vazio";
        dao.update(tenant, newUser("55555555551", "Helena", UserType.PACIENTE));

        Filter filter = new Filter();
        filter.setFilterParams(List.of(
                new FilterParam("search", ""),
                new FilterParam("campoQueNaoExiste", "x")));

        assertEquals(1, dao.countFiltered(tenant, filter, UserEntity.class));
        assertFalse(dao.filteredFindPaged(tenant, filter, 0, 10, UserEntity.class).isEmpty());
    }

    private static void assertNotEquals(Object unexpected, Object actual) {
        org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual);
    }
}
