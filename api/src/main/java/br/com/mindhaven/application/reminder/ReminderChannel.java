package br.com.mindhaven.application.reminder;

/**
 * Um meio de enviar lembrete de sessão (e-mail, WhatsApp...). Para acrescentar um canal
 * basta criar um bean que implemente esta interface: o {@link ReminderService} descobre
 * todos sozinho.
 */
public interface ReminderChannel {
    /** Nome curto para log. */
    String name();

    /** Ligado na configuração e com as credenciais necessárias. */
    boolean isEnabled();

    /** O paciente tem o contato que este canal usa (e-mail, telefone...). */
    boolean canReach(ReminderMessage message);

    /** Envia. Lança exceção se o envio falhar. */
    void send(ReminderMessage message) throws Exception;
}
