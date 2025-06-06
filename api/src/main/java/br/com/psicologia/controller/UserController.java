package br.com.psicologia.controller;

import br.com.psicologia.controller.dto.UserDto;
import br.com.psicologia.mapper.UserMapper;
import br.com.psicologia.repository.model.UserEntity;
import br.com.psicologia.service.UserService;
import core.controller.AbstractBaseController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/user")
public class UserController extends AbstractBaseController<UserDto, UserEntity> {
    public UserController(UserService service, UserMapper mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return service.describeEntity(UserDto.class);
    }
}
