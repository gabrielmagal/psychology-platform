package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.MercadoPagoInfoEntity;
import br.com.mindhaven.domain.repository.MercadoPagoInfoRepository;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MercadoPagoInfoRepositoryImpl implements MercadoPagoInfoRepository {

    @Inject
    GenericDao dao;

    @Override
    public MercadoPagoInfoEntity save(String tenant, MercadoPagoInfoEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public MercadoPagoInfoEntity update(String tenant, MercadoPagoInfoEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public MercadoPagoInfoEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, MercadoPagoInfoEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, MercadoPagoInfoEntity.class);
    }

    @Override
    public List<MercadoPagoInfoEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, MercadoPagoInfoEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, MercadoPagoInfoEntity.class);
    }
}
