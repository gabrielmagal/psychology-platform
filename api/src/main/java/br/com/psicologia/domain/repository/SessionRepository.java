package br.com.psicologia.domain.repository;

import br.com.psicologia.domain.entity.SessionEntity;

import java.util.List;
import java.util.UUID;

public interface SessionRepository {
    SessionEntity save(String tenant, SessionEntity entity);
    SessionEntity update(String tenant, SessionEntity entity);
    SessionEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<SessionEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
}

