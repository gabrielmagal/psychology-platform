package br.com.mindhaven.domain.repository;

import br.com.mindhaven.domain.entity.PaymentEntity;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository {
    PaymentEntity save(String tenant, PaymentEntity entity);
    PaymentEntity update(String tenant, PaymentEntity entity);
    PaymentEntity findById(String tenant, UUID id);
    void delete(String tenant, UUID id);
    List<PaymentEntity> filteredFindPaged(String tenant, Object filter, int page, int size);
    long countFiltered(String tenant, Object filter);
    PaymentEntity findByPaymentId(String tenant, String paymentId);
    void updatePaidStatus(String tenant, String paymentId, boolean paid, java.time.LocalDate paymentDate);
    br.com.mindhaven.domain.entity.MercadoPagoInfoEntity findMercadoPagoInfoByPsychologistId(String tenant, java.util.UUID psychologistId);
}
