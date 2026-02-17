package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageService extends AbstractCrudService<SessionPackageEntity> {
    @Inject
    SessionPackageUseCase sessionUseCase;

    public SessionPackageService() {
        super(SessionPackageEntity.class);
    }

    @Override
    public SessionPackageEntity save(SessionPackageEntity entity) {
        return sessionUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public SessionPackageEntity update(SessionPackageEntity entity) {
        return sessionUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public SessionPackageEntity findById(UUID id) {
        return sessionUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        sessionUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<SessionPackageEntity> filteredFindPaged(Filter filter, int page, int size) {
        return sessionUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return sessionUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    public List<SessionPackageEntity> findAllPaged(int page, int size) {
        return List.of();
    }
}
