package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.SessionDto;
import br.com.mindhaven.domain.entity.SessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, PaymentMapper.class, SessionPackageMapper.class})
public interface SessionMapper extends BaseMapper<SessionDto, SessionEntity> {
    @Mapping(target = "annotation", ignore = true)
    SessionDto toDto(SessionEntity entity);

    @Mapping(target = "annotation", ignore = true)
    SessionEntity toEntity(SessionDto dto);
}