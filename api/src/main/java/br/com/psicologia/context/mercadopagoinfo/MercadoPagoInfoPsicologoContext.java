package br.com.psicologia.context.mercadopagoinfo;

import br.com.psicologia.context.mercadopagoinfo.interfaces.IMercadoPagoInfoContextUser;
import br.com.psicologia.context.payment.PaymentContext;
import br.com.psicologia.context.sessionpackage.SessionPackageContext;
import br.com.psicologia.context.user.UserContext;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MercadoPagoInfoPsicologoContext implements IMercadoPagoInfoContextUser {

    @Inject
    EntityManager em;

    @Inject
    GenericDao dao;

    @Inject
    UserContext userContext;

    @Inject
    SessionPackageContext sessionPackageContext;

    @Inject
    PaymentContext paymentContext;

    private final String MSG_UNAUTHORIZED = "Sem permissão para executar essa ação.";

    @Transactional
    public MercadoPagoInfoEntity save(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        dao.defineSchema(tenant);
        UserEntity user = userContext.findById(securityContext, tenant, entity.getUser().getId());
        if (user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
            entity.setUser(user);
            entity.setId(user.getId());
            em.persist(entity);
            return entity;
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public MercadoPagoInfoEntity update(SecurityContext securityContext, String tenant, UserEntity loggedUser, MercadoPagoInfoEntity entity) {
        dao.defineSchema(tenant);
        UserEntity user = userContext.findById(securityContext, tenant, entity.getUser().getId());
        if (user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
            MercadoPagoInfoEntity original = dao.findById(tenant, entity.getId(), MercadoPagoInfoEntity.class);
            original.setAccessToken(entity.getAccessToken());
            original.setRefreshToken(entity.getRefreshToken());
            original.setScope(entity.getScope());
            original.setExpiresIn(entity.getExpiresIn());
            original.setTokenCreatedAt(entity.getTokenCreatedAt());
            return dao.update(tenant, original);
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public List<MercadoPagoInfoEntity> filteredFindPaged(SecurityContext securityContext, String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<MercadoPagoInfoEntity> query = cb.createQuery(MercadoPagoInfoEntity.class);
        Root<MercadoPagoInfoEntity> root = query.from(MercadoPagoInfoEntity.class);
        query.select(root).distinct(true);

        List<Predicate> predicates = new ArrayList<>();

        Predicate registeredBy = cb.equal(root.get("registeredByKeycloakId"), loggedUser.getKeycloakId());
        Predicate self = cb.equal(root.get("keycloakId"), loggedUser.getKeycloakId());
        predicates.add(cb.or(registeredBy, self));


        List<Predicate> filterPredicates = dao.buildPredicatesFromFilter(filter, cb, root, MercadoPagoInfoEntity.class);
        predicates.addAll(filterPredicates);

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public MercadoPagoInfoEntity findById(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = dao.findById(tenant, id, MercadoPagoInfoEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Informações do mercado pago não encontradas.");
        }
        if (original.getUser().getKeycloakId().equals(loggedUser.getKeycloakId())) {
            return original;
        }
        throw new SecurityException(MSG_UNAUTHORIZED);
    }

    @Transactional
    public void delete(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID id) {
        MercadoPagoInfoEntity original = dao.findById(tenant, id, MercadoPagoInfoEntity.class);
        if (original == null) {
            throw new IllegalArgumentException("Configuração do Mercado Livre não encontrada.");
        }
        if (!original.getUser().getKeycloakId().equals(loggedUser.getKeycloakId())) {
            throw new SecurityException(MSG_UNAUTHORIZED);
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

    @Override
    @Transactional
    public long countFiltered(SecurityContext securityContext, String tenant, UserEntity userEntity, Filter filter) {
        CriteriaBuilder cb = dao.getCriteriaBuilder(tenant);
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<MercadoPagoInfoEntity> root = query.from(MercadoPagoInfoEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate registeredBy = cb.equal(root.get("registeredByKeycloakId"), userEntity.getKeycloakId());
        Predicate self = cb.equal(root.get("keycloakId"), userEntity.getKeycloakId());
        predicates.add(cb.or(registeredBy, self));

        List<Predicate> filterPredicates = dao.buildPredicatesFromFilter(filter, cb, root, MercadoPagoInfoEntity.class);
        predicates.addAll(filterPredicates);

        query.select(cb.count(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return em.createQuery(query).getSingleResult();
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
                .success("https://909fb99ad600.ngrok-free.app/" + tenant + "/retorno-pagamento")
                .failure("https://909fb99ad600.ngrok-free.app/" + tenant + "/retorno-pagamento?status=failure")
                .pending("https://909fb99ad600.ngrok-free.app/" + tenant + "/retorno-pagamento?status=pending")
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
