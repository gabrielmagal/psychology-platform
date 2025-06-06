package br.com.psicologia.service;

import br.com.psicologia.repository.model.PaymentEntity;
import core.repository.dao.GenericDao;
import core.service.AbstractCrudService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentService extends AbstractCrudService<PaymentEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao genericDao;

    public PaymentService() {
        super(PaymentEntity.class);
    }

    @Override
    public List<PaymentEntity> findAllPaged(String tenant, int page, int size) {
        genericDao.defineSchema(tenant);

        return em.createQuery("""
            SELECT DISTINCT p FROM PaymentEntity p
            LEFT JOIN FETCH p.sessionPackage sp
            LEFT JOIN FETCH sp.patient
            LEFT JOIN FETCH sp.psychologist
        """, PaymentEntity.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public List<PaymentEntity> findPaidBySessionPackage(String tenant, UUID sessionPackageId) {
        genericDao.defineSchema(tenant);

        String jpql = """
        SELECT p FROM PaymentEntity p
        WHERE p.sessionPackage.id = :sessionPackageId
          AND p.paid = true
        ORDER BY p.paymentDate ASC
    """;

        return em.createQuery(jpql, PaymentEntity.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .getResultList();
    }

    public BigDecimal sumPaidAmountBySessionPackage(String tenant, UUID sessionPackageId) {
        genericDao.defineSchema(tenant);

        String jpql = """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PaymentEntity p
            WHERE p.sessionPackage.id = :sessionPackageId
              AND p.paid = true
        """;

        return em.createQuery(jpql, BigDecimal.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .getSingleResult();
    }
}
