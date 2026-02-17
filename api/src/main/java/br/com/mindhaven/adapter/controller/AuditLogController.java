package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.AuditLogService;
import br.com.mindhaven.adapter.controller.dto.AuditLogDto;
import br.com.mindhaven.domain.entity.AuditLogEntity;
import br.com.mindhaven.mapper.BaseMapper;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/audit-log")
public class AuditLogController extends AbstractBaseController<AuditLogDto, AuditLogEntity> {
    @Inject
    public AuditLogController(AuditLogService service, BaseMapper<AuditLogDto, AuditLogEntity> mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(AuditLogDto.class);
    }
}
