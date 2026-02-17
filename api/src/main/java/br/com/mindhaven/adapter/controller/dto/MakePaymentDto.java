package br.com.mindhaven.adapter.controller.dto;

import core.controller.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MakePaymentDto extends BaseDto {
    private String collectionId;
    private Long paymentId;
    private String preferenceId;
    private String status;
    private String paymentType;
    private String merchantOrderId;
}
