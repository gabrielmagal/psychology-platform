package core.service.interfaces;

import br.com.psicologia.domain.entity.UserEntity;
import core.service.model.Filter;
import core.repository.model.BaseEntity;

import javax.management.relation.InvalidRoleValueException;
import java.util.List;
import java.util.UUID;

public interface ICrudService<T extends BaseEntity> {
    T findById(UUID id);
    List<T> findAll();
    T save(T entity);
    T update(T entity);
    void delete(UUID id);
    List<T> findAllPaged(int page, int size);
    List<T> filteredFindPaged(Filter filter, int page, int size) throws InvalidRoleValueException;
    long countFiltered(Filter filter);
    String getTenant();
    UserEntity getCurrentLoggedUser();
}
