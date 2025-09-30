package br.com.psicologia.context.payment;

import br.com.psicologia.context.payment.interfaces.IPaymentContextUser;
import br.com.psicologia.context.sessionpackage.SessionPackageContext;
import br.com.psicologia.controller.dto.MakePaymentDto;
import br.com.psicologia.repository.model.MercadoPagoInfoEntity;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.UserEntity;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
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
public class PaymentPsicologoContext implements IPaymentContextUser {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    SessionPackageContext sessionPackageContext;

    @Transactional
    public PaymentEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        entity.setSessionPackage(sessionPackageContext.findById(securityContext, tenant, entity.getSessionPackage().getId()));
        if (entity.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return dao.update(tenant, entity);
        }
        throw new IllegalArgumentException("O Psicologo só pode vincular pagamentos aos seus próprios pacientes e sessões.");
    }

    @Transactional
    public PaymentEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        entity.setSessionPackage(sessionPackageContext.findById(securityContext, tenant, entity.getSessionPackage().getId()));
        if (entity.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return dao.update(tenant, entity);
        }
        throw new IllegalArgumentException("O Psicologo só pode vincular pagamentos aos seus próprios pacientes e sessões.");
    }

    @Transactional
    public List<PaymentEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PaymentEntity> query = cb.createQuery(PaymentEntity.class);
        Root<PaymentEntity> root = query.from(PaymentEntity.class);
        root.fetch("sessionPackage", JoinType.LEFT).fetch("patient", JoinType.LEFT); // fetch joins

        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        Join<Object, Object> sp = root.join("sessionPackage", JoinType.LEFT);
        predicates.add(cb.equal(sp.get("psychologistId"), userEntity.getId()));

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
        if (original.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return original;
        }
        throw new SecurityException("Psicólogo só pode ver seus próprios pagamentos.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        PaymentEntity original = dao.findById(tenant, id, PaymentEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Pagamento não encontrado.");
        }
        if (original.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            dao.delete(tenant, id, PaymentEntity.class);
            return;
        }
        throw new SecurityException("Psicólogo só pode deletar seus próprios pagamentos.");
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<PaymentEntity> root = query.from(PaymentEntity.class);

        Join<Object, Object> sp = root.join("sessionPackage", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(sp.get("psychologistId"), userEntity.getId()));

        List<Predicate> dynamicPredicates = dao.buildPredicatesFromFilter(filter, cb, root, PaymentEntity.class);
        predicates.addAll(dynamicPredicates);

        query.select(cb.countDistinct(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
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
              AND p.sessionPackage.psychologistId = :psychologistId
              AND p.paid = true
            ORDER BY p.paymentDate ASC
        """;

        return em.createQuery(jpql, PaymentEntity.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .setParameter("psychologistId", loggedUser.getId())
                .getResultList();
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        dao.defineSchema(tenant);

        String jpql = """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PaymentEntity p
            WHERE p.sessionPackage.id = :sessionPackageId
              AND p.sessionPackage.psychologistId= :psychologistId
              AND p.paid = true
        """;

        return em.createQuery(jpql, BigDecimal.class)
                .setParameter("sessionPackageId", sessionPackageId)
                .setParameter("psychologistId", loggedUser.getId())
                .getSingleResult();
    }

    @Override
    public void makePayment(SecurityContext securityContext, String tenant, UserEntity loggedUser, MakePaymentDto makePaymentDto) {
        dao.defineSchema(tenant);

        PaymentEntity payment = em.createQuery("""
                SELECT p FROM PaymentEntity p
                WHERE p.paymentId = :paymentId
            """, PaymentEntity.class)
                .setParameter("paymentId", makePaymentDto.getPaymentId())
                .getSingleResult();

        if (payment == null) {
            throw new NotFoundException("Pagamento pendente não encontrado com o ID: " + makePaymentDto.getPaymentId());
        }

        MercadoPagoInfoEntity mpConfig = dao.findById(tenant, payment.getSessionPackage().getPsychologistId(), MercadoPagoInfoEntity.class);

        if (mpConfig == null || mpConfig.getAccessToken() == null) {
            throw new IllegalStateException("Configuração Mercado Pago não encontrada (access token faltando).");
        }

        try {
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(mpConfig.getAccessToken())
                    .build();

            PaymentClient paymentClient = new PaymentClient();
            com.mercadopago.resources.payment.Payment mpPayment = paymentClient.get(makePaymentDto.getPaymentId(), requestOptions);

            payment.setPaid(true);
            payment.setPaymentDate(mpPayment.getDateApproved().toLocalDate());

        } catch (MPApiException | MPException e) {
            throw new RuntimeException("Erro ao consultar pagamento Mercado Pago", e);
        }

        dao.update(tenant, payment);
    }
}
