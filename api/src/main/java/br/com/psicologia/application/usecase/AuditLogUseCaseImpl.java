package br.com.psicologia.application.usecase;

import br.com.psicologia.application.usecase.interfaces.AuditLogUseCase;
import br.com.psicologia.domain.entity.*;
import br.com.psicologia.domain.repository.AuditLogRepository;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogUseCaseImpl implements AuditLogUseCase {
    private final AuditLogRepository auditLogRepository;

    public AuditLogUseCaseImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public AuditLogEntity save(String tenant, UserEntity loggedUser, AuditLogEntity entity) {
        return auditLogRepository.save(tenant, entity);
    }

    @Override
    public AuditLogEntity update(String tenant, UserEntity loggedUser, AuditLogEntity entity) {
        return auditLogRepository.update(tenant, entity);
    }

    @Override
    public AuditLogEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        return auditLogRepository.findById(tenant, id);
    }

    @Override
    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        auditLogRepository.delete(tenant, id);
    }

    @Override
    public List<AuditLogEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        return auditLogRepository.filteredFindPaged(tenant, filter, page, size);
    }

    @Override
    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        return auditLogRepository.countFiltered(tenant, filter);
    }
}
