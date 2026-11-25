package br.com.mindhaven.application;

import br.com.mindhaven.application.service.SessionService;
import br.com.mindhaven.application.usecase.interfaces.SessionUseCase;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.domain.entity.AnnotationEntity;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o elo que estava solto: o use case de sessões tinha verificação de dono, mas o
 * {@code SessionService} não a expunha na assinatura que o controller chama.
 */
@QuarkusTest
class SessionOwnershipTest {

    private static final String TENANT = "clinica_sessao";

    @Inject
    GenericDao dao;

    @Inject
    SessionUseCase sessionUseCase;

    private static UserEntity psicologoDono;
    private static UserEntity psicologoIntruso;
    private static UserEntity paciente;
    private static SessionEntity sessao;

    private UserEntity newUser(String cpf, String nome, UserType tipo) {
        return UserEntity.builder()
                .cpf(cpf)
                .firstName(nome)
                .lastName("Teste")
                .email(cpf + "@exemplo.com")
                .phoneNumber("11" + cpf)
                .birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString())
                .userType(tipo)
                .build();
    }

    @BeforeEach
    void seed() {
        if (sessao != null) return;

        psicologoDono = dao.update(TENANT, newUser("80000000001", "Dono", UserType.PROFISSIONAL));
        psicologoIntruso = dao.update(TENANT, newUser("80000000002", "Intruso", UserType.PROFISSIONAL));
        paciente = dao.update(TENANT, newUser("80000000003", "Paciente", UserType.PACIENTE));

        SessionPackageEntity pacote = new SessionPackageEntity();
        pacote.setProfessionalId(psicologoDono.getId());
        pacote.setPatient(paciente);
        pacote.setPackageTitle("Pacote mensal");
        pacote.setTotalSessions(4);
        pacote = dao.update(TENANT, pacote);

        SessionEntity nova = SessionEntity.builder()
                .title("Sessão inicial")
                .dateSession(LocalDate.of(2026, 4, 1))
                .privateNotes("nota clínica reservada")
                .sessionPackage(pacote)
                .build();
        sessao = sessionUseCase.save(TENANT, psicologoDono, nova);
        assertNotNull(sessao.getId());
    }

    @Test
    @DisplayName("outro profissional não lê, não altera e não apaga a sessão")
    void otherProfessionalIsBlocked() {
        UUID id = sessao.getId();

        assertThrows(SecurityException.class,
                () -> sessionUseCase.findById(TENANT, psicologoIntruso, id));
        assertThrows(SecurityException.class,
                () -> sessionUseCase.delete(TENANT, psicologoIntruso, id));

        SessionEntity alterada = SessionEntity.builder()
                .title("hackeada")
                .sessionPackage(sessao.getSessionPackage())
                .build();
        alterada.setId(id);
        assertThrows(SecurityException.class,
                () -> sessionUseCase.update(TENANT, psicologoIntruso, alterada));
    }

    @Test
    @DisplayName("a listagem do intruso vem vazia; a do dono, não")
    void listingIsScopedToOwner() {
        Filter filter = new Filter();
        filter.setFilterParams(new ArrayList<>());
        assertTrue(sessionUseCase.filteredFindPaged(TENANT, psicologoIntruso, filter, 0, 10).isEmpty());

        Filter filterDono = new Filter();
        filterDono.setFilterParams(new ArrayList<>());
        assertEquals(1, sessionUseCase.filteredFindPaged(TENANT, psicologoDono, filterDono, 0, 10).size());
    }

    @Test
    @DisplayName("o controller chama versões com verificação — as sobrescritas existem")
    void serviceOverridesTheMethodsTheControllerCalls() throws NoSuchMethodException {
        // O bug era exatamente este: os métodos com verificação existiam com OUTRA
        // assinatura, e o controller caía nos herdados (sem verificação). Fixamos que as
        // assinaturas chamadas pelo AbstractBaseController estão declaradas na própria
        // SessionService.
        assertEquals(SessionService.class,
                SessionService.class.getMethod("findById", UUID.class).getDeclaringClass());
        assertEquals(SessionService.class,
                SessionService.class.getMethod("delete", UUID.class).getDeclaringClass());
        assertEquals(SessionService.class,
                SessionService.class.getMethod("save", SessionEntity.class).getDeclaringClass());
        assertEquals(SessionService.class,
                SessionService.class.getMethod("update", SessionEntity.class).getDeclaringClass());
        assertEquals(SessionService.class,
                SessionService.class.getMethod("filteredFindPaged", Filter.class, int.class, int.class)
                        .getDeclaringClass());
    }
}
