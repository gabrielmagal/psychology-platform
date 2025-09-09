package br.com.psicologia.controller.dto;

import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoInfoDto extends BaseDto {

    @ILabel("Usuário")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @OneToOne(optional = false)
    private UserDto user;

    @ILabel("Access Token")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String accessToken;

    @ILabel("Refresh Token")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String refreshToken;

    @ILabel("Scopo")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String scope;

    @ILabel("Expira")
    @IShowInForm
    @IShowInTable
    @NotNull
    private Integer expiresIn;

    @ILabel("Data de criação")
    @IShowInForm
    @IShowInTable
    @NotNull
    private Instant tokenCreatedAt;
}
