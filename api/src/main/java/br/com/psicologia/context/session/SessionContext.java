package br.com.psicologia.context.session;

import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.model.SessionEntity;
import br.com.psicologia.model.UserEntity;
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
public class SessionContext implements IContext<SessionEntity> {
    @Inject
    UserContext userContext;

    @Inject
    Instance<SessionAdministradorContext> administradorContext;

    @Inject
    Instance<SessionPsicologoContext> psicologoContext;

    @Inject
    Instance<SessionPacienteContext> pacienteContext;

    private IContextUser<SessionEntity> iContextUser;

    public SessionEntity save(SecurityContext securityContext, String tenant, SessionEntity entity) {
        if (entity.getSessionPackage() == null) {
            throw new IllegalArgumentException("Sessão deve estar vinculada a um pacote.");
        }
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.save(securityContext, tenant, loggedUser, entity) ;
    }

    public SessionEntity update(SecurityContext securityContext, String tenant, SessionEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.update(securityContext, tenant, loggedUser, entity) ;
    }

    @Override
    public List<SessionEntity> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.filteredFindPaged(securityContext, tenant, loggedUser, filter, page, size);
    }

    @Override
    public SessionEntity findById(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.findById(securityContext, tenant, loggedUser, id);
    }

    public void delete(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        iContextUser.delete(securityContext, tenant, loggedUser, id);
    }

    @Override
    public long countFiltered(SecurityContext securityContext, String tenant, Filter filter) {
        return iContextUser.countFiltered(securityContext, tenant, createSessionContext(securityContext, tenant), filter);
    }

    public UserEntity createSessionContext(SecurityContext securityContext, String tenant) {
        UserEntity loggedUser = userContext.findByKeycloakId(securityContext, tenant);
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
}
