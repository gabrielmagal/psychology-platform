package br.com.psicologia.application.usecase;

import br.com.psicologia.application.usecase.interfaces.MercadoPagoInfoUseCase;
import br.com.psicologia.domain.entity.*;
import br.com.psicologia.domain.entity.enums.UserType;
import br.com.psicologia.domain.repository.MercadoPagoInfoRepository;
import br.com.psicologia.domain.repository.UserRepository;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;
import br.com.psicologia.domain.entity.PaymentEntity;
import br.com.psicologia.domain.entity.SessionPackageEntity;
import br.com.psicologia.domain.entity.enums.PaymentMethod;
import br.com.psicologia.application.usecase.interfaces.PaymentUseCase;
import br.com.psicologia.application.usecase.interfaces.SessionPackageUseCase;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MercadoPagoInfoUseCaseImpl implements MercadoPagoInfoUseCase {
    private final MercadoPagoInfoRepository mercadoPagoInfoRepository;
    private final UserRepository userRepository;
    private final PaymentUseCase paymentUseCase;
    private final SessionPackageUseCase sessionPackageUseCase;

    public MercadoPagoInfoUseCaseImpl(MercadoPagoInfoRepository mercadoPagoInfoRepository, UserRepository userRepository, PaymentUseCase paymentUseCase, SessionPackageUseCase sessionPackageUseCase) {
        this.mercadoPagoInfoRepository = mercadoPagoInfoRepository;
        this.userRepository = userRepository;
        this.paymentUseCase = paymentUseCase;
        this.sessionPackageUseCase = sessionPackageUseCase;
    }

    private final String MSG_UNAUTHORIZED = "Sem permissão para executar essa ação.";

    @Override
    public MercadoPagoInfoEntity save(String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            UserEntity user = userRepository.findById(tenant, entity.getUser().getId());
            entity.setUser(user);
            entity.setId(user.getId());
            return mercadoPagoInfoRepository.save(tenant, entity);
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            UserEntity user = userRepository.findById(tenant, entity.getUser().getId());
            if (user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
                entity.setUser(user);
                entity.setId(user.getId());
                return mercadoPagoInfoRepository.save(tenant, entity);
            }
            throw new SecurityException(MSG_UNAUTHORIZED);
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Override
    public MercadoPagoInfoEntity update(String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return mercadoPagoInfoRepository.update(tenant, entity);
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            UserEntity user = userRepository.findById(tenant, entity.getUser().getId());
            if (user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
                MercadoPagoInfoEntity original = mercadoPagoInfoRepository.findById(tenant, entity.getId());
                original.setAccessToken(entity.getAccessToken());
                original.setRefreshToken(entity.getRefreshToken());
                original.setScope(entity.getScope());
                original.setExpiresIn(entity.getExpiresIn());
                original.setTokenCreatedAt(entity.getTokenCreatedAt());
                return mercadoPagoInfoRepository.update(tenant, original);
            }
            throw new SecurityException(MSG_UNAUTHORIZED);
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Override
    public MercadoPagoInfoEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = mercadoPagoInfoRepository.findById(tenant, id);
        if (original == null) {
            throw new IllegalArgumentException("Informações do mercado pago não encontradas.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return original;
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (original.getUser().getKeycloakId().equals(loggedUser.getKeycloakId())) {
                return original;
            }
            throw new SecurityException(MSG_UNAUTHORIZED);
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Override
    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = mercadoPagoInfoRepository.findById(tenant, id);
        if (original == null) {
            throw new IllegalArgumentException("Configuração do Mercado Livre não encontrada.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            UserEntity user = userRepository.findById(tenant, id);
            if (user == null) {
                throw new IllegalArgumentException("Usuário não encontrado.");
            }
            if (user.getMercadoPagoInfo() != null) {
                user.setMercadoPagoInfo(null);
                userRepository.update(tenant, user);
            }
            mercadoPagoInfoRepository.delete(tenant, id);
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (!original.getUser().getKeycloakId().equals(loggedUser.getKeycloakId())) {
                throw new SecurityException(MSG_UNAUTHORIZED);
            }
            UserEntity user = userRepository.findById(tenant, id);
            if (user == null) {
                throw new IllegalArgumentException("Usuário não encontrado.");
            }
            if (user.getMercadoPagoInfo() != null) {
                user.setMercadoPagoInfo(null);
                userRepository.update(tenant, user);
            }
            mercadoPagoInfoRepository.delete(tenant, id);
        } else {
            throw new SecurityException(MSG_UNAUTHORIZED);
        }
    }

    public String createPaymentPreference(String tenant, UserEntity loggedUser, UUID packageId) {
        SessionPackageEntity sessionPackage = sessionPackageUseCase.findById(tenant, loggedUser, packageId);
        if (sessionPackage == null) {
            throw new NotFoundException("Pacote não encontrado");
        }
        MercadoPagoInfoEntity original = mercadoPagoInfoRepository.findById(tenant, sessionPackage.getPsychologistId());
        if (original == null) {
            throw new IllegalArgumentException("Configuração do Mercado Pago não encontrada.");
        }
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title("Pacote de Sessões")
                .quantity(1)
                .unitPrice(new BigDecimal("150.00"))
                .build();
        String callbackBase = "https://d6030e5ffd2d.ngrok-free.app/" + tenant + "/retorno-pagamento";
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(callbackBase)
                .failure(callbackBase + "?status=failure")
                .pending(callbackBase + "?status=pending")
                .build();
        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                .autoReturn("approved")
                .build();
        PreferenceClient client = new PreferenceClient();
        try {
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(original.getAccessToken())
                    .build();
            Preference preference = client.create(request, requestOptions);
            PaymentEntity payment = new PaymentEntity();
            payment.setAmount(new BigDecimal("150.00"));
            payment.setSessionPackage(sessionPackage);
            payment.setPaymentId(preference.getId());
            payment.setPaymentMethod(PaymentMethod.OTHER);
            paymentUseCase.save(tenant, loggedUser, payment);
            return preference.getInitPoint();
        } catch (MPException | MPApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MercadoPagoInfoEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return mercadoPagoInfoRepository.filteredFindPaged(tenant, filter, page, size);
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            List<MercadoPagoInfoEntity> all = mercadoPagoInfoRepository.filteredFindPaged(tenant, filter, page, size);
            return all.stream()
                .filter(info -> info.getUser() != null &&
                    (loggedUser.getKeycloakId().equals(info.getUser().getKeycloakId()) ||
                     loggedUser.getKeycloakId().equals(info.getUser().getRegisteredByKeycloakId())))
                .toList();
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Override
    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return mercadoPagoInfoRepository.countFiltered(tenant, filter);
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            List<MercadoPagoInfoEntity> all = mercadoPagoInfoRepository.filteredFindPaged(tenant, filter, 0, Integer.MAX_VALUE);
            return all.stream()
                .filter(info -> info.getUser() != null &&
                    (loggedUser.getKeycloakId().equals(info.getUser().getKeycloakId()) ||
                     loggedUser.getKeycloakId().equals(info.getUser().getRegisteredByKeycloakId())))
                .count();
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }
}
