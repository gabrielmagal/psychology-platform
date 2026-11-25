package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.PaymentUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.repository.PaymentRepository;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import core.error.BusinessException;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentUseCaseImpl implements PaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final br.com.mindhaven.domain.repository.SessionPackageRepository sessionPackageRepository;

    public PaymentUseCaseImpl(PaymentRepository paymentRepository,
                              br.com.mindhaven.domain.repository.SessionPackageRepository sessionPackageRepository) {
        this.sessionPackageRepository = sessionPackageRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentEntity save(String tenant, UserEntity loggedUser, PaymentEntity entity) {
        // O pacote chega do cliente só como {"id": ...}. A verificação de dono olhava o
        // professionalId desse objeto — sempre nulo —, então nenhum profissional conseguia
        // lançar pagamento. Carregando o pacote gravado, a regra vale sobre o dado real (e
        // não sobre o que o cliente diz).
        if (loggedUser.getUserType() != br.com.mindhaven.domain.entity.enums.UserType.PACIENTE
                && entity.getSessionPackage() != null && entity.getSessionPackage().getId() != null) {
            SessionPackageEntity stored = sessionPackageRepository.findById(tenant, entity.getSessionPackage().getId());
            if (stored == null) {
                throw BusinessException.invalid("Pacote de sessões não encontrado.");
            }
            entity.setSessionPackage(stored);
        }
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> paymentRepository.save(tenant, entity);
            case PROFISSIONAL -> {
                if (entity.getSessionPackage() != null && entity.getSessionPackage().getProfessionalId() != null && entity.getSessionPackage().getProfessionalId().equals(loggedUser.getId())) {
                    yield paymentRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Profissional só pode salvar pagamentos dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode salvar pagamentos.");
        };
    }

    @Override
    public PaymentEntity update(String tenant, UserEntity loggedUser, PaymentEntity entity) {
        return save(tenant, loggedUser, entity);
    }

    @Override
    public PaymentEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        PaymentEntity payment = paymentRepository.findById(tenant, id);
        if (payment == null) return null;
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> payment;
            case PROFISSIONAL -> {
                if (payment.getSessionPackage() != null && payment.getSessionPackage().getProfessionalId() != null && payment.getSessionPackage().getProfessionalId().equals(loggedUser.getId())) {
                    yield payment;
                } else {
                    throw new SecurityException("Profissional só pode acessar pagamentos dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode acessar pagamentos.");
        };
    }

    @Override
    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        PaymentEntity payment = paymentRepository.findById(tenant, id);
        if (payment == null) throw new IllegalArgumentException("Pagamento não encontrado.");
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> paymentRepository.delete(tenant, id);
            case PROFISSIONAL -> {
                if (payment.getSessionPackage() != null && payment.getSessionPackage().getProfessionalId() != null && payment.getSessionPackage().getProfessionalId().equals(loggedUser.getId())) {
                    paymentRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Profissional só pode deletar pagamentos dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode deletar pagamentos.");
        }
    }

    @Override
    public List<PaymentEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PROFISSIONAL) {
            if (filter.getFilterParams() == null) {
                filter.setFilterParams(new ArrayList<>());
            }
            filter.getFilterParams().add(new FilterParam("sessionPackage.professionalId", loggedUser.getId().toString()));
        }
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PACIENTE) {
            throw new SecurityException("Paciente não pode listar pagamentos.");
        }
        return paymentRepository.filteredFindPaged(tenant, filter, page, size);
    }

    @Override
    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PROFISSIONAL) {
            if (filter.getFilterParams() == null) {
                filter.setFilterParams(new ArrayList<>());
            }
            filter.getFilterParams().add(new FilterParam("sessionPackage.professionalId", loggedUser.getId().toString()));
        }
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PACIENTE) {
            throw new SecurityException("Paciente não pode contar pagamentos.");
        }
        return paymentRepository.countFiltered(tenant, filter);
    }

    @Override
    public List<PaymentEntity> findPaidBySessionPackage(String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        Filter filter = new Filter();
        List<FilterParam> params = new ArrayList<>();
        params.add(new FilterParam("sessionPackage.id", sessionPackageId.toString()));
        params.add(new FilterParam("paid", "true"));
        filter.setFilterParams(params);
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PACIENTE) {
            // O paciente consulta o que já pagou, mas só nos pacotes em que ele é o paciente.
            params.add(new FilterParam("sessionPackage.patient.id", loggedUser.getId().toString()));
            return paymentRepository.filteredFindPaged(tenant, filter, 0, Integer.MAX_VALUE);
        }
        return filteredFindPaged(tenant, loggedUser, filter, 0, Integer.MAX_VALUE);
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        List<PaymentEntity> pagos = findPaidBySessionPackage(tenant, loggedUser, sessionPackageId);
        return pagos.stream().map(PaymentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
