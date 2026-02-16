package br.com.psicologia.domain.repository;

import br.com.psicologia.domain.entity.MercadoPagoInfoEntity;

import java.util.List;
import java.util.UUID;

public interface MercadoPagoInfoRepository {
    MercadoPagoInfoEntity save(String tenant, MercadoPagoInfoEntity entity);
    MercadoPagoInfoEntity update(String tenant, MercadoPagoInfoEntity entity);
    MercadoPagoInfoEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<MercadoPagoInfoEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
}

