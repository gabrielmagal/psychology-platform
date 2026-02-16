package br.com.psicologia.adapter.controller;

import br.com.psicologia.adapter.controller.dto.SessionRatingDto;
import br.com.psicologia.mapper.SessionRatingMapper;
import br.com.psicologia.domain.entity.SessionRatingEntity;
import br.com.psicologia.application.service.SessionRatingService;
import core.service.AbstractEntityDescriptionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

@Path("/session-rating")
public class SessionRatingController extends AbstractEntityDescriptionService {

    @Context
    public HttpHeaders headers;

    @Inject
    SessionRatingService sessionRatingService;

    @Inject
    SessionRatingMapper sessionRatingMapper;

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(SessionRatingEntity.class);
    }

    @POST
    public Response rateSession(@Context SecurityContext securityContext, SessionRatingDto dto) {
        String tenant = headers.getHeaderString("Tenant");
        if (tenant == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Tenant é obrigatório.")
                    .build();
        }
        return Response.ok(sessionRatingMapper.toDto(sessionRatingService.save(tenant, sessionRatingMapper.toEntity(dto)))).build();
    }
}
