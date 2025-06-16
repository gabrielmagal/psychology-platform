package br.com.psicologia.context.user;

import br.com.psicologia.repository.model.UserEntity;
import br.com.psicologia.service.KeycloakService;
import core.context.IContext;
import core.context.IContextUser;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserContext implements IContext<UserEntity> {

    @Inject
    KeycloakService keycloakService;

    @Inject
    Instance<UserAdministradorContext> administradorContext;

    @Inject
    Instance<UserPsicologoContext> psicologoContext;

    @Inject
    Instance<UserPacienteContext> pacienteContext;

    private IContextUser<UserEntity> iContextUser;

    @Override
    public UserEntity save(SecurityContext securityContext, String tenant, UserEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.save(securityContext, tenant, loggedUser, entity);
    }

    @Override
    public UserEntity update(SecurityContext securityContext, String tenant, UserEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.update(securityContext, tenant, loggedUser, entity);
    }

    @Override
    public List<UserEntity> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.filteredFindPaged(securityContext, tenant, loggedUser, filter, page, size);
    }

    @Override
    public UserEntity findById(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.findById(securityContext, tenant, loggedUser, id);
    }

    @Override
    public void delete(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity userOld = findById(securityContext, tenant, id);
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        iContextUser.delete(securityContext, tenant, loggedUser, id);
        keycloakService.deleteUser(tenant, UUID.fromString(userOld.getKeycloakId()));
    }

    @Override
    public long countFiltered(SecurityContext securityContext, String tenant, Filter filter) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.countFiltered(securityContext, tenant, loggedUser, filter);
    }

    public UserEntity createSessionContext(SecurityContext securityContext, String tenant) {
        UserEntity loggedUser = findByKeycloakId(securityContext, tenant);
        if (loggedUser == null) {
            throw new IllegalStateException("Usuário autenticado não encontrado.");
        }
        iContextUser = switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> administradorContext.get();
            case PSICOLOGO -> psicologoContext.get();
            case PACIENTE -> pacienteContext.get();
        };
        return loggedUser;
    }

    public UserEntity findByKeycloakId(SecurityContext securityContext, String tenant) {
        return keycloakService.findByKeycloakId(tenant, securityContext.getUserPrincipal().getName());
    }
}
