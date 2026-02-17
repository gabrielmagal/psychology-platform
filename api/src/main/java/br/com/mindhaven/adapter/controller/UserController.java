package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.adapter.controller.dto.UserDto;
import br.com.mindhaven.mapper.BaseMapper;
import br.com.mindhaven.domain.entity.UserEntity;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

@Path("/user")
public class UserController extends AbstractBaseController<UserDto, UserEntity> {

    private final UserService userService;

    @Inject
    public UserController(UserService service, BaseMapper<UserDto, UserEntity> mapper) {
        super(service, mapper);
        this.userService = service;
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(UserDto.class);
    }

    @GET
    @Path("/keycloak")
    public UserDto findByKeycloakId(@Context SecurityContext securityContext) {
        UserEntity entity = userService.getCurrentLoggedUser();
        if (entity != null) {
            return mapper.toDto(entity);
        } else {
            throw new NotFoundException();
        }
    }
}
