package br.com.mindhaven.infrastructure.interceptor;

import br.com.mindhaven.domain.entity.AuditLogEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import core.repository.dao.GenericDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuditListenerTest {

    private static final String NOTA_CLINICA = "paciente relatou crise de ansiedade na segunda-feira";
    private static final String NOTA_CLINICA_REVISADA = "paciente relatou melhora apos a sessao";

    @Inject
    GenericDao dao;

    private List<AuditLogEntity> auditOf(String tenant, String entityId) {
        return dao.listAll(tenant, AuditLogEntity.class).stream()
                .filter(log -> entityId.equals(log.getEntityId()))
                .sorted(Comparator.comparing(AuditLogEntity::getTimestamp))
                .toList();
    }

    @Test
    @DisplayName("nota clínica não é copiada em claro para a trilha de auditoria")
    void doesNotCopyClinicalNotesIntoAuditLog() {
        String tenant = "clinica_auditoria";

        SessionEntity session = SessionEntity.builder()
                .title("Sessão 1")
                .summary("Resumo público")
                .dateSession(LocalDate.of(2026, 3, 10))
                .privateNotes(NOTA_CLINICA)
                .build();

        SessionEntity saved = dao.update(tenant, session);

        List<AuditLogEntity> logs = auditOf(tenant, saved.getId().toString());
        assertFalse(logs.isEmpty(), "a criação deveria ter gerado registro de auditoria");

        AuditLogEntity create = logs.getFirst();
        assertEquals("CREATE", create.getAction());
        assertNotNull(create.getNewValue());

        // O ponto central: o conteúdo clínico não pode estar no audit_log.
        assertFalse(create.getNewValue().contains(NOTA_CLINICA),
                "a nota clínica vazou para o audit_log: " + create.getNewValue());
        assertTrue(create.getNewValue().contains("sha256:"),
                "o campo sensível deveria aparecer como impressão digital");

        // Campos não sensíveis continuam auditáveis normalmente.
        assertTrue(create.getNewValue().contains("Sessão 1"));
        assertTrue(create.getNewValue().contains("Resumo público"));
    }

    @Test
    @DisplayName("alteração de campo sensível é detectável pela impressão digital")
    void fingerprintRevealsChangeWithoutRevealingContent() {
        String tenant = "clinica_auditoria_update";

        SessionEntity saved = dao.update(tenant, SessionEntity.builder()
                .title("Sessão 2")
                .dateSession(LocalDate.of(2026, 3, 11))
                .privateNotes(NOTA_CLINICA)
                .build());

        saved.setPrivateNotes(NOTA_CLINICA_REVISADA);
        dao.update(tenant, saved);

        AuditLogEntity update = auditOf(tenant, saved.getId().toString()).stream()
                .filter(log -> "UPDATE".equals(log.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nenhum registro UPDATE gerado"));

        assertNotNull(update.getOldValue());
        assertNotNull(update.getNewValue());

        assertFalse(update.getOldValue().contains(NOTA_CLINICA));
        assertFalse(update.getNewValue().contains(NOTA_CLINICA_REVISADA));

        // Antes, o "valor antigo" vinha do cache de primeiro nível e era igual ao novo:
        // a trilha nunca mostrava diferença.
        assertNotEquals(fingerprintOf(update.getOldValue()), fingerprintOf(update.getNewValue()),
                "a impressão digital deveria mudar quando o campo muda");
    }

    /** Extrai o primeiro sha256:… do snapshot. */
    private String fingerprintOf(String snapshot) {
        int start = snapshot.indexOf("sha256:");
        assertTrue(start >= 0, "snapshot sem impressão digital: " + snapshot);
        int end = snapshot.indexOf('"', start);
        return snapshot.substring(start, end < 0 ? snapshot.length() : end);
    }
}
