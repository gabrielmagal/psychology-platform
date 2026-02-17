package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.MercadoPagoInfoUseCase;
import br.com.mindhaven.domain.entity.MercadoPagoInfoEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MercadoPagoInfoService extends AbstractCrudService<MercadoPagoInfoEntity> {
    @Inject
    MercadoPagoInfoUseCase mercadoPagoInfoUseCase;

    public MercadoPagoInfoService() {
        super(MercadoPagoInfoEntity.class);
    }

    public MercadoPagoInfoEntity save(MercadoPagoInfoEntity entity) {
        return mercadoPagoInfoUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public MercadoPagoInfoEntity update(MercadoPagoInfoEntity entity) {
        return mercadoPagoInfoUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public MercadoPagoInfoEntity findById(UUID id) {
        return mercadoPagoInfoUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        mercadoPagoInfoUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<MercadoPagoInfoEntity> filteredFindPaged(Filter filter, int page, int size) {
        return mercadoPagoInfoUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return mercadoPagoInfoUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    public String createPaymentPreference(UUID packageId) {
        return mercadoPagoInfoUseCase.createPaymentPreference(getTenant(), getCurrentLoggedUser(), packageId);
    }
}
