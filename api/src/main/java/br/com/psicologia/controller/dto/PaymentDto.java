package br.com.psicologia.controller.dto;

import br.com.psicologia.enums.PaymentMethod;
import core.controller.dto.BaseDto;
import core.notes.*;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto extends BaseDto {

    @ILabel("Valor")
    @IShowInForm
    @IShowInTable
    @NotNull
    private BigDecimal amount;

    @ILabel("Método de Pagamento")
    @IShowInForm
    @IShowInTable
    @NotNull
    private PaymentMethod paymentMethod;

    @ILabel("Data do Pagamento")
    @IShowInForm
    @IShowInTable
    @NotNull
    private LocalDate paymentDate;

    @ILabel("Pago")
    @IShowInForm
    @IShowInTable
    @NotNull
    private boolean paid;

    @ILabel("Observação")
    @IShowInForm
    private String observation;

    @ILabel("Recibo (arquivo)")
    @IShowInForm
    @IShowInTable(value = false)
    @IFile
    private String receipt;

    @ILabel("Nome do Recibo")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String receiptName;

    @ILabel("Tipo do Recibo")
    @IShowInForm
    @IShowInTable
    @NotNull
    private String receiptType;

    @ILabel("Pacote de Sessão")
    @IShowInForm(value = false)
    @IShowInTable(value = false)
    @IShowField("packageTitle")
    @ManyToOne
    private SessionPackageDto sessionPackage;
}
