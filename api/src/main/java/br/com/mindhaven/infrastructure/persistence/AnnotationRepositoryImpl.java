package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.AnnotationEntity;
import br.com.mindhaven.domain.repository.AnnotationRepository;
import core.repository.dao.GenericDao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationRepositoryImpl implements AnnotationRepository {

    @Inject
    GenericDao dao;

    @Override
    public AnnotationEntity save(String tenant, AnnotationEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AnnotationEntity update(String tenant, AnnotationEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AnnotationEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, AnnotationEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, AnnotationEntity.class);
    }

    @Override
    public List<AnnotationEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, AnnotationEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, AnnotationEntity.class);
    }

    @Override
    public List<AnnotationEntity> filteredFindPagedComPermissao(String tenant, br.com.mindhaven.domain.entity.UserEntity loggedUser, Object filter, int page, int size) {
        if (loggedUser.getUserType().equals(br.com.mindhaven.domain.entity.enums.UserType.ADMINISTRADOR)) {
            return filteredFindPaged(tenant, filter, page, size);
        }
        List<AnnotationEntity> all = filteredFindPaged(tenant, filter, page, size);
        if (loggedUser.getUserType().equals(br.com.mindhaven.domain.entity.enums.UserType.PSICOLOGO)) {
            return all.stream()
                .filter(a -> a.getSession() != null &&
                    a.getSession().getSessionPackage() != null &&
                    a.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId()))
                .toList();
        }
        if (loggedUser.getUserType().equals(br.com.mindhaven.domain.entity.enums.UserType.PACIENTE)) {
            return all.stream()
                .filter(a -> a.getSession() != null &&
                    a.getSession().getSessionPackage() != null &&
                    a.getSession().getSessionPackage().getPatient() != null &&
                    a.getSession().getSessionPackage().getPatient().getId().equals(loggedUser.getId()))
                .toList();
        }
        return List.of();
    }

    @Override
    public long countFilteredComPermissao(String tenant, br.com.mindhaven.domain.entity.UserEntity loggedUser, Object filter) {
        return filteredFindPagedComPermissao(tenant, loggedUser, filter, 0, Integer.MAX_VALUE).size();
    }
}
