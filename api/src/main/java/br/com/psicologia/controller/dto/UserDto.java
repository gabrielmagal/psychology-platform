package br.com.psicologia.controller.dto;

import br.com.psicologia.enums.UserType;
import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
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
@AllArgsConstructor
public class UserDto extends BaseDto {
    private String keycloakId;
    private String registeredByKeycloakId;

    @ILabel("Cpf")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String cpf;

    @ILabel("Nome")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String firstName;

    @ILabel("Sobrenome")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String lastName;

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

    @ILabel("Imagem")
    @IShowInForm
    @IShowInTable(value = false)
    @IPhoto
    private String profileImage;

    @ILabel("Tipo de Usuário")
    @IShowInForm
    @IShowInTable
    @NotNull
    private UserType userType;
}
