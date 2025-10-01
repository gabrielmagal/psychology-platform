package br.com.psicologia.context.session;

import br.com.psicologia.model.SessionEntity;
import br.com.psicologia.model.UserEntity;
import core.context.IContextUser;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPacienteContext implements IContextUser<SessionEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    private final String MSG_UNAUTHORIZED = "Sem permissão para essa ação.";

    @Transactional
    public SessionEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public SessionEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, SessionEntity entity) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Override
    @Transactional
    public List<SessionEntity> filteredFindPaged(SecurityContext sc, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<SessionEntity> query = cb.createQuery(SessionEntity.class);
        Root<SessionEntity> root = query.from(SessionEntity.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.join("sessionPackage").get("patient").get("id"), userEntity.getId()));

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
        if (!original.getSessionPackage().getPatient().getId().toString().equals(loggedUser.getId().toString())) {
            throw new SecurityException("Paciente só pode ver suas próprias informações.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new RuntimeException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<SessionEntity> root = query.from(SessionEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.join("sessionPackage").get("patient").get("id"), userEntity.getId()));

        List<Predicate> filterParamPredicates = dao.buildPredicatesFromFilter(filter, cb, root, SessionEntity.class);
        predicates.addAll(filterParamPredicates);

        query.select(cb.count(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }
}
