package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.repository.model.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi", uses = {UserMapper.class, SessionPackageMapper.class})
public interface PaymentMapper extends BaseMapper<PaymentDto, PaymentEntity> {
    //@Mapping(target = "sessionPackage", ignore = true)
    PaymentDto toDto(PaymentEntity entity);

    //@Mapping(target = "sessionPackage", ignore = true)
    PaymentEntity toEntity(PaymentDto dto);
}