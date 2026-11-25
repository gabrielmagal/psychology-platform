package core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Trilha de auditoria das entidades anotadas com {@code @EntityListeners(AuditListener.class)}.
 *
 * <p>Duas mudanças de fundo em relação à versão anterior:
 *
 * <ol>
 *   <li>O snapshot é montado a partir dos metadados do Hibernate, e não de uma serialização
 *       Jackson da entidade. Serializar a entidade arrastava o grafo de associações (podendo
 *       disparar carregamentos preguiçosos durante o flush) e gerava payloads
 *       desproporcionais para o que a auditoria precisa registrar.</li>
 *   <li>Campos marcados com {@link ISensitive} entram como impressão digital, nunca como
 *       conteúdo.</li>
 * </ol>
 */
@ApplicationScoped
public class AuditListener {

    private static final Logger LOG = Logger.getLogger(AuditListener.class);

    private static final String REDACTED_NULL = "sha256:null";

    @Inject
    EntityManager em;

    @Inject
    SecurityContext securityContext;

    @Inject
    ObjectMapper objectMapper;

    /** Nomes de propriedade a redigir, por entidade — a reflexão acontece uma vez por classe. */
    private final Map<Class<?>, Set<String>> sensitiveFieldsCache = new ConcurrentHashMap<>();

    @PrePersist
    public void prePersist(Object entity) {
        saveAudit(entity, "CREATE", null, currentState(entity));
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        saveAudit(entity, "UPDATE", previousState(entity), currentState(entity));
    }

    @PreRemove
    public void preRemove(Object entity) {
        saveAudit(entity, "DELETE", currentState(entity), null);
    }

    /**
     * Estado anterior à alteração, lido do contexto de persistência do Hibernate.
     *
     * <p>Antes isto era {@code em.find(entity.getClass(), id)} — que, dentro de
     * {@code @PreUpdate}, devolve <b>a própria instância gerenciada já modificada</b>, vinda
     * do cache de primeiro nível. Ou seja, o "valor antigo" gravado era idêntico ao novo e a
     * trilha não registrava mudança nenhuma. O snapshot carregado é a única fonte real do
     * estado anterior.
     */
    private String previousState(Object entity) {
        try {
            EntityEntry entry = entry(entity);
            if (entry == null || entry.getLoadedState() == null) return null;

            return snapshot(entity.getClass(), entry.getPersister().getPropertyNames(), entry.getLoadedState());
        } catch (Exception e) {
            LOG.debugf(e, "Não foi possível capturar o estado anterior de %s", entity.getClass().getSimpleName());
            return null;
        }
    }

    private String currentState(Object entity) {
        try {
            EntityPersister persister = persisterOf(entity);
            if (persister == null) return null;

            return snapshot(entity.getClass(), persister.getPropertyNames(), persister.getValues(entity));
        } catch (Exception e) {
            LOG.debugf(e, "Não foi possível capturar o estado atual de %s", entity.getClass().getSimpleName());
            return null;
        }
    }

    /** Monta o JSON plano de um estado, redigindo os campos sensíveis. */
    private String snapshot(Class<?> entityClass, String[] propertyNames, Object[] values) {
        Set<String> sensitive = sensitiveFieldsOf(entityClass);

        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        for (int i = 0; i < propertyNames.length && i < values.length; i++) {
            String name = propertyNames[i];
            Object value = values[i];

            if (sensitive.contains(name)) {
                snapshot.put(name, fingerprint(value));
            } else if (value instanceof BaseEntity related) {
                // Associações entram pelo identificador, para não arrastar o grafo inteiro.
                snapshot.put(name, related.getId());
            } else if (value instanceof java.util.Collection<?>) {
                // Coleções são geridas pelo lado dono; auditá-las aqui só duplicaria registros.
                continue;
            } else {
                snapshot.put(name, value);
            }
        }

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            LOG.debugf(e, "Falha ao serializar snapshot de %s", entityClass.getSimpleName());
            return null;
        }
    }

    /**
     * Impressão digital estável do valor: permite comparar dois registros de auditoria e
     * afirmar que o campo mudou, sem que o conteúdo em si fique guardado.
     */
    private String fingerprint(Object value) {
        if (value == null) return REDACTED_NULL;

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception e) {
            return "***";
        }
    }

    private Set<String> sensitiveFieldsOf(Class<?> entityClass) {
        return sensitiveFieldsCache.computeIfAbsent(entityClass, clazz -> {
            Set<String> names = new java.util.HashSet<>();
            for (Class<?> type = clazz; type != null && type != Object.class; type = type.getSuperclass()) {
                names.addAll(java.util.Arrays.stream(type.getDeclaredFields())
                        .filter(f -> f.isAnnotationPresent(ISensitive.class))
                        .map(java.lang.reflect.Field::getName)
                        .collect(Collectors.toSet()));
            }
            return Set.copyOf(names);
        });
    }

    private EntityEntry entry(Object entity) {
        return em.unwrap(SessionImplementor.class).getPersistenceContext().getEntry(entity);
    }

    private EntityPersister persisterOf(Object entity) {
        EntityEntry entry = entry(entity);
        if (entry != null) return entry.getPersister();

        return em.unwrap(SessionImplementor.class)
                .getFactory()
                .getMappingMetamodel()
                .findEntityDescriptor(entity.getClass());
    }

    /**
     * Grava o registro com um INSERT nativo em vez de {@code em.persist}.
     *
     * <p>Persistir uma entidade dentro de um callback {@code @PrePersist}/{@code @PreUpdate}
     * é proibido pela especificação JPA (os callbacks não podem usar o EntityManager): o
     * callback roda no meio do flush, e enfileirar uma entidade nova nesse momento mexe na
     * fila de ações que o próprio flush está percorrendo — funcionava por detalhe de
     * implementação do Hibernate. Um INSERT parametrizado na mesma conexão/transação não
     * toca no contexto de persistência, é atômico com a operação auditada (mesmo commit e
     * rollback) e respeita o {@code search_path} do tenant já aplicado à conexão.
     */
    private void saveAudit(Object entity, String action, String oldValue, String newValue) {
        em.createNativeQuery("""
                        INSERT INTO audit_log
                            (id, entity_name, entity_id, action, keycloak_user_id, old_value, new_value, timestamp)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                        """)
                // Sem isto, a query nativa força um auto-flush — e, como estamos DENTRO de um
                // flush (é ele que chama os callbacks), o flush reentraria no callback em
                // recursão infinita. O INSERT não depende de estado pendente: pode pular o flush.
                .setFlushMode(jakarta.persistence.FlushModeType.COMMIT)
                .setParameter(1, java.util.UUID.randomUUID())
                .setParameter(2, entity.getClass().getSimpleName())
                .setParameter(3, String.valueOf(((BaseEntity) entity).getId()))
                .setParameter(4, action)
                .setParameter(5, currentUserId())
                .setParameter(6, oldValue)
                .setParameter(7, newValue)
                .setParameter(8, java.time.LocalDateTime.now())
                .executeUpdate();
    }

    /**
     * Requisições sem principal (por exemplo, uma rotina interna) estouravam NPE aqui e
     * derrubavam junto a gravação da entidade — a auditoria não deveria ser capaz de
     * impedir a operação que ela está auditando.
     */
    private String currentUserId() {
        try {
            Principal principal = securityContext.getUserPrincipal();
            return principal != null ? principal.getName() : "system";
        } catch (Exception e) {
            return "system";
        }
    }
}
