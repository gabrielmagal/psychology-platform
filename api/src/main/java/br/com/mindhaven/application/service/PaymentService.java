package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.PaymentUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.adapter.controller.dto.MakePaymentDto;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentService extends AbstractCrudService<PaymentEntity> {
    @Inject
    PaymentUseCase paymentUseCase;

    public PaymentService() {
        super(PaymentEntity.class);
    }

    public PaymentEntity save(PaymentEntity entity) {
        return paymentUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
    }

    public PaymentEntity update(PaymentEntity entity) {
        return paymentUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
    }

    public PaymentEntity findById(UUID id) {
        return paymentUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
    }

    public void delete(UUID id) {
        paymentUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    public List<PaymentEntity> filteredFindPaged(Filter filter, int page, int size) {
        return paymentUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
    }

    public long countFiltered(Filter filter) {
        return paymentUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    public List<PaymentEntity> findAllPaged(int page, int size) {
        return List.of();
    }

    public List<PaymentEntity> findPaidBySessionPackage(UUID sessionPackageId) {
        return paymentUseCase.findPaidBySessionPackage(getTenant(), getCurrentLoggedUser(), sessionPackageId);
    }

    public BigDecimal sumPaidAmountBySessionPackage(UUID sessionPackageId) {
        return paymentUseCase.sumPaidAmountBySessionPackage(getTenant(), getCurrentLoggedUser(), sessionPackageId);
    }

    public void makePayment(MakePaymentDto makePaymentDto) {
        paymentUseCase.makePayment(getTenant(), getCurrentLoggedUser(), makePaymentDto);
    }
}
