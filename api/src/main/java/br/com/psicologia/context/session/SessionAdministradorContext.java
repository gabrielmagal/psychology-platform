package br.com.psicologia.context.session;

import br.com.psicologia.context.sessionpackage.SessionPackageContext;
import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
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
public class SessionAdministradorContext implements IContextUser<SessionEntity> {

    @Inject
    SessionPackageContext sessionPackageContext;

    @Inject
    GenericDao dao;

    @Transactional
    public SessionEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        SessionPackageEntity pacote = entity.getSessionPackage();
        if (pacote.getId() != null) {
            pacote = sessionPackageContext.findById(securityContext, tenant, pacote.getId());
            entity.setSessionPackage(pacote);
        }
        return dao.update(tenant, entity);
    }

    @Transactional
    public SessionEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        SessionEntity original = dao.findById(tenant, entity.getId(), SessionEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        return dao.update(tenant, entity);
    }

    @Transactional
    public List<SessionEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        return dao.filteredFindPaged(tenant, filter, page, size, SessionEntity.class);
    }

    @Transactional
    public SessionEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity original = dao.findById(tenant, id, SessionEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity session = dao.findById(tenant, id, SessionEntity.class);
        if (session == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        dao.delete(tenant, id, SessionEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, SessionEntity.class);
    }
}
