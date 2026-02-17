package br.com.mindhaven.adapter.controller.dto;

import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionPackageDto extends BaseDto {

    @IShowInForm(value = false)
    @IShowInTable(value = false)
    private UUID psychologistId;

    @ILabel("Paciente")
    @IShowInForm
    @IShowInTable
    @IShowField({"firstName", "lastName"})
    @ManyToOne(optional = false)
    private UserDto patient;

    @ILabel("Sessões")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @IManageAsSubEntity(label = "Sessões", parentField = "sessionPackage")
    private List<SessionDto> session = new ArrayList<>();

    @ILabel("Pagamento")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @IManageAsSubEntity(label = "Pagamentos", parentField = "sessionPackage")
    private List<PaymentDto> payment = new ArrayList<>();

    @ILabel("Título")
    @IShowInForm
    @IShowInTable
    private String packageTitle;

    @ILabel("Total de Sessões")
    @IShowInForm
    @IShowInTable
    private Integer totalSessions;
}
