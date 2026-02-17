package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.AnnotationDto;
import br.com.mindhaven.domain.entity.AnnotationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta", uses = { SessionMapper.class })
public interface AnnotationMapper extends BaseMapper<AnnotationDto, AnnotationEntity> {
    AnnotationDto toDto(AnnotationEntity entity);
    AnnotationEntity toEntity(AnnotationDto dto);
}
