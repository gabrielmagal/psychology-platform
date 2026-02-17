package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.PaymentService;
import br.com.mindhaven.adapter.controller.dto.MakePaymentDto;
import br.com.mindhaven.adapter.controller.dto.PaymentDto;
import br.com.mindhaven.mapper.BaseMapper;
import br.com.mindhaven.domain.entity.PaymentEntity;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/payment")
public class PaymentController extends AbstractBaseController<PaymentDto, PaymentEntity> {

    private final PaymentService paymentService;

    @Inject
    public PaymentController(PaymentService service, BaseMapper<PaymentDto, PaymentEntity> mapper) {
        super(service, mapper);
        this.paymentService = service;
    }

    @POST
    @Path("/confirm")
    public void confirmarPagamento(MakePaymentDto makePaymentDto) {
        paymentService.makePayment(makePaymentDto);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(PaymentDto.class);
    }
}
