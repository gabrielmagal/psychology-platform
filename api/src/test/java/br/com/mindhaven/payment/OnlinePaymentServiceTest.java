package br.com.mindhaven.payment;

import core.error.BusinessException;
import br.com.mindhaven.application.service.ClinicService;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pagamento online: conta do profissional, cobrança do saldo, baixa e aviso do provedor. */
@QuarkusTest
class OnlinePaymentServiceTest {
    @Inject
    GenericDao dao;

    @Inject
    OnlinePaymentService payments;

    @Inject
    ClinicService clinic;

    @Inject
    FakeGateway gateway;

    private UserEntity newUser(String tenant, String cpf, String name, UserType type) {
        return dao.update(tenant, UserEntity.builder()
                .cpf(cpf).firstName(name).lastName("Teste").email(name.toLowerCase() + "@exemplo.com")
                .phoneNumber("11" + cpf).birthDate(LocalDate.of(1990, 1, 1))
                .keycloakId(UUID.randomUUID().toString()).userType(type).build());
    }

    private SessionPackageEntity newPackage(String tenant, UserEntity professional, UserEntity patient, String price) {
        SessionPackageEntity pkg = new SessionPackageEntity();
        pkg.setProfessionalId(professional.getId());
        pkg.setPatient(patient);
        pkg.setPackageTitle("Pacote mensal");
        pkg.setPrice(price == null ? null : new BigDecimal(price));
        return dao.update(tenant, pkg);
    }

    private static String chargeIdOf(Map<String, Object> charge) {
        String url = (String) charge.get("invoiceUrl");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("conta: chave validada no provedor, nunca devolvida inteira, só do profissional")
    void account() {
        String tenant = "pag_conta";
        UserEntity professional = newUser(tenant, "50000000001", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "50000000002", "Paciente", UserType.PACIENTE);

        assertEquals(Boolean.FALSE, payments.account(tenant, professional).get("configured"));
        assertThrows(BusinessException.class, () -> payments.saveAccount(tenant, professional, "chave-errada", true));
        assertThrows(SecurityException.class, () -> payments.saveAccount(tenant, patient, FakeGateway.VALID_KEY, true));

        payments.saveAccount(tenant, professional, FakeGateway.VALID_KEY, true);
        Map<String, Object> account = payments.account(tenant, professional);
        assertEquals(Boolean.TRUE, account.get("configured"));
        assertEquals("••••1234", account.get("apiKeyHint"));
        assertFalse(account.containsValue(FakeGateway.VALID_KEY), "a chave não volta para o navegador");
        assertTrue(((String) account.get("webhookUrl")).endsWith("/online-payment/webhook/" + tenant));

        // Trocar a chave mantém o segredo do webhook já cadastrado no provedor.
        Object token = account.get("webhookToken");
        payments.saveAccount(tenant, professional, FakeGateway.VALID_KEY, false);
        assertEquals(token, payments.account(tenant, professional).get("webhookToken"));
        assertEquals(Boolean.FALSE, payments.account(tenant, professional).get("sandbox"));

        payments.removeAccount(tenant, professional);
        assertEquals(Boolean.FALSE, payments.account(tenant, professional).get("configured"));
    }

    @Test
    @DisplayName("cobrança: saldo do pacote, reaproveita a fatura aberta e dá baixa só quando o provedor confirma")
    void chargeAndSync() {
        String tenant = "pag_cobranca";
        UserEntity professional = newUser(tenant, "50000000011", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "50000000012", "Paciente", UserType.PACIENTE);
        UserEntity stranger = newUser(tenant, "50000000013", "Outro", UserType.PACIENTE);
        SessionPackageEntity pkg = newPackage(tenant, professional, patient, "400.00");
        SessionPackageEntity noPrice = newPackage(tenant, professional, patient, null);

        // Sem conta conectada o paciente recebe uma orientação, não um erro técnico.
        BusinessException notReady = assertThrows(BusinessException.class, () -> payments.charge(tenant, patient, pkg.getId()));
        assertEquals(409, notReady.getStatus());
        payments.saveAccount(tenant, professional, FakeGateway.VALID_KEY, true);

        assertThrows(SecurityException.class, () -> payments.charge(tenant, stranger, pkg.getId()));
        assertEquals(400, assertThrows(BusinessException.class,
                () -> payments.charge(tenant, patient, noPrice.getId())).getStatus());

        int before = gateway.createdCount();
        Map<String, Object> charge = payments.charge(tenant, patient, pkg.getId());
        assertNotNull(charge.get("invoiceUrl"));
        assertEquals(0, new BigDecimal("400.00").compareTo((BigDecimal) charge.get("amount")));

        // Clicar de novo em "pagar" devolve a mesma fatura, sem abrir outra cobrança.
        assertEquals(charge.get("invoiceUrl"), payments.charge(tenant, patient, pkg.getId()).get("invoiceUrl"));
        assertEquals(before + 1, gateway.createdCount());

        Map<String, Object> pending = payments.sync(tenant, patient, pkg.getId());
        assertEquals(0, pending.get("confirmedNow"));
        assertEquals(1, pending.get("pending"));
        assertTrue(((List<?>) clinic.finance(tenant, professional, null, null).get("debtors")).size() == 1,
                "cobrança aberta não conta como paga");

        gateway.pay(chargeIdOf(charge));
        Map<String, Object> done = payments.sync(tenant, patient, pkg.getId());
        assertEquals(1, done.get("confirmedNow"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) done.get("balance")));
        assertTrue(((List<?>) clinic.finance(tenant, professional, null, null).get("debtors")).isEmpty());

        assertEquals(409, assertThrows(BusinessException.class,
                () -> payments.charge(tenant, patient, pkg.getId())).getStatus());
    }

    @Test
    @DisplayName("webhook: só vale com o segredo da conta e dá baixa consultando o provedor")
    void webhook() {
        String tenant = "pag_webhook";
        UserEntity professional = newUser(tenant, "50000000021", "Dona", UserType.PROFISSIONAL);
        UserEntity patient = newUser(tenant, "50000000022", "Paciente", UserType.PACIENTE);
        SessionPackageEntity pkg = newPackage(tenant, professional, patient, "250.00");
        payments.saveAccount(tenant, professional, FakeGateway.VALID_KEY, true);
        String token = (String) payments.account(tenant, professional).get("webhookToken");
        String chargeId = chargeIdOf(payments.charge(tenant, patient, pkg.getId()));

        assertFalse(payments.webhook(tenant, "segredo-errado", chargeId));
        assertFalse(payments.webhook(tenant, null, chargeId));

        // Aviso legítimo, mas a cobrança ainda não foi paga: nada muda.
        assertTrue(payments.webhook(tenant, token, chargeId));
        assertEquals(1, payments.sync(tenant, professional, pkg.getId()).get("pending"));

        gateway.pay(chargeId);
        assertTrue(payments.webhook(tenant, token, chargeId));
        Map<String, Object> state = payments.sync(tenant, professional, pkg.getId());
        assertEquals(0, state.get("pending"));
        assertEquals(0, new BigDecimal("250.00").compareTo((BigDecimal) state.get("paid")));

        // Cobrança desconhecida (criada direto no painel do provedor) é só ignorada.
        assertTrue(payments.webhook(tenant, "qualquer", "pay_inexistente"));
    }
}
