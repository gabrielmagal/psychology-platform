package core.service;

import br.com.psicologia.domain.entity.UserEntity;
import core.service.interfaces.ICrudService;
import core.service.interfaces.KeycloakService;
import core.service.model.Filter;
import core.repository.dao.interfaces.IGenericDao;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;

import javax.management.relation.InvalidRoleValueException;
import java.util.List;
import java.util.UUID;

@Transactional
public abstract class AbstractCrudService<T extends BaseEntity> extends AbstractEntityDescriptionService implements ICrudService<T> {
    @Inject
    protected IGenericDao dao;

    @Inject
    KeycloakService keycloakService;

    @Context
    public HttpHeaders headers;

    @Context
    public SecurityContext securityContext;

    protected final Class<T> entityClass;

    public AbstractCrudService(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T findById(UUID id) {
        return dao.findById(headers.getHeaderString("Tenant"), id, entityClass);
    }

    @Override
    public List<T> findAll() {
        return dao.listAll(headers.getHeaderString("Tenant"), entityClass);
    }

    @Override
    public List<T> findAllPaged(int page, int size) {
        return dao.findAllPaged(headers.getHeaderString("Tenant"), page, size, entityClass);
    }

    @Transactional
    @Override
    public T save(T entity) {
        String tenant = headers.getHeaderString("Tenant");
        T saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), entityClass);
    }

    @Transactional
    @Override
    public T update(T entity) {
        String tenant = headers.getHeaderString("Tenant");
        T saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), entityClass);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        dao.delete(headers.getHeaderString("Tenant"), id, entityClass);
    }

    @Override
    public List<T> filteredFindPaged(Filter filter, int page, int size) throws InvalidRoleValueException {
        return dao.filteredFindPaged(headers.getHeaderString("Tenant"), filter, page, size, entityClass);
    }

    @Override
    public long countFiltered(Filter filter) {
        return dao.countFiltered(headers.getHeaderString("Tenant"), filter, entityClass);
    }

    @Override
    public String getTenant() {
        return headers.getHeaderString("Tenant");
    }

    @Override
    public UserEntity getCurrentLoggedUser() {
        return keycloakService.findByKeycloakId(headers.getHeaderString("Tenant"), securityContext.getUserPrincipal().getName());
    }
}
