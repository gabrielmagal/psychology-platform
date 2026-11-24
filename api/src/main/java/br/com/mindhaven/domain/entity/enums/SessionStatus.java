package br.com.mindhaven.domain.entity.enums;

import core.notes.ILabel;

public enum SessionStatus {
    @ILabel("Agendada")
    AGENDADA,
    @ILabel("Realizada")
    REALIZADA,
    @ILabel("Falta")
    FALTA,
    @ILabel("Cancelada")
    CANCELADA
}
