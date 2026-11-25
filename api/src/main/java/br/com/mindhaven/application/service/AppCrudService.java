package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.UserEntity;
import core.repository.model.BaseEntity;
import core.service.AbstractCrudService;
import core.user.CurrentUser;
import jakarta.inject.Inject;

/**
 * O CRUD genérico do núcleo mais o usuário logado, já no tipo de usuário desta aplicação.
 * O núcleo não conhece usuário nenhum; quem liga as duas coisas é esta classe.
 */
public abstract class AppCrudService<T extends BaseEntity> extends AbstractCrudService<T> {
    @Inject
    CurrentUser currentUser;

    protected AppCrudService(Class<T> entityClass) {
        super(entityClass);
    }

    public UserEntity getCurrentLoggedUser() {
        return currentUser.get(UserEntity.class);
    }
}
