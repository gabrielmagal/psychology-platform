package br.com.mindhaven.application.usecase.interfaces;

import br.com.mindhaven.domain.entity.MercadoPagoInfoEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;

public interface MercadoPagoInfoUseCase {
    MercadoPagoInfoEntity save(String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity);
    MercadoPagoInfoEntity update(String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity);
    MercadoPagoInfoEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<MercadoPagoInfoEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
    String createPaymentPreference(String tenant, UserEntity loggedUser, UUID packageId);
}
