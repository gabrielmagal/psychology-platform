package br.com.mindhaven.application.usecase.interfaces;

import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface SessionPackageUseCase {
    SessionPackageEntity save(String tenant, UserEntity loggedUser, SessionPackageEntity entity);
    SessionPackageEntity update(String tenant, UserEntity loggedUser, SessionPackageEntity entity);
    SessionPackageEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<SessionPackageEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
}
