package br.com.psicologia.context.mercadopagoinfo;

import br.com.psicologia.context.mercadopagoinfo.interfaces.IMercadoPagoInfoContextUser;
import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.model.MercadoPagoInfoEntity;
import br.com.psicologia.model.UserEntity;
import core.context.IContext;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MercadoPagoInfoContext implements IContext<MercadoPagoInfoEntity> {
    @Inject
    UserContext userContext;

    @Inject
    Instance<MercadoPagoInfoAdministradorContext> administradorContext;

    @Inject
    Instance<MercadoPagoInfoPsicologoContext> psicologoContext;

    @Inject
    Instance<MercadoPagoInfoPacienteContext> pacienteContext;

    private IMercadoPagoInfoContextUser iContextUser;

    public MercadoPagoInfoEntity save(SecurityContext securityContext, String tenant, MercadoPagoInfoEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.save(securityContext, tenant, loggedUser, entity) ;
    }

    public MercadoPagoInfoEntity update(SecurityContext securityContext, String tenant, MercadoPagoInfoEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.update(securityContext, tenant, loggedUser, entity) ;
    }

    @Override
    public List<MercadoPagoInfoEntity> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.filteredFindPaged(securityContext, tenant, loggedUser, filter, page, size);
    }

    @Override
    public MercadoPagoInfoEntity findById(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.findById(securityContext, tenant, loggedUser, id);
    }

    public void delete(SecurityContext securityContext, String tenant, UUID id) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        iContextUser.delete(securityContext, tenant, loggedUser, id);
    }

    @Override
    public long countFiltered(SecurityContext securityContext, String tenant, Filter filter) {
        createSessionContext(securityContext, tenant);
        return iContextUser.countFiltered(securityContext, tenant, createSessionContext(securityContext, tenant), filter);
    }

    public String createPaymentPreference(SecurityContext securityContext, String tenant, UUID packageId) {
        createSessionContext(securityContext, tenant);
        return iContextUser.createPaymentPreference(securityContext, tenant, packageId);
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
