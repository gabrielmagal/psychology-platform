package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.repository.SessionPackageRepository;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageRepositoryImpl implements SessionPackageRepository {

    @Inject
    GenericDao dao;

    @Override
    public SessionPackageEntity save(String tenant, SessionPackageEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public SessionPackageEntity update(String tenant, SessionPackageEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public SessionPackageEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, SessionPackageEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, SessionEntity.class);
    }

    @Override
    public List<SessionPackageEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, SessionPackageEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, SessionPackageEntity.class);
    }
}
