package br.com.mindhaven.application.usecase.interfaces;

import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface SessionUseCase {
    SessionEntity save(String tenant, UserEntity loggedUser, SessionEntity entity);
    SessionEntity update(String tenant, UserEntity loggedUser, SessionEntity entity);
    SessionEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<SessionEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
}
