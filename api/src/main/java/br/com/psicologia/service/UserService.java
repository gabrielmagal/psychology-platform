package br.com.psicologia.service;

import br.com.psicologia.repository.model.UserEntity;
import core.repository.dao.GenericDao;
import core.service.AbstractCrudService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class UserService extends AbstractCrudService<UserEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao genericDao;

    @Inject
    KeycloakService keycloakService;

    public UserService() {
        super(UserEntity.class);
    }

    @Transactional
    @Override
    public UserEntity save(String tenant, UserEntity entity) {
        Set<String> roles = switch (entity.getUserType()) {
            case PSICOLOGO -> Set.of("PSICOLOGO_ROLE");
            case PACIENTE  -> Set.of("PACIENTE_ROLE");
        };

        String userId = keycloakService.createUser(tenant, entity, roles);

        entity.setKeycloakId(userId);

        UserEntity saved = dao.update(tenant, entity);
        return dao.findById(tenant, saved.getId(), UserEntity.class);
    }

    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        genericDao.defineSchema(tenant);
        em.createNativeQuery("SET search_path TO " + tenant).executeUpdate();

        List<UserEntity> result = em.createQuery("""
            SELECT u FROM UserEntity u
            WHERE u.keycloakId = :keycloakId
        """, UserEntity.class)
                .setParameter("keycloakId", keycloakId)
                .getResultList();

        if (result.isEmpty()) {
            throw new NotFoundException("Usuário com Keycloak ID não encontrado: " + keycloakId);
        }

        return result.getFirst();
    }
}
