package br.com.psicologia.context.annotation;

import br.com.psicologia.repository.model.AnnotationEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.context.IContextUser;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationAdministradorContext implements IContextUser<AnnotationEntity> {

    @Inject
    GenericDao dao;

    @Transactional
    public AnnotationEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        return dao.update(tenant, entity);
    }

    @Transactional
    public AnnotationEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        AnnotationEntity original = dao.findById(tenant, entity.getId(), AnnotationEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        return dao.update(tenant, entity);
    }

    @Transactional
    public List<AnnotationEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter, int page, int size) {
        return dao.filteredFindPaged(tenant, filter, page, size, AnnotationEntity.class);
    }

    @Transactional
    public AnnotationEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity original = dao.findById(tenant, id, AnnotationEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity session = dao.findById(tenant, id, AnnotationEntity.class);
        if (session == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        dao.delete(tenant, id, AnnotationEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, AnnotationEntity.class);
    }
}
