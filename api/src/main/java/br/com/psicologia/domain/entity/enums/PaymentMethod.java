package br.com.psicologia.domain.entity.enums;

import core.notes.ILabel;

public enum PaymentMethod {

    @ILabel("Pix")
    PIX,

    @ILabel("Dinheiro")
    CASH,

    @ILabel("Transferência")
    TRANSFER,

    @ILabel("Cartão")
    CARD,

    @ILabel("Outro")
    OTHER
}