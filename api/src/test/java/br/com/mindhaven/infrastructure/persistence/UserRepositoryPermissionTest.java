package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.service.model.Filter;
import core.service.model.FilterParam;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre a regra de visibilidade por perfil agora que ela é aplicada no banco.
 */
@QuarkusTest
class UserRepositoryPermissionTest {

    private static final String TENANT = "clinica_permissao";

    private static final String PROFISSIONAL_KC = "kc-psicologo";
    private static final String OUTRO_PROFISSIONAL_KC = "kc-outro-psicologo";

    @Inject
    UserRepositoryImpl repository;

    // O JUnit cria uma instância nova de teste por método; a massa é semeada uma vez só.
    private static UserEntity psicologo;

    private UserEntity build(String cpf, String nome, UserType tipo, String keycloakId, String registeredBy) {
        return UserEntity.builder()
                .cpf(cpf)
                .firstName(nome)
                .lastName("Teste")
                .email(cpf + "@exemplo.com")
                .phoneNumber("11" + cpf)
                .birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(keycloakId)
                .registeredByKeycloakId(registeredBy)
                .userType(tipo)
                .build();
    }

    @BeforeEach
    void seed() {
        if (psicologo != null) return;

        psicologo = repository.save(TENANT,
                build("90000000001", "Psicologo", UserType.PROFISSIONAL, PROFISSIONAL_KC, null));

        repository.save(TENANT,
                build("90000000002", "OutroPsicologo", UserType.PROFISSIONAL, OUTRO_PROFISSIONAL_KC, null));

        // 12 pacientes deste profissional...
        for (int i = 0; i < 12; i++) {
            repository.save(TENANT, build(String.format("91%09d", i), "Paciente" + i,
                    UserType.PACIENTE, "kc-paciente-" + i, PROFISSIONAL_KC));
        }

        // ...e 6 de outro, que ele não pode enxergar.
        for (int i = 0; i < 6; i++) {
            repository.save(TENANT, build(String.format("92%09d", i), "Alheio" + i,
                    UserType.PACIENTE, "kc-alheio-" + i, OUTRO_PROFISSIONAL_KC));
        }
    }

    private Filter emptyFilter() {
        Filter filter = new Filter();
        filter.setFilterParams(List.of());
        return filter;
    }

    @Test
    @DisplayName("profissional recebe páginas cheias, só com usuários visíveis para ele")
    void professionalGetsFullPages() {
        // Antes: a página vinha do banco sem restrição e era filtrada em memória depois,
        // então uma página de 5 podia chegar com 1 ou 2 registros — e os descartados não
        // apareciam em página nenhuma.
        List<UserEntity> page0 = repository.filteredFindPagedComPermissao(TENANT, psicologo, emptyFilter(), 0, 5);
        List<UserEntity> page1 = repository.filteredFindPagedComPermissao(TENANT, psicologo, emptyFilter(), 1, 5);

        assertEquals(5, page0.size());
        assertEquals(5, page1.size());

        assertTrue(page0.stream().allMatch(this::visivelParaPsicologo));
        assertTrue(page1.stream().allMatch(this::visivelParaPsicologo));
    }

    @Test
    @DisplayName("contagem reflete só os usuários visíveis")
    void countIsScopedToPermission() {
        // 12 pacientes + ele mesmo.
        assertEquals(13, repository.countFilteredComPermissao(TENANT, psicologo, emptyFilter()));
    }

    @Test
    @DisplayName("paginação percorre todos os visíveis sem repetir nem pular")
    void pagesCoverEveryVisibleUser() {
        long total = repository.countFilteredComPermissao(TENANT, psicologo, emptyFilter());

        var vistos = new java.util.HashSet<UUID>();
        for (int page = 0; page * 5 < total; page++) {
            repository.filteredFindPagedComPermissao(TENANT, psicologo, emptyFilter(), page, 5)
                    .forEach(u -> vistos.add(u.getId()));
        }

        assertEquals(total, vistos.size());
    }

    @Test
    @DisplayName("paciente enxerga apenas a si mesmo")
    void patientSeesOnlyItself() {
        UserEntity paciente = repository.filteredFindPagedComPermissao(TENANT, psicologo, emptyFilter(), 0, 20)
                .stream()
                .filter(u -> u.getUserType() == UserType.PACIENTE)
                .findFirst()
                .orElseThrow();

        assertEquals(1, repository.countFilteredComPermissao(TENANT, paciente, emptyFilter()));

        List<UserEntity> visiveis = repository.filteredFindPagedComPermissao(TENANT, paciente, emptyFilter(), 0, 20);
        assertEquals(1, visiveis.size());
        assertEquals(paciente.getId(), visiveis.getFirst().getId());
    }

    @Test
    @DisplayName("a restrição de perfil se combina com os filtros da tela")
    void permissionCombinesWithScreenFilters() {
        Filter filter = new Filter();
        filter.setFilterParams(List.of(new FilterParam("userType", "PACIENTE")));

        assertEquals(12, repository.countFilteredComPermissao(TENANT, psicologo, filter));

        List<UserEntity> encontrados = repository.filteredFindPagedComPermissao(TENANT, psicologo, filter, 0, 20);
        assertEquals(12, encontrados.size());
        assertTrue(encontrados.stream().allMatch(u -> u.getUserType() == UserType.PACIENTE));
    }

    private boolean visivelParaPsicologo(UserEntity user) {
        return PROFISSIONAL_KC.equals(user.getKeycloakId())
                || PROFISSIONAL_KC.equals(user.getRegisteredByKeycloakId());
    }
}
