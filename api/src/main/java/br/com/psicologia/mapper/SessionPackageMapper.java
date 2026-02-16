package br.com.psicologia.mapper;

import br.com.psicologia.adapter.controller.dto.SessionPackageDto;
import br.com.psicologia.domain.entity.SessionPackageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, PaymentMapper.class})
public interface SessionPackageMapper extends BaseMapper<SessionPackageDto, SessionPackageEntity> {
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "payment", ignore = true)
    SessionPackageDto toDto(SessionPackageEntity entity);

    @Mapping(target = "session", ignore = true)
    @Mapping(target = "payment", ignore = true)
    SessionPackageEntity toEntity(SessionPackageDto dto);
}
