package core.repository.dao.interfaces;

import core.repository.model.BaseEntity;
import core.service.model.Filter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

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

    /**
     * Igual a {@link #filteredFindPaged}, porém permite que o chamador acrescente
     * restrições próprias (ex.: regras de visibilidade por perfil) diretamente na query,
     * em vez de filtrar a lista em memória depois de paginar.
     */
    <T extends BaseEntity> List<T> filteredFindPaged(String tenant, Filter filter, int page, int size,
                                                     Class<T> entityClass, PredicateProvider<T> extraPredicates);

    /** Contrapartida de contagem de {@link #filteredFindPaged(String, Filter, int, int, Class, PredicateProvider)}. */
    <T extends BaseEntity> long countFiltered(String tenant, Filter filter, Class<T> clazz,
                                              PredicateProvider<T> extraPredicates);

    void defineSchema(String schema);

    /** Restrições adicionais aplicadas a uma query de listagem/contagem. */
    @FunctionalInterface
    interface PredicateProvider<T> {
        List<Predicate> apply(CriteriaBuilder cb, Root<T> root, CriteriaQuery<?> query);
    }
}
