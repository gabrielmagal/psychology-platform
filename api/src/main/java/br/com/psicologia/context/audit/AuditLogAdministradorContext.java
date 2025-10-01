package br.com.psicologia.context.audit;

import br.com.psicologia.model.AuditLogEntity;
import br.com.psicologia.model.UserEntity;
import core.context.IContextUser;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogAdministradorContext implements IContextUser<AuditLogEntity> {

    @Inject
    GenericDao dao;

    private final String MSG_UNAUTHORIZED = "Sem permissão para essa ação.";

    @Transactional
    public AuditLogEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, AuditLogEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public AuditLogEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, AuditLogEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public List<AuditLogEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        return dao.filteredFindPaged(tenant, filter, page, size, AuditLogEntity.class);
    }

    @Transactional
    public AuditLogEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        AuditLogEntity original = dao.findById(tenant, id, AuditLogEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Log de auditoria não encontrado.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, AuditLogEntity.class);
    }
}
