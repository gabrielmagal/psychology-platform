package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.AuditLogEntity;
import br.com.mindhaven.domain.repository.AuditLogRepository;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogRepositoryImpl implements AuditLogRepository {

    @Inject
    GenericDao dao;

    @Override
    public AuditLogEntity save(String tenant, AuditLogEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AuditLogEntity update(String tenant, AuditLogEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AuditLogEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, AuditLogEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, AuditLogEntity.class);
    }

    @Override
    public List<AuditLogEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, AuditLogEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, AuditLogEntity.class);
    }
}
