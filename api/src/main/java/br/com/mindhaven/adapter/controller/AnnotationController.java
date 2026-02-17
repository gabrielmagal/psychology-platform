package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.AnnotationService;
import br.com.mindhaven.domain.entity.AnnotationEntity;

import br.com.mindhaven.adapter.controller.dto.AnnotationDto;
import br.com.mindhaven.mapper.BaseMapper;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

import java.util.Map;

@Path("/annotation")
public class AnnotationController extends AbstractBaseController<AnnotationDto, AnnotationEntity> {
    @Inject
    public AnnotationController(AnnotationService service, BaseMapper<AnnotationDto, AnnotationEntity> mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(AnnotationDto.class);
    }
}
