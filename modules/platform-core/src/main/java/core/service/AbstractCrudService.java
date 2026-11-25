package core.service;

import core.service.interfaces.ICrudService;
import core.service.model.Filter;
import core.repository.dao.interfaces.IGenericDao;
import core.tenant.TenantIdentifier;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import javax.management.relation.InvalidRoleValueException;
import java.util.List;
import java.util.UUID;

@Transactional
public abstract class AbstractCrudService<T extends BaseEntity> extends AbstractEntityDescriptionService implements ICrudService<T> {
    @Inject
    protected IGenericDao dao;

    @Context
    public HttpHeaders headers;

    protected final Class<T> entityClass;

    public AbstractCrudService(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T findById(UUID id) {
        return dao.findById(getTenant(), id, entityClass);
    }

    @Override
    public List<T> findAll() {
        return dao.listAll(getTenant(), entityClass);
    }

    @Override
    public List<T> findAllPaged(int page, int size) {
        return dao.findAllPaged(getTenant(), page, size, entityClass);
    }

    @Transactional
    @Override
    public T save(T entity) {
        return dao.update(getTenant(), entity);
    }

    @Transactional
    @Override
    public T update(T entity) {
        return dao.update(getTenant(), entity);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        dao.delete(getTenant(), id, entityClass);
    }

    @Override
    public List<T> filteredFindPaged(Filter filter, int page, int size) throws InvalidRoleValueException {
        return dao.filteredFindPaged(getTenant(), filter, page, size, entityClass);
    }

    @Override
    public long countFiltered(Filter filter) {
        return dao.countFiltered(getTenant(), filter, entityClass);
    }

    @Override
    public String getTenant() {
        return TenantIdentifier.requireValid(headers.getHeaderString("Tenant"));
    }

}
