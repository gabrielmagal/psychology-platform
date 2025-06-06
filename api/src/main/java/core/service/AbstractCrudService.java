package core.service;

import br.com.psicologia.service.CrudService;
import br.com.psicologia.service.model.Filter;
import core.repository.dao.interfaces.IGenericDao;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import core.repository.model.BaseEntity;

import java.util.List;
import java.util.UUID;

@Transactional
public abstract class AbstractCrudService<T extends BaseEntity> extends AbstractEntityDescriptionService implements CrudService<T> {
    @Inject
    protected IGenericDao dao;

    private final Class<T> entityClass;

    public AbstractCrudService(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T findById(String tenant, UUID id) {
        return dao.findById(tenant, id, entityClass);
    }

    @Override
    public List<T> findAll(String tenant) {
        return dao.listAll(tenant, entityClass);
    }

    @Override
    public List<T> findAllPaged(String tenant, int page, int size) {
        return dao.findAllPaged(tenant, page, size, entityClass);
    }

    @Transactional
    @Override
    public T save(String tenant, T entity) {
        T saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), entityClass);
    }

    @Transactional
    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, entityClass);
    }

    @Override
    public List<T> findAllPaged(int page, int size) {
        return dao.findAllPaged(page, size, entityClass);
    }

    @Override
    public List<T> filteredFindPaged(String tenant, Filter filter, int page, int size) {
        return dao.filteredFindPaged(tenant, filter, page, size, entityClass);
    }

    @Override
    public long countFiltered(String tenant, Filter filter) {
        return dao.countFiltered(tenant, filter, entityClass);
    }
}
