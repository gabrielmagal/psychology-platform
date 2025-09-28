package core.repository.dao.interfaces;

import core.repository.model.BaseEntity;
import core.service.model.Filter;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.util.List;
import java.util.UUID;

public interface IGenericDao {
    <T extends BaseEntity> T findById(String tenant, UUID id, Class<T> clazz);
    <T extends BaseEntity> void delete(String tenant, UUID id, Class<T> clazz);
    <T extends BaseEntity> T update(String tenant, T entity);
    <T extends BaseEntity> long count(String tenant, Class<T> clazz);
    <T extends BaseEntity> List<T> listAll(String tenant, Class<T> clazz);
    <T extends BaseEntity> List<T> findAllPaged(String tenant, int page, int size, Class<T> clazz);
    CriteriaBuilder getCriteriaBuilder(String tenant);
    <T extends BaseEntity> List<T> filteredFindPaged(String tenant, Filter filter, int page, int size, Class<T> entityClass);
    <T extends BaseEntity> long countFiltered(String tenant, Filter filter, Class<T> clazz);
    void defineSchema(String schema);
}
