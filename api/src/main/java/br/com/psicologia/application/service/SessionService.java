package br.com.psicologia.application.service;

import br.com.psicologia.application.usecase.interfaces.SessionUseCase;
import br.com.psicologia.domain.entity.SessionEntity;
import br.com.psicologia.domain.entity.UserEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionService extends AbstractCrudService<SessionEntity> {
    @Inject
    SessionUseCase sessionUseCase;

    public SessionService() {
        super(SessionEntity.class);
    }

    public SessionEntity save(String tenant, UserEntity loggedUser, SessionEntity entity) {
        return sessionUseCase.save(tenant, loggedUser, entity);
    }

    public SessionEntity update(String tenant, UserEntity loggedUser, SessionEntity entity) {
        return sessionUseCase.update(tenant, loggedUser, entity);
    }

    public SessionEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        return sessionUseCase.findById(tenant, loggedUser, id);
    }

    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        sessionUseCase.delete(tenant, loggedUser, id);
    }

    public List<SessionEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        return sessionUseCase.filteredFindPaged(tenant, loggedUser, filter, page, size);
    }

    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        return sessionUseCase.countFiltered(tenant, loggedUser, filter);
    }

    public List<SessionEntity> findAllPaged(SecurityContext securityContext, int page, int size) {
        return List.of();
    }
}
