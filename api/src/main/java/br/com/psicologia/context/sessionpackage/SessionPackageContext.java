package br.com.psicologia.context.sessionpackage;

import br.com.psicologia.context.sessionpackage.interfaces.ISessionPackageContextUser;
import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.context.IContext;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageContext implements IContext<SessionPackageEntity> {

    @Inject
    UserContext userContext;

    @Inject
    Instance<SessionPackageAdministradorContext> administradorContext;

    @Inject
    Instance<SessionPackagePsicologoContext> psicologoContext;

    @Inject
    Instance<SessionPackagePacienteContext> pacienteContext;

    private ISessionPackageContextUser iContextUser;

    public SessionPackageEntity save(SecurityContext securityContext, String tenant, SessionPackageEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.save(securityContext, tenant, loggedUser, entity);
    }

    public SessionPackageEntity update(SecurityContext securityContext, String tenant, SessionPackageEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.update(securityContext, tenant, loggedUser, entity) ;
    }

    @Override
    public List<SessionPackageEntity> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.filteredFindPaged(securityContext, tenant, loggedUser, filter, page, size);
    }

    @Override
    public SessionPackageEntity findById(SecurityContext securityContext, String tenant, UUID id) {
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

    public List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        return iContextUser.findPaidBySessionPackage(securityContext, tenant, sessionPackageId);
    }

    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        return iContextUser.sumPaidAmountBySessionPackage(securityContext, tenant, sessionPackageId);
    }
}
