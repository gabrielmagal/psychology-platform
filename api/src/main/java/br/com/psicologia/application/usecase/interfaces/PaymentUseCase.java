package br.com.psicologia.application.usecase.interfaces;

import br.com.psicologia.domain.entity.PaymentEntity;
import br.com.psicologia.domain.entity.UserEntity;
import br.com.psicologia.adapter.controller.dto.MakePaymentDto;
import core.service.model.Filter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentUseCase {
    PaymentEntity save(String tenant, UserEntity loggedUser, PaymentEntity entity);
    PaymentEntity update(String tenant, UserEntity loggedUser, PaymentEntity entity);
    PaymentEntity findById(String tenant, UserEntity loggedUser, UUID id);
    void delete(String tenant, UserEntity loggedUser, UUID id);
    List<PaymentEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size);
    long countFiltered(String tenant, UserEntity loggedUser, Filter filter);
    List<PaymentEntity> findPaidBySessionPackage(String tenant, UserEntity loggedUser, UUID sessionPackageId);
    BigDecimal sumPaidAmountBySessionPackage(String tenant, UserEntity loggedUser, UUID sessionPackageId);
    void makePayment(String tenant, UserEntity loggedUser, MakePaymentDto makePaymentDto);
}
