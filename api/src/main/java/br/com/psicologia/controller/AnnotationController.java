package br.com.psicologia.controller;

import br.com.psicologia.context.annotation.AnnotationContext;
import br.com.psicologia.controller.dto.AnnotationDto;
import br.com.psicologia.mapper.AnnotationMapper;
import br.com.psicologia.model.AnnotationEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/annotation")
public class AnnotationController extends AbstractBaseContextController<AnnotationDto, AnnotationEntity> {
    public AnnotationController(AnnotationContext context, AnnotationMapper mapper) {
        super(context, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(AnnotationDto.class);
    }
}
