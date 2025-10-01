package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.SessionRatingDto;
import br.com.psicologia.model.SessionRatingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, PaymentMapper.class})
public interface SessionRatingMapper extends BaseMapper<SessionRatingDto, SessionRatingEntity> {
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "patient", ignore = true)
    SessionRatingDto toDto(SessionRatingEntity entity);

    @Mapping(target = "session", ignore = true)
    @Mapping(target = "patient", ignore = true)
    SessionRatingEntity toEntity(SessionRatingDto dto);
}
