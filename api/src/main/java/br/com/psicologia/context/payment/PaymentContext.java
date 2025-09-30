package br.com.psicologia.context.payment;

import br.com.psicologia.context.payment.interfaces.IPaymentContextUser;
import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.controller.dto.MakePaymentDto;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.context.IContext;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentContext implements IContext<PaymentEntity> {
    @Inject
    UserContext userContext;

    @Inject
    GenericDao dao;

    @Inject
    Instance<PaymentAdministradorContext> administradorContext;

    @Inject
    Instance<PaymentPsicologoContext> psicologoContext;

    @Inject
    Instance<PaymentPacienteContext> pacienteContext;

    private IPaymentContextUser iContextUser;

    public PaymentEntity save(SecurityContext securityContext, String tenant, PaymentEntity entity) {
        if (entity.getSessionPackage() == null || entity.getSessionPackage().getId() == null) {
            throw new IllegalArgumentException("Pagamento deve estar vinculado a um pacote.");
        }

        SessionPackageEntity pacote = dao.findById(tenant, entity.getSessionPackage().getId(), SessionPackageEntity.class);
        if (pacote == null) {
            throw new IllegalArgumentException("Pacote de sessão não encontrado.");
        }

        entity.setSessionPackage(pacote);

        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.save(securityContext, tenant, loggedUser, entity) ;
    }

    public PaymentEntity update(SecurityContext securityContext, String tenant, PaymentEntity entity) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.update(securityContext, tenant, loggedUser, entity) ;
    }

    @Override
    public List<PaymentEntity> filteredFindPaged(SecurityContext securityContext, String tenant, Filter filter, int page, int size) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.filteredFindPaged(securityContext, tenant, loggedUser, filter, page, size);
    }

    @Override
    public PaymentEntity findById(SecurityContext securityContext, String tenant, UUID id) {
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

    public void makePayment(SecurityContext securityContext, String tenant, MakePaymentDto makePaymentDto) {
        iContextUser.makePayment(securityContext, tenant, createSessionContext(securityContext, tenant), makePaymentDto);
    }

    public List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.findPaidBySessionPackage(securityContext, tenant, loggedUser, sessionPackageId);
    }

    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        UserEntity loggedUser = createSessionContext(securityContext, tenant);
        return iContextUser.sumPaidAmountBySessionPackage(securityContext, tenant, loggedUser, sessionPackageId);
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
