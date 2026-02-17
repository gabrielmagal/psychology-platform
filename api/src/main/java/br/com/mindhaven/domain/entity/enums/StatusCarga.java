package br.com.mindhaven.domain.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum StatusCarga {
    CRIADA,
    PENDENTE,
    ACEITA,
    RECUSADA,
    ENTREGUE
}
