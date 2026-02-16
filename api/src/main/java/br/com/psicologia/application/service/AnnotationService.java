package br.com.psicologia.application.service;

import br.com.psicologia.application.usecase.interfaces.AnnotationUseCase;
import br.com.psicologia.domain.entity.AnnotationEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationService extends AbstractCrudService<AnnotationEntity> {
    @Inject
    AnnotationUseCase annotationUseCase;

    public AnnotationService() {
        super(AnnotationEntity.class);
    }

    public AnnotationEntity save(AnnotationEntity entity) {
        return annotationUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public AnnotationEntity update(AnnotationEntity entity) {
        return annotationUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public AnnotationEntity findById(UUID id) {
        return annotationUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        annotationUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<AnnotationEntity> filteredFindPaged(Filter filter, int page, int size) {
        return annotationUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return annotationUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }
}
