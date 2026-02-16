package br.com.psicologia.domain.repository;

import br.com.psicologia.domain.entity.UserEntity;

import java.util.List;
import java.util.UUID;

public interface UserRepository {
    UserEntity save(String tenant, UserEntity entity);
    UserEntity update(String tenant, UserEntity entity);
    UserEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<UserEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
    List<UserEntity> filteredFindPagedComPermissao(String tenant, UserEntity loggedUser, Object filter, int page, int size);
    long countFilteredComPermissao(String tenant, UserEntity loggedUser, Object filter);
}
