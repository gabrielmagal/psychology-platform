package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.MercadoPagoInfoUseCase;
import br.com.mindhaven.domain.entity.*;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.domain.repository.MercadoPagoInfoRepository;
import br.com.mindhaven.domain.repository.UserRepository;
import br.com.mindhaven.domain.repository.PaymentRepository;
import br.com.mindhaven.application.BusinessException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import core.service.model.Filter;

import java.util.List;
import java.util.UUID;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.application.usecase.interfaces.PaymentUseCase;
import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
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

    private final PaymentRepository paymentRepository;
    private final String frontendUrl;

    public MercadoPagoInfoUseCaseImpl(MercadoPagoInfoRepository mercadoPagoInfoRepository, UserRepository userRepository,
                                      PaymentUseCase paymentUseCase, SessionPackageUseCase sessionPackageUseCase,
                                      PaymentRepository paymentRepository,
                                      @ConfigProperty(name = "app.frontend-url") String frontendUrl) {
        this.paymentRepository = paymentRepository;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
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

    /**
     * Abre um checkout do Mercado Pago para o saldo em aberto do pacote.
     *
     * <p>Quem pode: quem enxerga o pacote — o psicólogo dono, o administrador e o próprio
     * paciente. O valor era fixo em R$ 150,00 e a URL de retorno apontava para um túnel de
     * desenvolvimento; agora o valor é o saldo do pacote e o retorno vem da configuração
     * ({@code app.frontend-url}).
     */
    public String createPaymentPreference(String tenant, UserEntity loggedUser, UUID packageId) {
        SessionPackageEntity sessionPackage = sessionPackageUseCase.findById(tenant, loggedUser, packageId);
        if (sessionPackage == null) {
            throw new NotFoundException("Pacote não encontrado");
        }
        if (sessionPackage.getPrice() == null) {
            throw BusinessException.invalid(
                    "Este pacote não tem valor definido. Peça ao psicólogo para preencher o valor do pacote.");
        }
        BigDecimal balance = sessionPackage.getPrice().subtract(paidSoFar(tenant, packageId));
        if (balance.signum() <= 0) {
            throw BusinessException.conflict("Este pacote já está quitado.");
        }
        MercadoPagoInfoEntity original = mercadoPagoInfoRepository.findById(tenant, sessionPackage.getPsychologistId());
        if (original == null) {
            throw BusinessException.conflict("O psicólogo ainda não conectou uma conta do Mercado Pago.");
        }
        String title = sessionPackage.getPackageTitle() == null || sessionPackage.getPackageTitle().isBlank()
                ? "Pacote de Sessões"
                : sessionPackage.getPackageTitle();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(title)
                .quantity(1)
                .unitPrice(balance)
                .build();
        String callbackBase = frontendUrl + "/" + tenant + "/retorno-pagamento";
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(callbackBase)
                .failure(callbackBase + "?status=failure")
                .pending(callbackBase + "?status=pending")
                .build();
        var requestBuilder = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                // Conferida na confirmação: o pagamento aprovado tem de ser deste pacote.
                .externalReference(sessionPackage.getId().toString());
        // O Mercado Pago recusa o retorno automático quando a URL de volta não é https.
        if (callbackBase.startsWith("https://")) {
            requestBuilder.autoReturn("approved");
        }
        PreferenceClient client = new PreferenceClient();
        try {
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(original.getAccessToken())
                    .build();
            Preference preference = client.create(requestBuilder.build(), requestOptions);
            PaymentEntity payment = new PaymentEntity();
            payment.setAmount(balance);
            payment.setSessionPackage(sessionPackage);
            payment.setPaymentId(preference.getId());
            payment.setPaymentMethod(PaymentMethod.OTHER);
            payment.setObservation("Mercado Pago");
            // Direto no repositório: o acesso ao pacote já foi verificado acima, e o paciente
            // (que não pode lançar pagamentos manualmente) precisa registrar este pendente.
            paymentRepository.save(tenant, payment);
            return preference.getInitPoint();
        } catch (MPException | MPApiException e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal paidSoFar(String tenant, UUID packageId) {
        Filter filter = new Filter();
        filter.setFilterParams(new java.util.ArrayList<>(List.of(
                new core.service.model.FilterParam("sessionPackage.id", packageId.toString()),
                new core.service.model.FilterParam("paid", "true"))));
        return paymentRepository.filteredFindPaged(tenant, filter, 0, Integer.MAX_VALUE).stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
