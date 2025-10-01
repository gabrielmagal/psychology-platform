package br.com.psicologia.controller;

import br.com.psicologia.context.user.UserContext;
import br.com.psicologia.controller.dto.UserDto;
import br.com.psicologia.mapper.UserMapper;
import br.com.psicologia.model.UserEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

@Path("/user")
public class UserController extends AbstractBaseContextController<UserDto, UserEntity> {

    private final UserContext userContext;

    public UserController(UserContext context, UserMapper mapper) {
        super(context, mapper);
        this.userContext = context;
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(UserDto.class);
    }

    @GET
    @Path("/keycloak")
    public UserDto findByKeycloakId(@Context SecurityContext securityContext) {
        UserEntity entity = userContext.findByKeycloakId(securityContext, headers.getHeaderString("Tenant"));
        if (entity != null) {
            return mapper.toDto(entity);
        } else {
            throw new NotFoundException();
        }
    }
}
