package br.com.psicologia.adapter.controller.dto;

import core.controller.dto.BaseDto;
import core.notes.ILabel;
import core.notes.IShowInForm;
import core.notes.IShowInTable;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnnotationDto extends BaseDto {
    @ILabel("Sentimento Principal")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String mainFeeling;

    @ILabel("Eventos Significativos")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String significantEvents;

    @ILabel("Fase Atual")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String currentPhase;

    @ILabel("Pensamento Dominante")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String dominantThought;

    @ILabel("Intervenção")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String intervention;

    @ManyToOne(optional = false)
    private SessionDto session;
}
