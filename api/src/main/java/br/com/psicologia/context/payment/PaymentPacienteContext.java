package br.com.psicologia.context.payment;

import br.com.psicologia.context.payment.interfaces.IPaymentContextUser;
import br.com.psicologia.controller.dto.MakePaymentDto;
import br.com.psicologia.model.MercadoPagoInfoEntity;
import br.com.psicologia.model.PaymentEntity;
import br.com.psicologia.model.SessionPackageEntity;
import br.com.psicologia.model.UserEntity;
import core.service.KeycloakServiceImpl;
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
public class PaymentPacienteContext implements IPaymentContextUser {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    KeycloakServiceImpl keycloakService;

    private final String MSG_UNAUTHORIZED = "Sem permissão para essa ação.";

    @Transactional
    public PaymentEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, PaymentEntity entity) {
        entity.setSessionPackage(dao.findById(tenant, entity.getSessionPackage().getId(), SessionPackageEntity.class));
        UserEntity psicologo = keycloakService.findByKeycloakId(tenant, loggedUser.getRegisteredByKeycloakId());
        if (entity.getSessionPackage().getPsychologistId().equals(psicologo.getId())) {
            return dao.update(tenant, entity);
        }
        throw new IllegalArgumentException("O paciente só pode vincular pagamentos a si mesmo.");
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

    @Override
    public void makePayment(SecurityContext securityContext, String tenant, UserEntity loggedUser, MakePaymentDto makePaymentDto) {
        dao.defineSchema(tenant);

        PaymentEntity payment = em.createQuery("""
                SELECT p FROM PaymentEntity p
                WHERE p.paymentId = :paymentId
            """, PaymentEntity.class)
                .setParameter("paymentId", makePaymentDto.getPreferenceId())
                .getSingleResult();

        if (payment == null) {
            throw new NotFoundException("Pagamento pendente não encontrado com o ID: " + makePaymentDto.getPreferenceId());
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
