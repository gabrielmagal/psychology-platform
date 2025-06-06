package br.com.psicologia.service;

import br.com.psicologia.repository.model.AnnotationEntity;
import core.service.AbstractCrudService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AnnotationService extends AbstractCrudService<AnnotationEntity> {
    public AnnotationService() {
        super(AnnotationEntity.class);
    }
}
