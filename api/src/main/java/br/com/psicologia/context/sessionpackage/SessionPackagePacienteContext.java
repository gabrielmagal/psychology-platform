package br.com.psicologia.context.sessionpackage;

import br.com.psicologia.context.sessionpackage.interfaces.ISessionPackageContextUser;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackagePacienteContext implements ISessionPackageContextUser {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Transactional
    public SessionPackageEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        throw new SecurityException("Você não tem permissão para adicionar pacote de sessão;");
    }

    @Transactional
    public SessionPackageEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        throw new SecurityException("Você não tem permissão para atualizar pacote de sessão.");
    }

    @Transactional
    public List<SessionPackageEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<SessionPackageEntity> query = cb.createQuery(SessionPackageEntity.class);
        Root<SessionPackageEntity> root = query.from(SessionPackageEntity.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("patient").get("id"), userEntity.getId()));

        if (filter != null && filter.getFilterParams() != null) {
            List<Predicate> customPredicates = dao.buildPredicatesFromFilter(filter, cb, root, SessionPackageEntity.class);
            predicates.addAll(customPredicates);
        }

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        List<SessionPackageEntity> result = em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        result.forEach(e -> {
            e.getSession().size();
            e.getPayment().size();
            e.getPatient().getId();
        });

        return result;
    }



    @Transactional
    public SessionPackageEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity original = dao.findById(tenant, id, SessionPackageEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        if (original.getPsychologistId().toString().equals(loggedUser.getKeycloakId())) {
            return original;
        }
        throw new SecurityException("Você não tem permissão para obter pacote de sessão de outros psicólogos.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity original = dao.findById(tenant, id, SessionPackageEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        if (original.getPsychologistId().toString().equals(loggedUser.getKeycloakId())) {
            dao.delete(tenant, id, SessionPackageEntity.class);
            return;
        }
        throw new SecurityException("Você não tem permissão para remover pacote de sessão de outros psicólogos.");
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<SessionPackageEntity> root = query.from(SessionPackageEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("patient").get("id"), userEntity.getId()));

        if (filter != null && filter.getFilterParams() != null) {
            List<Predicate> customPredicates = dao.buildPredicatesFromFilter(filter, cb, root, SessionPackageEntity.class);
            predicates.addAll(customPredicates);
        }

        query.select(cb.countDistinct(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }

    @Override
    public List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        return List.of();
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId) {
        return null;
    }
}
