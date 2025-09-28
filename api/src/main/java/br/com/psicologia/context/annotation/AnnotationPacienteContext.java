package br.com.psicologia.context.annotation;

import br.com.psicologia.repository.model.AnnotationEntity;
import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.context.IContextUser;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationPacienteContext implements IContextUser<AnnotationEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    private final String MSG_UNAUTHORIZED = "Sem permissão para essa ação.";

    @Transactional
    public AnnotationEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public AnnotationEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public List<AnnotationEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<AnnotationEntity> query = cb.createQuery(AnnotationEntity.class);
        Root<AnnotationEntity> root = query.from(AnnotationEntity.class);
        query.select(root).distinct(true);

        Join<AnnotationEntity, SessionEntity> sessionJoin = root.join("session", JoinType.INNER);
        Join<SessionEntity, SessionPackageEntity> packageJoin = sessionJoin.join("sessionPackage", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(packageJoin.get("patient").get("id"), userEntity.getId()));

        List<Predicate> filterParamPredicates = dao.buildPredicatesFromFilter(filter, cb, root, AnnotationEntity.class);
        predicates.addAll(filterParamPredicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public AnnotationEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity original = dao.findById(tenant, id, AnnotationEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        if (original.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return original;
        }
        throw new SecurityException("Psicólogo só pode ver suas próprias anotações.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity original = dao.findById(tenant, id, AnnotationEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        if (original.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            dao.delete(tenant, id, SessionEntity.class);
        }
        throw new SecurityException("Psicólogo só pode deletar sessões dos próprios pacientes.");
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<AnnotationEntity> root = query.from(AnnotationEntity.class);

        Join<AnnotationEntity, SessionEntity> sessionJoin = root.join("session", JoinType.INNER);
        Join<SessionEntity, SessionPackageEntity> packageJoin = sessionJoin.join("sessionPackage", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(packageJoin.get("patient").get("id"), userEntity.getId()));

        List<Predicate> filterParamPredicates = dao.buildPredicatesFromFilter(filter, cb, root, AnnotationEntity.class);
        predicates.addAll(filterParamPredicates);

        query.select(cb.countDistinct(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }
}
