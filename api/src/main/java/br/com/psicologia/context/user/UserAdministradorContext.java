package br.com.psicologia.context.user;

import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.repository.model.UserEntity;
import br.com.psicologia.service.KeycloakService;
import core.context.IContextUser;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserAdministradorContext implements IContextUser<UserEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    @Inject
    KeycloakService keycloakService;

    @Transactional
    public UserEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        Set<String> roles = switch (entity.getUserType()) {
            case PSICOLOGO -> Set.of("PSICOLOGO_ROLE");
            case PACIENTE  -> Set.of("PACIENTE_ROLE");
            case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
        };

        String userId = keycloakService.createUser(tenant, entity, roles);
        entity.setKeycloakId(userId);
        entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());

        UserEntity saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), UserEntity.class);
    }

    @Transactional
    public UserEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        UserEntity original = dao.findById(tenant, entity.getId(), UserEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }

        Set<String> roles = switch (entity.getUserType()) {
            case PSICOLOGO -> Set.of("PSICOLOGO_ROLE");
            case PACIENTE  -> Set.of("PACIENTE_ROLE");
            case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
        };

        entity.setKeycloakId(original.getKeycloakId());
        entity.setRegisteredByKeycloakId(original.getRegisteredByKeycloakId());

        UserEntity saved = dao.update(tenant, entity);

        keycloakService.updateUser(tenant, original.getKeycloakId(), saved, roles);

        return dao.findById(tenant, saved.getId(), UserEntity.class);
    }

    @Override
    public List<UserEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        return dao.filteredFindPaged(tenant, filter, page, size, UserEntity.class);
    }

    @Transactional
    public UserEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        UserEntity original = dao.findById(tenant, id, UserEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        UserEntity user = dao.findById(tenant, id, UserEntity.class);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        dao.delete(tenant, id, SessionEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, SessionEntity.class);
    }
}
