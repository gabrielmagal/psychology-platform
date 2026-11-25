package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.SessionUseCase;
import br.com.mindhaven.domain.entity.SessionEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Os métodos daqui tinham assinatura própria ({@code save(tenant, loggedUser, entity)}),
 * diferente da que o {@code AbstractBaseController} invoca ({@code save(entity)}).
 * Resultado: o use case com toda a verificação de dono existia, mas <b>nunca era
 * chamado</b> — o controller caía nos métodos herdados de {@code AbstractCrudService}, que
 * vão direto ao DAO. Qualquer usuário autenticado do tenant podia ler, alterar e apagar
 * sessões (e as anotações clínicas dentro delas) de qualquer paciente via
 * {@code /api/session/{id}}.
 *
 * <p>Agora as sobrescritas têm a assinatura que o controller usa, como já acontecia em
 * {@code AnnotationService} e {@code PaymentService}.
 */
@ApplicationScoped
public class SessionService extends AppCrudService<SessionEntity> {
    @Inject
    SessionUseCase sessionUseCase;

    public SessionService() {
        super(SessionEntity.class);
    }

    @Override
    public SessionEntity save(SessionEntity entity) {
        return sessionUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    @Override
    public SessionEntity update(SessionEntity entity) {
        return sessionUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    @Override
    public SessionEntity findById(UUID id) {
        return sessionUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    @Override
    public void delete(UUID id) {
        sessionUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    @Override
    public List<SessionEntity> filteredFindPaged(Filter filter, int page, int size) {
        return sessionUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    @Override
    public long countFiltered(Filter filter) {
        return sessionUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    /**
     * Era um stub que devolvia {@code List.of()}. Delegar para a listagem filtrada com
     * filtro vazio mantém a regra de visibilidade num lugar só.
     */
    @Override
    public List<SessionEntity> findAllPaged(int page, int size) {
        return filteredFindPaged(emptyFilter(), page, size);
    }

    @Override
    public List<SessionEntity> findAll() {
        return filteredFindPaged(emptyFilter(), 0, Integer.MAX_VALUE);
    }

    private Filter emptyFilter() {
        Filter filter = new Filter();
        filter.setFilterParams(new ArrayList<>());
        return filter;
    }
}
