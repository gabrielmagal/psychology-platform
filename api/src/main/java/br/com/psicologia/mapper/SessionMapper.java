package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.SessionDto;
import br.com.psicologia.model.SessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, PaymentMapper.class, SessionPackageMapper.class})
public interface SessionMapper extends BaseMapper<SessionDto, SessionEntity> {

    @Mapping(target = "annotation", ignore = true)
    SessionDto toDto(SessionEntity entity);

    @Mapping(target = "annotation", ignore = true)
    SessionEntity toEntity(SessionDto dto);
}