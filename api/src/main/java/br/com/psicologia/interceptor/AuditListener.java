package br.com.psicologia.interceptor;

import br.com.psicologia.model.AuditLogEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Inject
    ObjectMapper objectMapper;

    @PrePersist
    public void prePersist(Object entity) throws JsonProcessingException {
        saveAudit(entity, "CREATE", null);
    }

    @PreUpdate
    public void preUpdate(Object entity) throws JsonProcessingException {
        Object old = em.find(entity.getClass(), ((BaseEntity) entity).getId());
        saveAudit(entity, "UPDATE", old);
    }

    @PreRemove
    public void preRemove(Object entity) throws JsonProcessingException {
        saveAudit(entity, "DELETE", entity);
    }

    private void saveAudit(Object entity, String action, Object oldEntity) throws JsonProcessingException {
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
            log.setNewValue(objectMapper.writeValueAsString(entity));
        }

        em.persist(log);
    }
}