package br.com.psicologia.controller;

import br.com.psicologia.context.session.SessionContext;
import br.com.psicologia.controller.dto.SessionDto;
import br.com.psicologia.mapper.SessionMapper;
import br.com.psicologia.repository.model.SessionEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/session")
public class SessionController extends AbstractBaseContextController<SessionDto, SessionEntity> {
    public SessionController(SessionContext context, SessionMapper mapper) {
        super(context, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(SessionDto.class);
    }
}
