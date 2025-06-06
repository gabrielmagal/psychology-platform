package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.UserDto;
import br.com.psicologia.repository.model.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface UserMapper extends BaseMapper<UserDto, UserEntity> {
}