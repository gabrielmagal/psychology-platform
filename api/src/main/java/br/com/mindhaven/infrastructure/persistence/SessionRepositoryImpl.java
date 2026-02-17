package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.repository.SessionRepository;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionRepositoryImpl implements SessionRepository {

    @Inject
    GenericDao dao;

    @Override
    public SessionEntity save(String tenant, SessionEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public SessionEntity update(String tenant, SessionEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public SessionEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, SessionEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, SessionEntity.class);
    }

    @Override
    public List<SessionEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, SessionEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, SessionEntity.class);
    }
}
