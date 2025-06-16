package br.com.psicologia.interceptor;

import br.com.psicologia.repository.model.AuditLogEntity;
import core.repository.model.BaseEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.ws.rs.core.SecurityContext;
import org.flywaydb.core.internal.util.JsonUtils;

@ApplicationScoped
public class AuditListener {

    @Inject
    EntityManager em;

    @Inject
    SecurityContext securityContext;

    @PrePersist
    public void prePersist(Object entity) {
        saveAudit(entity, "CREATE", null);
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        Object old = em.find(entity.getClass(), ((BaseEntity) entity).getId());
        saveAudit(entity, "UPDATE", old);
    }

    @PreRemove
    public void preRemove(Object entity) {
        saveAudit(entity, "DELETE", entity);
    }

    private void saveAudit(Object entity, String action, Object oldEntity) {
        String keycloakId = securityContext.getUserPrincipal().getName();

        AuditLogEntity log = new AuditLogEntity();
        log.setEntityName(entity.getClass().getSimpleName());
        log.setEntityId(String.valueOf(((BaseEntity) entity).getId()));
        log.setAction(action);
        log.setKeycloakUserId(keycloakId);

        if (oldEntity != null) {
            log.setOldValue(JsonUtils.toJson(oldEntity));
        }

        if (!"DELETE".equals(action)) {
            log.setNewValue(JsonUtils.toJson(entity));
        }

        em.persist(log);
    }
}