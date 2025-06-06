package br.com.psicologia.controller.dto;

import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.repository.model.enums.UserType;
import core.controller.dto.BaseDto;
import core.repository.model.interfaces.ILabel;
import core.repository.model.interfaces.IShowInForm;
import core.repository.model.interfaces.IShowInTable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto extends BaseDto {
    private String keycloakId;

    @ILabel("Cpf")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String cpf;

    @ILabel("Nome do Usuário")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String name;

    @ILabel("Email")
    @IShowInForm
    @IShowInTable
    @NotNull
    @Email
    private String email;

    @ILabel("Telefone")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String phoneNumber;

    @ILabel("Data de Nascimento")
    @IShowInForm
    @IShowInTable
    @NotNull
    private LocalDate birthDate;

    @ILabel("Tipo de Usuário")
    @IShowInForm
    @IShowInTable
    @NotNull
    private UserType userType;
}
