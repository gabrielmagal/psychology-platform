package br.com.mindhaven.payment;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

/**
 * Asaas, API v3 (https://docs.asaas.com).
 *
 * <p>Fluxo usado: garante o cliente (busca por CPF, cria se não houver), abre uma cobrança
 * com {@code billingType = UNDEFINED} — o pagador escolhe Pix, boleto ou cartão na fatura do
 * próprio Asaas — e consulta a situação da cobrança para dar baixa.
 */
@ApplicationScoped
public class AsaasGateway implements PaymentGateway {
    private static final Logger LOG = Logger.getLogger(AsaasGateway.class);

    // CONFIRMED: pago, saldo ainda não liberado. RECEIVED: pago e disponível.
    // RECEIVED_IN_CASH: o recebedor confirmou no Asaas que recebeu em dinheiro.
    private static final Set<String> PAID = Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");

    @ConfigProperty(name = "asaas.sandbox-url", defaultValue = "https://api-sandbox.asaas.com/v3")
    String sandboxUrl;

    @ConfigProperty(name = "asaas.production-url", defaultValue = "https://api.asaas.com/v3")
    String productionUrl;

    @ConfigProperty(name = "asaas.user-agent", defaultValue = "MindHaven/1.0")
    String userAgent;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String name() {
        return "Asaas";
    }

    @Override
    public void checkCredentials(Credentials credentials) {
        call(credentials, "GET", "/customers?limit=1", null);
    }

    @Override
    public String ensureCustomer(Credentials credentials, Payer payer) {
        String cpf = digits(payer.cpf());
        if (cpf.isEmpty()) {
            throw new GatewayException("O pagador precisa ter CPF cadastrado para gerar a cobrança.");
        }
        // O Asaas aceita clientes duplicados; por isso a busca antes de criar.
        JsonObject found = call(credentials, "GET", "/customers?cpfCnpj=" + encode(cpf) + "&limit=1", null);
        JsonArray data = found.getJsonArray("data");
        if (data != null && !data.isEmpty()) {
            return data.getJsonObject(0).getString("id");
        }
        JsonObject body = new JsonObject().put("name", payer.name()).put("cpfCnpj", cpf);
        if (payer.email() != null && !payer.email().isBlank()) body.put("email", payer.email());
        String phone = digits(payer.phone());
        if (phone.length() >= 10) body.put("mobilePhone", phone);
        return call(credentials, "POST", "/customers", body).getString("id");
    }

    @Override
    public Charge createCharge(Credentials credentials, String customerId, BigDecimal value, LocalDate dueDate,
                               String description, String externalReference, String successUrl) {
        JsonObject body = new JsonObject()
                .put("customer", customerId)
                .put("billingType", "UNDEFINED")
                .put("value", value)
                .put("dueDate", dueDate.toString())
                .put("description", description)
                .put("externalReference", externalReference);
        if (successUrl != null) {
            body.put("callback", new JsonObject().put("successUrl", successUrl).put("autoRedirect", true));
        }
        return toCharge(call(credentials, "POST", "/payments", body));
    }

    @Override
    public Charge getCharge(Credentials credentials, String chargeId) {
        return toCharge(call(credentials, "GET", "/payments/" + encode(chargeId), null));
    }

    @Override
    public boolean isPaid(String status) {
        return status != null && PAID.contains(status);
    }

    private Charge toCharge(JsonObject json) {
        String paidOn = json.getString("clientPaymentDate", json.getString("paymentDate"));
        Number value = json.getNumber("value");
        return new Charge(
                json.getString("id"),
                json.getString("invoiceUrl"),
                json.getString("status"),
                value == null ? null : new BigDecimal(value.toString()),
                json.getString("billingType"),
                paidOn == null ? null : LocalDate.parse(paidOn.substring(0, 10)));
    }

    private JsonObject call(Credentials credentials, String method, String path, JsonObject body) {
        String base = (credentials.sandbox() ? sandboxUrl : productionUrl).replaceAll("/+$", "");
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(20))
                .header("access_token", credentials.apiKey())
                .header("User-Agent", userAgent)
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body.encode()));
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("A comunicação com o Asaas foi interrompida. Tente novamente.", e);
        } catch (Exception e) {
            throw new GatewayException("Não foi possível falar com o Asaas agora. Tente novamente em instantes.", e);
        }

        int status = response.statusCode();
        if (status / 100 == 2) {
            return response.body() == null || response.body().isBlank() ? new JsonObject() : new JsonObject(response.body());
        }
        if (status == 401) {
            throw new GatewayException("O Asaas recusou a chave de API. Confira se a chave está correta e se o "
                    + "ambiente (testes ou produção) é o mesmo em que ela foi gerada.");
        }
        String reason = firstError(response.body());
        // Sem a chave e sem o corpo enviado: só o que o Asaas respondeu.
        LOG.warnf("Asaas respondeu %d em %s %s. %s", status, method, path.replaceAll("\\?.*", ""), reason);
        throw new GatewayException(reason.isEmpty()
                ? "O Asaas não aceitou a operação (código " + status + ")."
                : "O Asaas não aceitou a operação. " + reason);
    }

    /** Corpo de erro do Asaas: {"errors":[{"code":"...","description":"..."}]}. */
    private static String firstError(String body) {
        try {
            JsonArray errors = new JsonObject(body).getJsonArray("errors");
            if (errors != null && !errors.isEmpty()) {
                return errors.getJsonObject(0).getString("description", "");
            }
        } catch (Exception ignored) {
            // corpo que não é JSON: cai na mensagem genérica
        }
        return "";
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
