package br.com.psicologia.application.usecase.interfaces;

import br.com.psicologia.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface UserUseCase {
    UserEntity save(String tenant, UserEntity loggedUser, UserEntity entity);
    UserEntity update(String tenant, UserEntity loggedUser, UserEntity entity);
    UserEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<UserEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
}
