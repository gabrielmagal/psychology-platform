package br.com.psicologia.adapter.controller.dto;

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

    @ILabel("Título")
    @IShowInForm
    @IShowInTable
    private String title;

    @ILabel("Resumo")
    @IShowInForm
    @IShowInTable
    private String summary;

    @ILabel("Notas")
    @IShowInForm
    private String privateNotes;

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

    @ILabel("Anotações")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @IManageAsSubEntity(label = "Anotações")
    private List<AnnotationDto> annotation = new ArrayList<>();
}
