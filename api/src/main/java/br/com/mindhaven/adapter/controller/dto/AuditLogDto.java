package br.com.mindhaven.adapter.controller.dto;

import core.controller.dto.BaseDto;
import core.notes.ILabel;
import core.notes.IShowInForm;
import core.notes.IShowInTable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto extends BaseDto {

    @ILabel("Nome Entidade")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String entityName;

    @ILabel("Id Entidade")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String entityId;

    @ILabel("Ação")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String action;

    @ILabel("Id Keycloak")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String keycloakUserId;

    @ILabel("Valor anterior")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String oldValue;

    @ILabel("Valor novo")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String newValue;

    @ILabel("Data/Hora")
    @IShowInForm
    @IShowInTable
    @NotNull
    private LocalDateTime timestamp = LocalDateTime.now();
}
