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
@AllArgsConstructor
public class UserDto extends BaseDto {

    private String keycloakId;
    private String registeredByKeycloakId;

    /** Só vem preenchida na resposta do cadastro: é mostrada uma única vez a quem cadastrou. */
    private String temporaryPassword;

    @ILabel("CPF")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Dados pessoais")
    private String cpf;

    @ILabel("Nome")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Dados pessoais")
    private String firstName;

    @ILabel("Sobrenome")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Dados pessoais")
    private String lastName;

    @ILabel("E-mail")
    @IShowInForm
    @IShowInTable
    @NotNull
    @Email
    @IGroup("Contato")
    private String email;

    @ILabel("Telefone")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Contato")
    private String phoneNumber;

    @ILabel("Data de Nascimento")
    @IShowInForm
    @IShowInTable(value = false)
    @NotNull
    @IGroup("Dados pessoais")
    private LocalDate birthDate;

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

    @ILabel("Imagem")
    @IShowInForm
    @IShowInTable(value = false)
    @IPhoto
    @IGroup("Dados pessoais")
    private String profileImage;

    @ILabel("Perfil")
    @IShowInForm
    @IShowInTable
    @NotNull
    @IGroup("Acesso")
    private UserType userType;
}
