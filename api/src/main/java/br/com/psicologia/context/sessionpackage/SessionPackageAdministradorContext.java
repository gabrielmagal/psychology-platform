package br.com.psicologia.context.sessionpackage;

import br.com.psicologia.context.payment.PaymentContext;
import br.com.psicologia.context.sessionpackage.interfaces.ISessionPackageContextUser;
import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageAdministradorContext implements ISessionPackageContextUser {

    @Inject
    UserContext userContext;

    @Inject
    PaymentContext paymentContext;

    @Inject
    GenericDao dao;

    @Transactional
    public SessionPackageEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        entity.setPsychologistId(loggedUser.getId());
        SessionPackageEntity saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), SessionPackageEntity.class);
    }

    @Transactional
    public SessionPackageEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        SessionPackageEntity original = dao.findById(tenant, entity.getId(), SessionPackageEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Pacote de sessão não encontrado.");
        }
        return dao.update(tenant, entity);
    }

    @Transactional
    public List<SessionPackageEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        if (filter == null) filter = new Filter();
        if (filter.getFilterParams() == null) filter.setFilterParams(new ArrayList<>());

        List<SessionPackageEntity> paged = dao.filteredFindPaged(tenant, filter, page, size, SessionPackageEntity.class);

        paged.forEach(e -> {
            e.getSession().size();
            e.getPayment().size();
            e.getPatient().getId();
        });

        return paged;
    }

    @Transactional
    public SessionPackageEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity original = dao.findById(tenant, id, SessionPackageEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity session = dao.findById(tenant, id, SessionPackageEntity.class);
        if (session == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        dao.delete(tenant, id, SessionPackageEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, SessionPackageEntity.class);
    }

    @Override
    public List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        UserEntity loggedUser = userContext.findByKeycloakId(securityContext, tenant);
        if (loggedUser == null) {
            throw new IllegalStateException("Usuário autenticado não encontrado.");
        }

        return paymentContext.findPaidBySessionPackage(securityContext, tenant, sessionPackageId);
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        return paymentContext.sumPaidAmountBySessionPackage(securityContext, tenant, sessionPackageId);
    }
}
