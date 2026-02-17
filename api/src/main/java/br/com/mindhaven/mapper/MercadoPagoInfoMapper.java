package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.MercadoPagoInfoDto;
import br.com.mindhaven.domain.entity.MercadoPagoInfoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface MercadoPagoInfoMapper extends BaseMapper<MercadoPagoInfoDto, MercadoPagoInfoEntity> {
    @Mapping(target = "user.mercadoPagoInfo", ignore = true)
    MercadoPagoInfoEntity toEntity(MercadoPagoInfoDto dto);

    @Mapping(target = "user.mercadoPagoInfo", ignore = true)
    MercadoPagoInfoDto toDto(MercadoPagoInfoEntity entity);
}