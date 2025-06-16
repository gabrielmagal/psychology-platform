package br.com.psicologia.controller;

import br.com.psicologia.context.audit.AuditLogContext;
import br.com.psicologia.controller.dto.AuditLogDto;
import br.com.psicologia.mapper.AuditLogMapper;
import br.com.psicologia.repository.model.AuditLogEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/audit-log")
public class AuditLogController extends AbstractBaseContextController<AuditLogDto, AuditLogEntity> {
    public AuditLogController(AuditLogContext context, AuditLogMapper mapper) {
        super(context, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(AuditLogDto.class);
    }
}
