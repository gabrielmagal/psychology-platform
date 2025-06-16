package br.com.psicologia.controller.dto;

import br.com.psicologia.repository.model.SessionEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.notes.ILabel;
import core.notes.IShowField;
import core.notes.IShowInForm;
import core.notes.IShowInTable;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionRatingDto {
    @ILabel("Pacote de Sessão")
    @IShowInForm(value = true)
    @IShowInTable(value = true)
    @IShowField("packageTitle")
    @ManyToOne
    private SessionDto session;

    @ILabel("Paciente")
    @IShowInForm
    @IShowInTable
    @IShowField({"firstName", "lastName"})
    @ManyToOne(optional = true)
    private UserEntity patient;

    @Min(1)
    @Max(5)
    @IShowInForm(value = true)
    @IShowInTable(value = true)
    private int rating;

    @IShowInForm(value = true)
    @IShowInTable(value = true)
    private String comment;

    @IShowInForm(value = true)
    @IShowInTable(value = true)
    private LocalDateTime createdAt = LocalDateTime.now();
}
