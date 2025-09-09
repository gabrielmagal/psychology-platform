package br.com.psicologia.context.user;

import br.com.psicologia.repository.model.UserEntity;
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
public class UserPacienteContext implements IContextUser<UserEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    private final String MSG_UNAUTHORIZED = "Sem permissão para criar usuário.";

    @Transactional
    public UserEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public UserEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        UserEntity original = dao.findById(tenant, entity.getId(), UserEntity.class);
        if (original.getKeycloakId().equals(loggedUser.getKeycloakId()) || original.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
            original.setFirstName(entity.getFirstName());
            original.setLastName(entity.getLastName());
            original.setEmail(entity.getEmail());
            original.setPhoneNumber(entity.getPhoneNumber());
            original.setBirthDate(entity.getBirthDate());
            original.setProfileImage(entity.getProfileImage());
            return dao.update(tenant, original);
        }
        throw new SecurityException("Paciente só pode alterar seus próprios dados.");
    }

    @Transactional
    public List<UserEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<UserEntity> query = cb.createQuery(UserEntity.class);
        Root<UserEntity> root = query.from(UserEntity.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("keycloakId"), loggedUser.getKeycloakId()));

        List<Predicate> filterPredicates = dao.buildPredicatesFromFilter(filter, cb, root, UserEntity.class);
        predicates.addAll(filterPredicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public UserEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        UserEntity original = dao.findById(tenant, id, UserEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (original.getKeycloakId().equals(loggedUser.getKeycloakId()) || original.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
            return original;
        }
        throw new SecurityException("Paciente só pode ver a sí mesmo.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new SecurityException("Pacientes não têm permissão para remover usuários.");
    }

    @Override
    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<UserEntity> root = query.from(UserEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("keycloakId"), userEntity.getKeycloakId()));

        List<Predicate> filterPredicates = dao.buildPredicatesFromFilter(filter, cb, root, UserEntity.class);
        predicates.addAll(filterPredicates);

        query.select(cb.count(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }

}
