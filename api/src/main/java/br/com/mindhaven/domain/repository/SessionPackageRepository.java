package br.com.mindhaven.domain.repository;

import br.com.mindhaven.domain.entity.SessionPackageEntity;

import java.util.List;
import java.util.UUID;

public interface SessionPackageRepository {
    SessionPackageEntity save(String tenant, SessionPackageEntity entity);
    SessionPackageEntity update(String tenant, SessionPackageEntity entity);
    SessionPackageEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<SessionPackageEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
}

