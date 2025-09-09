package br.com.psicologia.context.payment;

import br.com.psicologia.context.payment.interfaces.IPaymentContextUser;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentAdministradorContext implements IPaymentContextUser {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Transactional
    public PaymentEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        return dao.update(tenant, entity);
    }

    @Transactional
    public PaymentEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        PaymentEntity original = dao.findById(tenant, entity.getId(), PaymentEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Pagamento não encontrado.");
        }
        return dao.update(tenant, entity);
    }

    @Transactional
    public List<PaymentEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);
        String baseQuery = """
            SELECT DISTINCT p FROM PaymentEntity p
            LEFT JOIN FETCH p.sessionPackage sp
            LEFT JOIN FETCH sp.patient
        """;

        return em.createQuery(baseQuery, PaymentEntity.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public PaymentEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        PaymentEntity original = dao.findById(tenant, id, PaymentEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Pagamento não encontrado.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        PaymentEntity payment = dao.findById(tenant, id, PaymentEntity.class);
        if (payment == null) {
            throw new IllegalArgumentException("Pagamento não encontrado.");
        }
        dao.delete(tenant, id, PaymentEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, PaymentEntity.class);
    }

    @Transactional
    public List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        dao.defineSchema(tenant);

        String jpql = """
            SELECT new PaymentEntity(
                p.id,
                p.amount,
                p.paymentDate,
                p.paymentMethod,
                p.paid,
                p.observation,
                p.receiptName,
                p.receiptType
            )
            FROM PaymentEntity p
            WHERE p.sessionPackage.id = :sessionPackageId
              AND p.paid = true
            ORDER BY p.paymentDate ASC
        """;

        return em.createQuery(jpql, PaymentEntity.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .getResultList();
    }

    @Transactional
    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        dao.defineSchema(tenant);

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

    @Override
    public void makePayment(SecurityContext securityContext, String tenant, UserEntity loggedUser, String paymentId) {
        dao.defineSchema(tenant);

        List<PaymentEntity> result = em.createQuery("""
                SELECT p FROM PaymentEntity p
                WHERE p.paymentId = :paymentId
            """, PaymentEntity.class)
            .setParameter("paymentId", paymentId)
            .getResultList();

        if (result.isEmpty()) {
            throw new NotFoundException("Usuário com Keycloak ID não encontrado: " + paymentId);
        }

        dao.update(tenant, result.getFirst());
    }
}
