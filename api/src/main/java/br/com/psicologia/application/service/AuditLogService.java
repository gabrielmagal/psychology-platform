package br.com.psicologia.application.service;

import br.com.psicologia.application.usecase.interfaces.AuditLogUseCase;
import br.com.psicologia.domain.entity.AuditLogEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogService extends AbstractCrudService<AuditLogEntity> {
    @Inject
    AuditLogUseCase auditLogUseCase;

    public AuditLogService() {
        super(AuditLogEntity.class);
    }

    public AuditLogEntity save(AuditLogEntity entity) {
        return auditLogUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public AuditLogEntity update(AuditLogEntity entity) {
        return auditLogUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public AuditLogEntity findById(UUID id) {
        return auditLogUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        auditLogUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<AuditLogEntity> filteredFindPaged(Filter filter, int page, int size) {
        return auditLogUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return auditLogUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }
}
