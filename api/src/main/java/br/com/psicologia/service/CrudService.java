package br.com.psicologia.service;

import br.com.psicologia.service.model.Filter;
import core.repository.model.BaseEntity;

import java.util.List;
import java.util.UUID;

public interface CrudService<T extends BaseEntity> {
    T findById(String tenant, UUID id);
    List<T> findAll(String tenant);
    T save(String tenant, T entity);
    void delete(String tenant, UUID id);
    List<T> findAllPaged(int page, int size);
    List<T> findAllPaged(String tenant, int page, int size);
    List<T> filteredFindPaged(String tenant, Filter filter, int page, int size);
    long countFiltered(String tenant, Filter filter);
}
