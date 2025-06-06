package br.com.psicologia.controller;

import br.com.psicologia.controller.dto.AnnotationDto;
import br.com.psicologia.mapper.AnnotationMapper;
import br.com.psicologia.repository.model.AnnotationEntity;
import br.com.psicologia.service.AnnotationService;
import core.controller.AbstractBaseController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/annotation")
public class AnnotationController extends AbstractBaseController<AnnotationDto, AnnotationEntity> {
    public AnnotationController(AnnotationService service, AnnotationMapper mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return service.describeEntity(AnnotationDto.class);
    }
}
