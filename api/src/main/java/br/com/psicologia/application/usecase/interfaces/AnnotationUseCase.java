package br.com.psicologia.application.usecase.interfaces;

import br.com.psicologia.domain.entity.AnnotationEntity;
import br.com.psicologia.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface AnnotationUseCase {
    AnnotationEntity save(String tenant, UserEntity loggedUser, AnnotationEntity entity);
    AnnotationEntity update(String tenant, UserEntity loggedUser, AnnotationEntity entity);
    AnnotationEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<AnnotationEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
}
