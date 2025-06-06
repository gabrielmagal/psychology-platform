package br.com.psicologia.controller;

import br.com.psicologia.controller.dto.SessionDto;
import br.com.psicologia.mapper.SessionMapper;
import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.service.SessionService;
import core.controller.AbstractBaseController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/session")
public class SessionController extends AbstractBaseController<SessionDto, SessionEntity> {
    public SessionController(SessionService service, SessionMapper mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return service.describeEntity(SessionDto.class);
    }
}
