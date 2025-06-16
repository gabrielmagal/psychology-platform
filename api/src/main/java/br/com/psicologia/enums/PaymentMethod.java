package br.com.psicologia.enums;

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