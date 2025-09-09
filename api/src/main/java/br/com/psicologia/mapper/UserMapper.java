package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.UserDto;
import br.com.psicologia.repository.model.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface UserMapper extends BaseMapper<UserDto, UserEntity> {
    @Mapping(target = "mercadoPagoInfo.user", ignore = true)
    UserDto toDto(UserEntity entity);
    @Mapping(target = "mercadoPagoInfo.user", ignore = true)
    UserEntity toEntity(UserDto dto);
}