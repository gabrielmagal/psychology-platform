package core.user;

import core.controller.dto.BaseDto;
import core.notes.IGroup;
import core.notes.ILabel;
import core.notes.IPhoto;
import core.notes.IShowInForm;
import core.notes.IShowInTable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Os campos do usuário base como aparecem no formulário e na tabela. A aplicação estende
 * este DTO com os campos que só ela tem; os daqui já vêm com rótulo, grupo e validação.
 */
@Getter
@Setter
@NoArgsConstructor
public class BaseUserDto extends BaseDto {
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

    @ILabel("Data de Nascimento")
    @IShowInForm
    @IShowInTable(value = false)
    @NotNull
    @IGroup("Dados pessoais")
    private LocalDate birthDate;

    @ILabel("Imagem")
    @IShowInForm
    @IShowInTable(value = false)
    @IPhoto
    @IGroup("Dados pessoais")
    private String profileImage;

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
}
