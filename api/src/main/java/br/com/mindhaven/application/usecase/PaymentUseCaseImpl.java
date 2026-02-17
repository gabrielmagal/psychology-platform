package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.PaymentUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.repository.PaymentRepository;
import br.com.mindhaven.adapter.controller.dto.MakePaymentDto;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.MercadoPagoInfoEntity;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentUseCaseImpl implements PaymentUseCase {
    private final PaymentRepository paymentRepository;

    public PaymentUseCaseImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentEntity save(String tenant, UserEntity loggedUser, PaymentEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> paymentRepository.save(tenant, entity);
            case PSICOLOGO -> {
                if (entity.getSessionPackage() != null && entity.getSessionPackage().getPsychologistId() != null && entity.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield paymentRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Psicólogo só pode salvar pagamentos dos próprios pacotes.");
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
            case PSICOLOGO -> {
                if (payment.getSessionPackage() != null && payment.getSessionPackage().getPsychologistId() != null && payment.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield payment;
                } else {
                    throw new SecurityException("Psicólogo só pode acessar pagamentos dos próprios pacotes.");
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
            case PSICOLOGO -> {
                if (payment.getSessionPackage() != null && payment.getSessionPackage().getPsychologistId() != null && payment.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    paymentRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Psicólogo só pode deletar pagamentos dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode deletar pagamentos.");
        }
    }

    @Override
    public List<PaymentEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PSICOLOGO) {
            if (filter.getFilterParams() == null) {
                filter.setFilterParams(new ArrayList<>());
            }
            filter.getFilterParams().add(new FilterParam("sessionPackage.psychologistId", loggedUser.getId().toString()));
        }
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PACIENTE) {
            throw new SecurityException("Paciente não pode listar pagamentos.");
        }
        return paymentRepository.filteredFindPaged(tenant, filter, page, size);
    }

    @Override
    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        if (loggedUser.getUserType() == br.com.mindhaven.domain.entity.enums.UserType.PSICOLOGO) {
            if (filter.getFilterParams() == null) {
                filter.setFilterParams(new ArrayList<>());
            }
            filter.getFilterParams().add(new FilterParam("sessionPackage.psychologistId", loggedUser.getId().toString()));
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
        return filteredFindPaged(tenant, loggedUser, filter, 0, Integer.MAX_VALUE);
    }

    @Override
    public BigDecimal sumPaidAmountBySessionPackage(String tenant, UserEntity loggedUser, UUID sessionPackageId) {
        List<PaymentEntity> pagos = findPaidBySessionPackage(tenant, loggedUser, sessionPackageId);
        return pagos.stream().map(PaymentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void makePayment(String tenant, UserEntity loggedUser, MakePaymentDto makePaymentDto) {
        PaymentEntity payment = paymentRepository.findByPaymentId(tenant, makePaymentDto.getPreferenceId());
        if (payment == null) {
            throw new NotFoundException("Pagamento pendente não encontrado com o ID: " + makePaymentDto.getPreferenceId());
        }
        SessionPackageEntity sessionPackage = payment.getSessionPackage();
        if (sessionPackage == null || sessionPackage.getPsychologistId() == null) {
            throw new IllegalStateException("Pacote de sessão ou psicólogo não encontrado para o pagamento.");
        }
        MercadoPagoInfoEntity mpConfig = paymentRepository.findMercadoPagoInfoByPsychologistId(tenant, sessionPackage.getPsychologistId());
        if (mpConfig == null || mpConfig.getAccessToken() == null) {
            throw new IllegalStateException("Configuração Mercado Pago não encontrada (access token faltando).");
        }
        try {
            PaymentClient paymentClient = new PaymentClient();
            com.mercadopago.resources.payment.Payment mpPayment = paymentClient.get(makePaymentDto.getPaymentId());
            boolean paid = true;
            java.time.LocalDate paymentDate = mpPayment.getDateApproved() != null ? mpPayment.getDateApproved().toLocalDate() : null;
            paymentRepository.updatePaidStatus(tenant, makePaymentDto.getPreferenceId(), paid, paymentDate);
        } catch (MPApiException | MPException e) {
            throw new RuntimeException("Erro ao consultar pagamento Mercado Pago", e);
        }
    }
}
