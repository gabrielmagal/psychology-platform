package br.com.psicologia.adapter.controller;

import br.com.psicologia.application.service.PaymentService;
import br.com.psicologia.adapter.controller.dto.MakePaymentDto;
import br.com.psicologia.adapter.controller.dto.PaymentDto;
import br.com.psicologia.mapper.BaseMapper;
import br.com.psicologia.domain.entity.PaymentEntity;
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
