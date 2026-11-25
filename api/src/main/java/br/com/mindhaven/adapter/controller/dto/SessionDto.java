package br.com.mindhaven.adapter.controller.dto;

import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto extends BaseDto {

    @ILabel("Data")
    @IShowInForm
    @IShowInTable
    private LocalDate dateSession;

    @ILabel("Horário")
    @IShowInForm
    @IShowInTable
    private java.time.LocalTime startTime;

    @ILabel("Duração (min)")
    @IShowInForm
    private Integer durationMinutes;

    @ILabel("Situação")
    @IShowInForm
    @IShowInTable
    private br.com.mindhaven.domain.entity.enums.SessionStatus status;

    @ILabel("Modalidade")
    @IShowInForm
    @IShowInTable
    private br.com.mindhaven.domain.entity.enums.SessionModality modality;

    @ILabel("Link do atendimento online")
    @IShowInForm
    private String meetingLink;

    @IShowInForm(value = false)
    @IShowInTable(value = false)
    private LocalDateTime confirmedAt;

    @IShowInForm(value = false)
    @IShowInTable(value = false)
    private LocalDateTime remindedAt;

    @ILabel("Assunto")
    @IShowInForm
    @IShowInTable
    private String title;

    // O registro clínico (demanda, observações, intervenções, evolução, plano) não passa por
    // este formulário: tem tela própria, com conclusão e adendos. Ver SessionRecord.

    @ILabel("Presença Confirmada")
    @IShowInForm
    @IShowInTable
    private LocalDateTime attendedAt;

    @ILabel("Pacote de Sessões")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @IShowField("packageTitle")
    @ManyToOne
    private SessionPackageDto sessionPackage;

}
