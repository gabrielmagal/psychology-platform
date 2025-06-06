package br.com.psicologia.repository.model.enums;

import core.repository.model.interfaces.ILabel;

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