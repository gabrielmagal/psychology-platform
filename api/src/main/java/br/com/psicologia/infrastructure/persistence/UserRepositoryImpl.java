package br.com.psicologia.infrastructure.persistence;

import br.com.psicologia.domain.entity.UserEntity;
import br.com.psicologia.domain.entity.enums.UserType;
import br.com.psicologia.domain.repository.UserRepository;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserRepositoryImpl implements UserRepository {

    @Inject
    GenericDao dao;

    @Override
    public UserEntity save(String tenant, UserEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public UserEntity update(String tenant, UserEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public UserEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, UserEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, UserEntity.class);
    }

    @Override
    public List<UserEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, UserEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, UserEntity.class);
    }

    @Override
    public List<UserEntity> filteredFindPagedComPermissao(String tenant, UserEntity loggedUser, Object filter, int page, int size) {
        if (loggedUser.getUserType().equals(UserType.ADMINISTRADOR)) {
            return filteredFindPaged(tenant, filter, page, size);
        }
        List<UserEntity> all = filteredFindPaged(tenant, filter, page, size);
        if (loggedUser.getUserType().equals(UserType.PSICOLOGO)) {
            return all.stream()
                .filter(u ->
                    u.getKeycloakId().equals(loggedUser.getKeycloakId()) ||
                    (u.getRegisteredByKeycloakId() != null && u.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId()))
                ).toList();
        }
        if (loggedUser.getUserType().equals(UserType.PACIENTE)) {
            return all.stream()
                .filter(u -> u.getKeycloakId().equals(loggedUser.getKeycloakId()))
                .toList();
        }
        return List.of();
    }

    @Override
    public long countFilteredComPermissao(String tenant, UserEntity loggedUser, Object filter) {
        return filteredFindPagedComPermissao(tenant, loggedUser, filter, 0, Integer.MAX_VALUE).size();
    }
}
