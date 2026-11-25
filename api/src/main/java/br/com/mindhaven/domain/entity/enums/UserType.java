package br.com.mindhaven.domain.entity.enums;

import core.notes.ILabel;

public enum UserType {
    @ILabel("Profissional")
    PROFISSIONAL,
    @ILabel("Paciente")
    PACIENTE,
    @ILabel("Administrador")
    ADMINISTRADOR
}