package br.com.psicologia.context.user;

import br.com.psicologia.enums.UserType;
import br.com.psicologia.repository.model.UserEntity;
import br.com.psicologia.service.KeycloakService;
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
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserPsicologoContext implements IContextUser<UserEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    @Inject
    KeycloakService keycloakService;

    @Transactional
    public UserEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        if (entity.getUserType() == UserType.PACIENTE)
        {
            Set<String> roles = Set.of("PACIENTE_ROLE");

            String userId = keycloakService.createUser(tenant, entity, roles);
            entity.setKeycloakId(userId);
            entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());

            UserEntity saved = dao.update(tenant, entity);
            return dao.findById(tenant, saved.getId(), UserEntity.class);
        }

        throw new SecurityException("Psicólogos só podem criar pacientes.");
    }

    @Transactional
    public UserEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, UserEntity entity) {
        if (entity.getKeycloakId().equals(loggedUser.getKeycloakId()) || entity.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
            UserEntity original = dao.findById(tenant, entity.getId(), UserEntity.class);
            original.setFirstName(entity.getFirstName());
            original.setLastName(entity.getLastName());
            original.setEmail(entity.getEmail());
            original.setPhoneNumber(entity.getPhoneNumber());
            original.setBirthDate(entity.getBirthDate());
            original.setProfileImage(entity.getProfileImage());
            return dao.update(tenant, original);
        }
        throw new SecurityException("Psicólogos só podem editar seus próprios dados ou de seus pacientes.");
    }

    @Transactional
    public List<UserEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<UserEntity> query = cb.createQuery(UserEntity.class);
        Root<UserEntity> root = query.from(UserEntity.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        Predicate registeredBy = cb.equal(root.get("registeredByKeycloakId"), loggedUser.getKeycloakId());
        Predicate self = cb.equal(root.get("keycloakId"), loggedUser.getKeycloakId());
        predicates.add(cb.or(registeredBy, self));


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
        UserEntity user = dao.findById(tenant, id, UserEntity.class);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (user.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId()) || user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
            return user;
        }
        throw new SecurityException("Psicólogo só pode ver a sí mesmo e seus pacientes.");
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        UserEntity user = dao.findById(tenant, id, UserEntity.class);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (user.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId()) || user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
            dao.delete(tenant, id, UserEntity.class);
            return;
        }
        throw new SecurityException("Psicólogo só pode gerenciar a sí mesmo e seus pacientes.");
    }

    @Override
    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        dao.defineSchema(tenant);

        CriteriaBuilder cb = dao.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<UserEntity> root = query.from(UserEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate registeredBy = cb.equal(root.get("registeredByKeycloakId"), userEntity.getKeycloakId());
        Predicate self = cb.equal(root.get("keycloakId"), userEntity.getKeycloakId());
        predicates.add(cb.or(registeredBy, self));

        List<Predicate> filterPredicates = dao.buildPredicatesFromFilter(filter, cb, root, UserEntity.class);
        predicates.addAll(filterPredicates);

        query.select(cb.count(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
    }
}
