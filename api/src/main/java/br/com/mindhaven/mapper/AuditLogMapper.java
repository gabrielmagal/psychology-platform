package br.com.mindhaven.mapper;

import br.com.mindhaven.adapter.controller.dto.AuditLogDto;
import br.com.mindhaven.domain.entity.AuditLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface AuditLogMapper extends BaseMapper<AuditLogDto, AuditLogEntity> {
    AuditLogDto toDto(AuditLogEntity entity);
    AuditLogEntity toEntity(AuditLogDto dto);
}