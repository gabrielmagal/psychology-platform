package br.com.psicologia.infrastructure.persistence;

import br.com.psicologia.domain.entity.MercadoPagoInfoEntity;
import br.com.psicologia.domain.entity.PaymentEntity;
import br.com.psicologia.domain.repository.PaymentRepository;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentRepositoryImpl implements PaymentRepository {

    @Inject
    GenericDao dao;

    @Override
    public PaymentEntity save(String tenant, PaymentEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public PaymentEntity update(String tenant, PaymentEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public PaymentEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, PaymentEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, PaymentEntity.class);
    }

    @Override
    public List<PaymentEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (core.service.model.Filter) filter, page, size, PaymentEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (core.service.model.Filter) filter, PaymentEntity.class);
    }

    @Override
    public PaymentEntity findByPaymentId(String tenant, String paymentId) {
        var filter = new core.service.model.Filter();
        filter.setFilterParams(List.of(new core.service.model.FilterParam("paymentId", paymentId)));
        List<PaymentEntity> result = dao.filteredFindPaged(tenant, filter, 0, 1, PaymentEntity.class);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public void updatePaidStatus(String tenant, String paymentId, boolean paid, java.time.LocalDate paymentDate) {
        PaymentEntity payment = findByPaymentId(tenant, paymentId);
        if (payment != null) {
            payment.setPaid(paid);
            payment.setPaymentDate(paymentDate);
            dao.update(tenant, payment);
        }
    }

    @Override
    public MercadoPagoInfoEntity findMercadoPagoInfoByPsychologistId(String tenant, java.util.UUID psychologistId) {
        var filter = new Filter();
        filter.setFilterParams(List.of(new FilterParam("user.id", psychologistId.toString())));
        List<MercadoPagoInfoEntity> result = dao.filteredFindPaged(tenant, filter, 0, 1, MercadoPagoInfoEntity.class);
        return result.isEmpty() ? null : result.getFirst();
    }
}
