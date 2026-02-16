package br.com.psicologia.application.service;

import br.com.psicologia.application.usecase.interfaces.UserUseCase;
import br.com.psicologia.domain.entity.UserEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService extends AbstractCrudService<UserEntity> {
    @Inject
    UserUseCase userUseCase;

    public UserService() {
        super(UserEntity.class);
    }

    public UserEntity save(UserEntity entity) {
        return userUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public UserEntity update(UserEntity entity) {
        return userUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public UserEntity findById(UUID id) {
        return userUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        userUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<UserEntity> filteredFindPaged(Filter filter, int page, int size) {
        return userUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return userUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }
}
