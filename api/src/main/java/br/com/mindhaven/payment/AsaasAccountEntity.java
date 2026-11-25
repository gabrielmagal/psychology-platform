package br.com.mindhaven.payment;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import jakarta.persistence.Convert;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Conta do Asaas de um profissional — é para ela que vai o dinheiro dos pacotes dele. */
@Entity
@Table(name = "asaas_account")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditListener.class)
public class AsaasAccountEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    // Credencial de pagamento: nunca vai para a auditoria em claro nem volta para o navegador.
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /** true = ambiente de testes do Asaas; false = produção (dinheiro de verdade). */
    @Column(name = "sandbox", nullable = false)
    private boolean sandbox = true;

    /** Segredo que o Asaas envia em cada aviso (webhook), para provar que o aviso é dele. */
    @ISensitive
    @Column(name = "webhook_token", nullable = false, length = 100)
    private String webhookToken;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
