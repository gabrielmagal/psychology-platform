package br.com.psicologia.controller;

import br.com.psicologia.context.payment.PaymentContext;
import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.repository.model.PaymentEntity;
import core.controller.AbstractBaseContextController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

@Path("/payment")
public class PaymentController extends AbstractBaseContextController<PaymentDto, PaymentEntity> {

    @Inject
    PaymentContext paymentContext;

    public PaymentController(PaymentContext context, PaymentMapper mapper) {
        super(context, mapper);
    }

    @POST
    @Path("/confirm")
    public void confirmarPagamento(@Context SecurityContext securityContext, String paymentId) {
        paymentContext.makePayment(securityContext, headers.getHeaderString("Tenant"), paymentId);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(PaymentDto.class);
    }
}
