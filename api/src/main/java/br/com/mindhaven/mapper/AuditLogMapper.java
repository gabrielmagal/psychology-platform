package br.com.mindhaven.mapper;

import core.mapper.BaseMapper;

import br.com.mindhaven.adapter.controller.dto.AuditLogDto;
import core.audit.AuditLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface AuditLogMapper extends BaseMapper<AuditLogDto, AuditLogEntity> {
    AuditLogDto toDto(AuditLogEntity entity);
    AuditLogEntity toEntity(AuditLogDto dto);
}