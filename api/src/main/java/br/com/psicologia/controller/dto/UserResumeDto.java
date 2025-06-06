package br.com.psicologia.controller.dto;

import core.controller.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResumeDto extends BaseDto {
    private String keycloakId;
    private String name;
}