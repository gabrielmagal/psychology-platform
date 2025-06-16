package br.com.psicologia.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum StatusCarga {
    CRIADA,
    PENDENTE,
    ACEITA,
    RECUSADA,
    ENTREGUE
}
