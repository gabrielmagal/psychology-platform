package core.context;

import br.com.psicologia.repository.model.UserEntity;
import core.service.model.Filter;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

public interface IContext <E> {
    E save(SecurityContext securityContext, String tenant, E entity);
    E update(SecurityContext securityContext, String tenant, E entity);
    List<E> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size);
    E findById(SecurityContext securityContext, String tenant, UUID id);
    void delete(SecurityContext securityContext, String tenant, UUID id);
    long countFiltered(SecurityContext securityContext, String tenant, Filter filter);
    UserEntity createSessionContext(SecurityContext securityContext, String tenant);
}
