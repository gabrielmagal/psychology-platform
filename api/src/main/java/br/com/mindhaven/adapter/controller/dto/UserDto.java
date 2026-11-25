package br.com.mindhaven.adapter.controller.dto;

import br.com.mindhaven.domain.entity.enums.UserType;
import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
// Nome, CPF, contato e foto vêm de BaseUserDto (módulo platform-user).
public class UserDto extends core.user.BaseUserDto {

    @ILabel("Registro profissional (CRP)")
    @IShowInForm
    @IGroup("Acesso")
    private String crp;

    @ILabel("Endereço")
    @IShowInForm
    @IGroup("Contato")
    private String address;

    @ILabel("Profissão")
    @IShowInForm
    @IGroup("Dados pessoais")
    private String occupation;

    @ILabel("Contato de emergência")
    @IShowInForm
    @IGroup("Contato de emergência")
    private String emergencyContactName;

    @ILabel("Telefone de emergência")
    @IShowInForm
    @IGroup("Contato de emergência")
    private String emergencyContactPhone;

    @ILabel("Nome do responsável")
    @IShowInForm
    @IGroup("Responsável legal (menor de idade)")
    private String guardianName;

    @ILabel("Telefone do responsável")
    @IShowInForm
    @IGroup("Responsável legal (menor de idade)")
    private String guardianPhone;

    @ILabel("Perfil")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Acesso")
    private UserType userType;
}
