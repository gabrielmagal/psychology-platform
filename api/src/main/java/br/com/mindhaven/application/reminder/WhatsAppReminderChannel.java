package br.com.mindhaven.application.reminder;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Lembrete pelo WhatsApp usando a API oficial da Meta (WhatsApp Cloud API).
 *
 * <p><b>Estrutura pronta, desligada por padrão</b> ({@code reminder.whatsapp.enabled=false})
 * e ainda não exercitada contra a API real. Mensagem iniciada pela empresa só pode ser
 * enviada por <em>modelo aprovado</em> pela Meta — por isso o envio é do tipo
 * {@code template}, com três variáveis no corpo: nome do paciente, data/hora e nome do
 * psicólogo. Para ligar: criar o modelo, preencher id do número e token, e ativar a flag.
 */
@ApplicationScoped
public class WhatsAppReminderChannel implements ReminderChannel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    @ConfigProperty(name = "reminder.whatsapp.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "reminder.whatsapp.api-url", defaultValue = "https://graph.facebook.com/v20.0")
    String apiUrl;

    @ConfigProperty(name = "reminder.whatsapp.phone-number-id")
    Optional<String> phoneNumberId;

    @ConfigProperty(name = "reminder.whatsapp.token")
    Optional<String> token;

    @ConfigProperty(name = "reminder.whatsapp.template", defaultValue = "lembrete_sessao")
    String template;

    @ConfigProperty(name = "reminder.whatsapp.language", defaultValue = "pt_BR")
    String language;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String name() {
        return "WhatsApp";
    }

    @Override
    public boolean isEnabled() {
        return enabled && phoneNumberId.filter(v -> !v.isBlank()).isPresent()
                && token.filter(v -> !v.isBlank()).isPresent();
    }

    @Override
    public boolean canReach(ReminderMessage message) {
        return internationalNumber(message.patientPhone()) != null;
    }

    @Override
    public void send(ReminderMessage message) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/" + phoneNumberId.orElseThrow() + "/messages"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token.orElseThrow())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(message).encode()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("WhatsApp respondeu " + response.statusCode());
        }
    }

    /** Corpo da chamada, separado do envio para poder ser conferido em teste. */
    JsonObject payload(ReminderMessage message) {
        JsonArray parameters = new JsonArray()
                .add(text(message.patientFirstName()))
                .add(text(message.startsAt().format(WHEN)))
                .add(text(message.psychologistName()));
        return new JsonObject()
                .put("messaging_product", "whatsapp")
                .put("to", internationalNumber(message.patientPhone()))
                .put("type", "template")
                .put("template", new JsonObject()
                        .put("name", template)
                        .put("language", new JsonObject().put("code", language))
                        .put("components", new JsonArray().add(new JsonObject()
                                .put("type", "body")
                                .put("parameters", parameters))));
    }

    private static JsonObject text(String value) {
        return new JsonObject().put("type", "text").put("text", value == null ? "" : value);
    }

    /** Só dígitos, com DDI. Telefone de 10 ou 11 dígitos é tratado como brasileiro (55). */
    static String internationalNumber(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 10) return null;
        return digits.length() <= 11 ? "55" + digits : digits;
    }
}
