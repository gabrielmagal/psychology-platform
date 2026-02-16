package br.com.psicologia.mapper;

import br.com.psicologia.adapter.controller.dto.AuditLogDto;
import br.com.psicologia.domain.entity.AuditLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface AuditLogMapper extends BaseMapper<AuditLogDto, AuditLogEntity> {
    AuditLogDto toDto(AuditLogEntity entity);
    AuditLogEntity toEntity(AuditLogDto dto);
}