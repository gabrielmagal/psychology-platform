package br.com.psicologia.context.payment;

import br.com.psicologia.context.payment.interfaces.IPaymentContextUser;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentPacienteContext implements IPaymentContextUser {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    private final String MSG_UNAUTHORIZED = "Sem permissão para essa ação.";

    @Transactional
    public PaymentEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public PaymentEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public List<PaymentEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PaymentEntity> query = cb.createQuery(PaymentEntity.class);
        Root<PaymentEntity> root = query.from(PaymentEntity.class);
        root.fetch("sessionPackage", JoinType.LEFT).fetch("patient", JoinType.LEFT);

        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        Join<Object, Object> sp = root.join("sessionPackage", JoinType.LEFT);
        predicates.add(cb.equal(sp.get("patient").get("id"), userEntity.getId()));

        List<Predicate> dynamicPredicates = dao.buildPredicatesFromFilter(filter, cb, root, PaymentEntity.class);
        predicates.addAll(dynamicPredicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query)
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
        if (!original.getSessionPackage().getPatient().getId().equals(loggedUser.getId())) {
            throw new SecurityException("Paciente só pode ver seus próprios pagamentos.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, PaymentEntity.class);
    }

    @Override
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
              AND p.sessionPackage.patient.id = :patientId
              AND p.paid = true
            ORDER BY p.paymentDate ASC
        """;

        return em.createQuery(jpql, PaymentEntity.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .setParameter("patientId", loggedUser.getId())
                .getResultList();
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        dao.defineSchema(tenant);

        String jpql = """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PaymentEntity p
            WHERE p.sessionPackage.id = :sessionPackageId
              AND p.sessionPackage.patient.id = :patientId
              AND p.paid = true
        """;

        return em.createQuery(jpql, BigDecimal.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .setParameter("patientId", loggedUser.getId())
                .getSingleResult();
    }

    // Implementar validação com Session do usuário
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
