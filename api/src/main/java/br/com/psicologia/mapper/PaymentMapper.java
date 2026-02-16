package br.com.psicologia.mapper;

import br.com.psicologia.adapter.controller.dto.PaymentDto;
import br.com.psicologia.domain.entity.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, SessionPackageMapper.class})
public interface PaymentMapper extends BaseMapper<PaymentDto, PaymentEntity> {
    PaymentDto toDto(PaymentEntity entity);
    PaymentEntity toEntity(PaymentDto dto);
}