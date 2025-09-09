package br.com.psicologia.context.mercadopagoinfo;

import br.com.psicologia.context.mercadopagoinfo.interfaces.IMercadoPagoInfoContextUser;
import br.com.psicologia.context.sessionpackage.SessionPackageContext;
import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.repository.model.MercadoPagoInfoEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.repository.model.UserEntity;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
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
public class MercadoPagoInfoAdministradorContext implements IMercadoPagoInfoContextUser {

    @Inject
    UserContext userContext;

    @Inject
    SessionPackageContext sessionPackageContext;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Transactional
    public MercadoPagoInfoEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        dao.defineSchema(tenant);
        UserEntity user = userContext.findById(securityContext, tenant, entity.getUser().getId());
        entity.setUser(user);
        entity.setId(user.getId());
        em.persist(entity);
        return entity;
    }

    @Transactional
    public MercadoPagoInfoEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public List<MercadoPagoInfoEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        consultaPagamento();
        return dao.filteredFindPaged(tenant, filter, page, size, MercadoPagoInfoEntity.class);
    }

    @Transactional
    public MercadoPagoInfoEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = dao.findById(tenant, id, MercadoPagoInfoEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }

        return original;
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = dao.findById(tenant, id, MercadoPagoInfoEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Configuração do Mercado Livre não encontrada.");
        }

        UserEntity user = dao.findById(tenant, id, UserEntity.class);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }

        if (user.getMercadoPagoInfo() != null) {
            user.setMercadoPagoInfo(null);
            dao.update(tenant, user);
        }

        dao.delete(tenant, id, MercadoPagoInfoEntity.class);
    }

    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        return dao.countFiltered(tenant, filter, MercadoPagoInfoEntity.class);
    }

    @Transactional
    public String createPaymentPreference(SecurityContext context, String tenant, UUID packageId) {
        SessionPackageEntity sessionPackage = sessionPackageContext.findById(context, tenant, packageId);
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

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success("http://localhost:3000/barueri/usuarios")
                .failure("https://seusite.com/falha")
                .pending("https://seusite.com/pendente")
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
            return preference.getInitPoint();
        }
        catch (MPException | MPApiException e) {
            System.out.println("OKS");
            throw new RuntimeException(e);
        }
    }

    //public Payment consultaPagamento(Long collectionId, String accessToken) {
    public Payment consultaPagamento() {
        MPRequestOptions opts = MPRequestOptions.builder()
                .accessToken("APP_USR-5738332708210583-071723-d90d99f94b63bc6465edd3a0820701b7-2500754715")
                .build();

        PaymentClient client = new PaymentClient();
        try {
            Payment payment = client.get(122583688225L, opts);
            return payment;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar pagamento: " + e.getMessage(), e);
        }
    }

}
