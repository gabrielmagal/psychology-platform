package br.com.mindhaven.domain.repository;

import br.com.mindhaven.domain.entity.AuditLogEntity;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {
    AuditLogEntity save(String tenant, AuditLogEntity entity);
    AuditLogEntity update(String tenant, AuditLogEntity entity);
    AuditLogEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<AuditLogEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
}

