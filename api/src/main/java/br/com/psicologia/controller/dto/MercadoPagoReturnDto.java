package br.com.psicologia.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoReturnDto {
    private String collectionId;
    private String paymentId;
    private String preferenceId;
    private String status;
    private String paymentType;
    private String merchantOrderId;
}