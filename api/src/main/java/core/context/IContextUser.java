package core.context;

import br.com.psicologia.repository.model.UserEntity;
import core.service.model.Filter;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

public interface IContextUser <E> {
    E save(SecurityContext securityContext, String tenant, UserEntity userEntity, E entity);
    E update(SecurityContext securityContext, String tenant, UserEntity userEntity, E entity);
    List<E> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size);
    E findById(SecurityContext securityContext, String tenant, UserEntity userEntity, UUID id);
    void delete(SecurityContext securityContext, String tenant, UserEntity userEntity, UUID id);
    long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter);
}
