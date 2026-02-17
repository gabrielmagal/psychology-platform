package br.com.mindhaven.application.usecase.interfaces;

import br.com.mindhaven.domain.entity.AuditLogEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface AuditLogUseCase {
    AuditLogEntity save(String tenant, UserEntity loggedUser, AuditLogEntity entity);
    AuditLogEntity update(String tenant, UserEntity loggedUser, AuditLogEntity entity);
    AuditLogEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<AuditLogEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
}
