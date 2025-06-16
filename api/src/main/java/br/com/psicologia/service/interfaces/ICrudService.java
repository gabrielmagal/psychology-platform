package br.com.psicologia.service.interfaces;

import core.service.model.Filter;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.SecurityContext;

import javax.management.relation.InvalidRoleValueException;
import java.util.List;
import java.util.UUID;

public interface ICrudService<T extends BaseEntity> {
    T findById(SecurityContext securityContext, String tenant, UUID id);
    List<T> findAll(SecurityContext securityContext, String tenant);
    T save(SecurityContext securityContext, String tenant, T entity);
    T update(SecurityContext securityContext, String tenant, T entity);
    void delete(SecurityContext securityContext, String tenant, UUID id);
    List<T> findAllPaged(SecurityContext securityContext, int page, int size);
    List<T> findAllPaged(SecurityContext securityContext, String tenant, int page, int size);
    List<T> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) throws InvalidRoleValueException;
    long countFiltered(SecurityContext securityContext, String tenant, Filter filter);
}
