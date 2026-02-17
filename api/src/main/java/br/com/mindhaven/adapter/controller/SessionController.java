package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.SessionService;
import br.com.mindhaven.adapter.controller.dto.SessionDto;
import br.com.mindhaven.mapper.BaseMapper;
import br.com.mindhaven.domain.entity.SessionEntity;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/session")
public class SessionController extends AbstractBaseController<SessionDto, SessionEntity> {
    @Inject
    public SessionController(SessionService service, BaseMapper<SessionDto, SessionEntity> mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(SessionDto.class);
    }
}
