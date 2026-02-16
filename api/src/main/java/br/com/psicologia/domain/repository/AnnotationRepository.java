package br.com.psicologia.domain.repository;

import br.com.psicologia.domain.entity.AnnotationEntity;
import br.com.psicologia.domain.entity.UserEntity;
import java.util.List;
import java.util.UUID;

public interface AnnotationRepository {
    AnnotationEntity save(String tenant, AnnotationEntity entity);
    AnnotationEntity update(String tenant, AnnotationEntity entity);
    AnnotationEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<AnnotationEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
    List<AnnotationEntity> filteredFindPagedComPermissao(String tenant, UserEntity loggedUser, Object filter, int page, int size);
    long countFilteredComPermissao(String tenant, UserEntity loggedUser, Object filter);
}
