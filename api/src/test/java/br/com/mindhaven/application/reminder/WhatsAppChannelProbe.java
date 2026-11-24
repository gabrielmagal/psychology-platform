package br.com.mindhaven.application.reminder;

import io.vertx.core.json.JsonObject;

/** Abre, para os testes, as partes do canal WhatsApp que não dependem da rede. */
public class WhatsAppChannelProbe {
    private final WhatsAppReminderChannel channel = new WhatsAppReminderChannel();

    public WhatsAppChannelProbe() {
        channel.template = "lembrete_sessao";
        channel.language = "pt_BR";
    }

    public String number(String phone) {
        return WhatsAppReminderChannel.internationalNumber(phone);
    }

    public JsonObject payload(ReminderMessage message) {
        return channel.payload(message);
    }
}
