package br.com.psicologia.service;

import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import core.repository.dao.GenericDao;
import core.service.AbstractCrudService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageService extends AbstractCrudService<SessionPackageEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao genericDao;

    @Inject
    PaymentService paymentService;

    public SessionPackageService() {
        super(SessionPackageEntity.class);
    }

    @Override
    public List<SessionPackageEntity> findAllPaged(String tenant, int page, int size) {
        genericDao.defineSchema(tenant);

        List<SessionPackageEntity> entities = em.createQuery("""
        SELECT DISTINCT sp FROM SessionPackageEntity sp
        LEFT JOIN FETCH sp.session s
        LEFT JOIN FETCH s.patient
        LEFT JOIN FETCH s.psychologist
        LEFT JOIN FETCH sp.patient
        LEFT JOIN FETCH sp.psychologist
        """, SessionPackageEntity.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        entities.forEach(sp -> sp.getPayment().size());

        return entities;
    }

    public BigDecimal sumPaidAmountBySessionPackage(String tenant, UUID sessionPackageId) {
        return paymentService.sumPaidAmountBySessionPackage(tenant, sessionPackageId);
    }

    public List<PaymentEntity> findPaidBySessionPackage(String tenant, UUID sessionPackageId) {
        return paymentService.findPaidBySessionPackage(tenant, sessionPackageId);
    }
}
