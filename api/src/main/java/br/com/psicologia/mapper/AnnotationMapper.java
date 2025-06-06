package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.AnnotationDto;
import br.com.psicologia.repository.model.AnnotationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi", uses = { SessionMapper.class })
public interface AnnotationMapper extends BaseMapper<AnnotationDto, AnnotationEntity> {
    AnnotationDto toDto(AnnotationEntity entity);
    AnnotationEntity toEntity(AnnotationDto dto);
}
