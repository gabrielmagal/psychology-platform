package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.UserDto;
import br.com.mindhaven.domain.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface UserMapper extends BaseMapper<UserDto, UserEntity> {
    UserDto toDto(UserEntity entity);

    UserEntity toEntity(UserDto dto);
}