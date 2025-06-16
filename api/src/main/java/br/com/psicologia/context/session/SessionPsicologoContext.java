package br.com.psicologia.context.session;

import br.com.psicologia.context.sessionpackage.SessionPackageContext;
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
public class SessionPsicologoContext implements IContextUser<SessionEntity> {

    @Inject
    SessionPackageContext sessionPackageContext;

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    @Transactional
    public SessionEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        SessionPackageEntity pacote = entity.getSessionPackage();
        if (pacote.getId() != null && (pacote.getPsychologistId() == null || !pacote.getPsychologistId().equals(loggedUser.getId()))) {
            pacote = sessionPackageContext.findById(securityContext, tenant, pacote.getId());
        }
        if (pacote != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
            entity.setSessionPackage(pacote);
            return dao.update(tenant, entity);
        }
        throw new SecurityException("Psicólogo só pode criar sessões nos próprios pacotes.");
    }

    @Transactional
    public SessionEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        SessionEntity original = dao.findById(tenant, entity.getId(), SessionEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        if (original.getSessionPackage() != null && original.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return dao.update(tenant, entity);
        } else {
            throw new SecurityException("Psicólogo só pode alterar sessões dos próprios pacientes.");
        }
    }

    @Transactional
    public List<SessionEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<SessionEntity> query = cb.createQuery(SessionEntity.class);
        Root<SessionEntity> root = query.from(SessionEntity.class);
        query.select(root).distinct(true);

        root.fetch("annotation", JoinType.LEFT);
        root.join("sessionPackage", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("sessionPackage").get("psychologistId"), userEntity.getId()));

        List<Predicate> filterParamPredicates = dao.buildPredicatesFromFilter(filter, cb, root, SessionEntity.class);
        predicates.addAll(filterParamPredicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public SessionEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity original = dao.findById(tenant, id, SessionEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        if (original.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
            return original;
        }
        throw new SecurityException("Psicólogo só pode ver a sí mesmo e seus pacientes.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity session = dao.findById(tenant, id, SessionEntity.class);
        if (session == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        SessionPackageEntity pacote = session.getSessionPackage();
        if (pacote != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
            dao.delete(tenant, id, SessionEntity.class);
        } else {
            throw new SecurityException("Psicólogo só pode deletar sessões dos próprios pacientes.");
        }
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<SessionEntity> root = query.from(SessionEntity.class);

        root.join("sessionPackage", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("sessionPackage").get("psychologistId"), userEntity.getId()));

        List<Predicate> filterParamPredicates = dao.buildPredicatesFromFilter(filter, cb, root, SessionEntity.class);
        predicates.addAll(filterParamPredicates);

        query.select(cb.countDistinct(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }

}
