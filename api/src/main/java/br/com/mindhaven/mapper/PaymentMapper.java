package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.PaymentDto;
import br.com.mindhaven.domain.entity.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, SessionPackageMapper.class})
public interface PaymentMapper extends BaseMapper<PaymentDto, PaymentEntity> {
    PaymentDto toDto(PaymentEntity entity);
    PaymentEntity toEntity(PaymentDto dto);
}