package core.service;

import br.com.psicologia.service.interfaces.ICrudService;
import core.service.model.Filter;
import core.repository.dao.interfaces.IGenericDao;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.SecurityContext;

import javax.management.relation.InvalidRoleValueException;
import java.util.List;
import java.util.UUID;

@Transactional
public abstract class AbstractCrudService<T extends BaseEntity> extends AbstractEntityDescriptionService implements ICrudService<T> {
    @Inject
    protected IGenericDao dao;

    private final Class<T> entityClass;

    public AbstractCrudService(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T findById(SecurityContext securityContext, String tenant, UUID id) {
        return dao.findById(tenant, id, entityClass);
    }

    @Override
    public List<T> findAll(SecurityContext securityContext, String tenant) {
        return dao.listAll(tenant, entityClass);
    }

    @Override
    public List<T> findAllPaged(SecurityContext securityContext, String tenant, int page, int size) {
        return dao.findAllPaged(tenant, page, size, entityClass);
    }

    @Transactional
    @Override
    public T save(SecurityContext securityContext, String tenant, T entity) {
        T saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), entityClass);
    }

    @Transactional
    @Override
    public T update(SecurityContext securityContext, String tenant, T entity) {
        T saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), entityClass);
    }

    @Transactional
    @Override
    public void delete(SecurityContext securityContext, String tenant, UUID id) {
        dao.delete(tenant, id, entityClass);
    }

    @Override
    public List<T> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) throws InvalidRoleValueException {
        return dao.filteredFindPaged(tenant, filter, page, size, entityClass);
    }

    @Override
    public long countFiltered(SecurityContext securityContext, String tenant, Filter filter) {
        return dao.countFiltered(tenant, filter, entityClass);
    }
}
