package br.com.psicologia.adapter.controller;

import br.com.psicologia.application.service.AnnotationService;
import br.com.psicologia.domain.entity.AnnotationEntity;

import br.com.psicologia.adapter.controller.dto.AnnotationDto;
import br.com.psicologia.mapper.BaseMapper;
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
