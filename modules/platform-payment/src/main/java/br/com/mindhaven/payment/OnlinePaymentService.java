package br.com.mindhaven.payment;

import core.user.BaseUserEntity;
import br.com.mindhaven.payment.PayableSource.ChargePaid;
import br.com.mindhaven.payment.PayableSource.Payable;
import br.com.mindhaven.payment.PaymentGateway.Charge;
import br.com.mindhaven.payment.PaymentGateway.Credentials;
import br.com.mindhaven.payment.PaymentGateway.GatewayException;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pagamento online.
 *
 * <p>Módulo reaproveitável: não conhece o domínio do sistema. O que está sendo pago vem de
 * {@link PayableSource}; o provedor fica atrás de {@link PaymentGateway}; e a confirmação
 * sai como evento {@link ChargePaid}, para a aplicação registrar o pagamento como quiser.
 *
 * <p>Regras que valem para qualquer sistema: cada recebedor conecta a própria conta; a
 * cobrança é sempre do saldo em aberto; e a baixa só acontece quando o provedor confirma —
 * consultado por nós, nunca informado pelo navegador.
 */
@ApplicationScoped
@Transactional
public class OnlinePaymentService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PaymentGateway gateway;

    @Inject
    PayableSource payables;

    @Inject
    Event<ChargePaid> chargePaid;

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:3000")
    String frontendUrl;

    @ConfigProperty(name = "app.api-url", defaultValue = "http://localhost:8080/api")
    String apiUrl;

    /** Dias que o pagador tem para pagar a cobrança antes de ela vencer. */
    @ConfigProperty(name = "asaas.due-days", defaultValue = "3")
    int dueDays;

    // ------------------------------------------------------- conta do recebedor

    public Map<String, Object> account(String tenant, BaseUserEntity user) {
        requireReceiver(user);
        dao.defineSchema(tenant);
        AsaasAccountEntity account = findAccount(user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", gateway.name());
        result.put("configured", account != null);
        if (account != null) {
            String key = account.getApiKey();
            // A chave nunca volta inteira: só o final, para o recebedor reconhecê-la.
            result.put("apiKeyHint", "••••" + key.substring(Math.max(0, key.length() - 4)));
            result.put("sandbox", account.isSandbox());
            result.put("webhookUrl", apiUrl.replaceAll("/+$", "") + "/online-payment/webhook/" + tenant);
            result.put("webhookToken", account.getWebhookToken());
            result.put("updatedAt", account.getUpdatedAt() == null ? null : account.getUpdatedAt().toString());
        }
        return result;
    }

    public void saveAccount(String tenant, BaseUserEntity user, String apiKey, boolean sandbox) {
        requireReceiver(user);
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.invalid("Informe a chave de API do " + gateway.name() + ".");
        }
        try {
            gateway.checkCredentials(new Credentials(apiKey.trim(), sandbox));
        } catch (GatewayException e) {
            throw BusinessException.invalid(e.getMessage());
        }
        dao.defineSchema(tenant);
        AsaasAccountEntity account = findAccount(user.getId());
        if (account == null) {
            account = new AsaasAccountEntity();
            account.setOwnerId(user.getId());
            account.setWebhookToken(newToken());
        }
        account.setApiKey(apiKey.trim());
        account.setSandbox(sandbox);
        account.setUpdatedAt(LocalDateTime.now());
        em.merge(account);
    }

    public void removeAccount(String tenant, BaseUserEntity user) {
        requireReceiver(user);
        dao.defineSchema(tenant);
        AsaasAccountEntity account = findAccount(user.getId());
        if (account != null) em.remove(account);
    }

    // ----------------------------------------------------------------- cobrança

    /** Abre (ou reaproveita) a cobrança do saldo em aberto e devolve o link da fatura. */
    public Map<String, Object> charge(String tenant, BaseUserEntity user, UUID payableId) {
        dao.defineSchema(tenant);
        Payable payable = require(payables.load(tenant, user, payableId));
        if (payable.amountDue() == null) {
            throw BusinessException.invalid(
                    "O valor ainda não foi definido. Peça ao profissional para informar o valor antes de cobrar.");
        }
        AsaasAccountEntity account = findAccount(payable.receiverId());
        if (account == null) {
            throw BusinessException.conflict("O profissional ainda não ativou o pagamento online. "
                    + "Combine o pagamento diretamente com ele.");
        }
        Credentials credentials = new Credentials(account.getApiKey(), account.isSandbox());

        // Uma cobrança anterior pode ter sido paga sem o pagador voltar para cá.
        if (syncPending(tenant, credentials, pendingCharges(payableId)) > 0) {
            payable = require(payables.load(tenant, user, payableId));
        }
        BigDecimal balance = payable.amountDue();
        if (balance.signum() <= 0) {
            throw BusinessException.conflict("Não há saldo em aberto: já está tudo quitado.");
        }

        OnlineChargeEntity open = pendingCharges(payableId).stream()
                .filter(c -> c.getInvoiceUrl() != null && c.getAmount().compareTo(balance) == 0)
                .findFirst().orElse(null);
        if (open == null) {
            Charge created;
            try {
                String customerId = gateway.ensureCustomer(credentials, payable.payer());
                created = gateway.createCharge(credentials, customerId, balance, LocalDate.now().plusDays(dueDays),
                        payable.description(), payable.id().toString(), successUrl(tenant, payable.id()));
            } catch (GatewayException e) {
                throw new BusinessException(502, e.getMessage());
            }
            open = new OnlineChargeEntity();
            open.setPayableId(payable.id());
            open.setReceiverId(payable.receiverId());
            open.setGatewayChargeId(created.id());
            open.setInvoiceUrl(created.invoiceUrl());
            open.setAmount(balance);
            open.setCreatedAt(LocalDateTime.now());
            em.persist(open);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceUrl", open.getInvoiceUrl());
        result.put("amount", open.getAmount());
        result.put("sandbox", account.isSandbox());
        return result;
    }

    /** Consulta no provedor as cobranças em aberto e dá baixa nas que foram pagas. */
    public Map<String, Object> sync(String tenant, BaseUserEntity user, UUID payableId) {
        dao.defineSchema(tenant);
        Payable payable = require(payables.load(tenant, user, payableId));
        AsaasAccountEntity account = findAccount(payable.receiverId());
        int confirmed = 0;
        if (account != null) {
            confirmed = syncPending(tenant, new Credentials(account.getApiKey(), account.isSandbox()),
                    pendingCharges(payableId));
            if (confirmed > 0) payable = require(payables.load(tenant, user, payableId));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmedNow", confirmed);
        result.put("paid", em.createQuery("select coalesce(sum(c.amount), 0) from OnlineChargeEntity c "
                        + "where c.payableId = :p and c.status = :s", BigDecimal.class)
                .setParameter("p", payableId).setParameter("s", OnlineChargeEntity.PAID).getSingleResult());
        result.put("balance", payable.amountDue() == null ? null : payable.amountDue().max(BigDecimal.ZERO));
        result.put("pending", pendingCharges(payableId).size());
        return result;
    }

    /**
     * Aviso do provedor de que algo mudou numa cobrança. O conteúdo do aviso não é usado
     * para dar baixa: ele só diz <em>qual</em> cobrança consultar. A baixa vem da consulta.
     *
     * @return false quando o segredo do aviso não confere
     */
    public boolean webhook(String tenant, String token, String gatewayChargeId) {
        dao.defineSchema(tenant);
        if (gatewayChargeId == null || gatewayChargeId.isBlank()) return true;
        OnlineChargeEntity charge = em.createQuery(
                        "select c from OnlineChargeEntity c where c.gatewayChargeId = :id", OnlineChargeEntity.class)
                .setParameter("id", gatewayChargeId).setMaxResults(1).getResultStream().findFirst().orElse(null);
        // Cobrança que não é nossa (criada direto no painel do provedor): nada a fazer.
        if (charge == null) return true;

        AsaasAccountEntity account = findAccount(charge.getReceiverId());
        if (account == null || !sameToken(account.getWebhookToken(), token)) return false;
        if (OnlineChargeEntity.PENDING.equals(charge.getStatus())) {
            syncPending(tenant, new Credentials(account.getApiKey(), account.isSandbox()), List.of(charge));
        }
        return true;
    }

    // --------------------------------------------------------------- auxiliares

    private int syncPending(String tenant, Credentials credentials, List<OnlineChargeEntity> pending) {
        int confirmed = 0;
        for (OnlineChargeEntity charge : pending) {
            Charge remote;
            try {
                remote = gateway.getCharge(credentials, charge.getGatewayChargeId());
            } catch (GatewayException e) {
                // Uma cobrança que não pôde ser consultada agora não impede as outras.
                continue;
            }
            if (!gateway.isPaid(remote.status())) continue;
            // A baixa é do valor que o provedor diz que foi cobrado.
            if (remote.value() != null) charge.setAmount(remote.value());
            charge.setStatus(OnlineChargeEntity.PAID);
            charge.setBillingType(remote.billingType());
            charge.setPaidOn(remote.paymentDate() == null ? LocalDate.now() : remote.paymentDate());
            em.flush();
            chargePaid.fire(new ChargePaid(tenant, charge.getPayableId(), charge.getId(), charge.getAmount(),
                    charge.getPaidOn(), charge.getBillingType()));
            confirmed++;
        }
        if (confirmed > 0) em.flush();
        return confirmed;
    }

    private List<OnlineChargeEntity> pendingCharges(UUID payableId) {
        return em.createQuery("select c from OnlineChargeEntity c where c.payableId = :p and c.status = :s "
                        + "order by c.createdAt", OnlineChargeEntity.class)
                .setParameter("p", payableId).setParameter("s", OnlineChargeEntity.PENDING).getResultList();
    }

    private static Payable require(Payable payable) {
        if (payable == null) throw BusinessException.invalid("Não encontramos o que você quer pagar.");
        return payable;
    }

    private AsaasAccountEntity findAccount(UUID ownerId) {
        if (ownerId == null) return null;
        return em.createQuery("select a from AsaasAccountEntity a where a.ownerId = :owner", AsaasAccountEntity.class)
                .setParameter("owner", ownerId).getResultStream().findFirst().orElse(null);
    }

    /** O Asaas só redireciona para um endereço do domínio cadastrado na conta, e em https. */
    private String successUrl(String tenant, UUID payableId) {
        String base = frontendUrl.replaceAll("/+$", "");
        return base.startsWith("https://") ? base + "/" + tenant + "/retorno-pagamento?pacote=" + payableId : null;
    }

    /** Só quem recebe configura a conta; quem é, o sistema diz em {@link PayableSource#canReceive}. */
    private void requireReceiver(BaseUserEntity user) {
        if (!payables.canReceive(user)) {
            throw new SecurityException("Só quem recebe configura o recebimento online.");
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Comparação em tempo constante, para o segredo não vazar pelo tempo de resposta. */
    private static boolean sameToken(String expected, String received) {
        if (expected == null || received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }
}
