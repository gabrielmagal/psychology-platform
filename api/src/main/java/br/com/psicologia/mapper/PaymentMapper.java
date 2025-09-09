package br.com.psicologia.mapper;

import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.repository.model.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta", uses = {UserMapper.class, SessionPackageMapper.class})
public interface PaymentMapper extends BaseMapper<PaymentDto, PaymentEntity> {
    PaymentDto toDto(PaymentEntity entity);
    PaymentEntity toEntity(PaymentDto dto);
}