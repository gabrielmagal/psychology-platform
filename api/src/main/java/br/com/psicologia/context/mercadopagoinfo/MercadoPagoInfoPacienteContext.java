package br.com.psicologia.context.mercadopagoinfo;

import br.com.psicologia.context.mercadopagoinfo.interfaces.IMercadoPagoInfoContextUser;
import br.com.psicologia.context.payment.PaymentContext;
import br.com.psicologia.repository.enums.PaymentMethod;
import br.com.psicologia.repository.model.MercadoPagoInfoEntity;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import core.repository.dao.GenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MercadoPagoInfoPacienteContext implements IMercadoPagoInfoContextUser {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    @Inject
    PaymentContext paymentContext;

    private final String MSG_UNAUTHORIZED = "Sem permissão para executar essa ação.";

    @Transactional
    public MercadoPagoInfoEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public MercadoPagoInfoEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public List<MercadoPagoInfoEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public MercadoPagoInfoEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Override
    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public String createPaymentPreference(SecurityContext context, String tenant, UUID packageId) {
        SessionPackageEntity sessionPackage = dao.findById(tenant, packageId, SessionPackageEntity.class);
        if (sessionPackage == null) {
            throw new NotFoundException("Pacote não encontrado");
        }

        MercadoPagoInfoEntity original = dao.findById(tenant, sessionPackage.getPsychologistId(), MercadoPagoInfoEntity.class);
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
            paymentContext.save(context, tenant, payment);

            return preference.getInitPoint();
        }
        catch (MPException | MPApiException e) {
            System.out.println("OKS");
            throw new RuntimeException(e);
        }
    }
}
