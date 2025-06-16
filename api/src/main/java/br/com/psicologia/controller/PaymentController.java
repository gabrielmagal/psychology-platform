package br.com.psicologia.controller;

import br.com.psicologia.context.payment.PaymentContext;
import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.repository.model.PaymentEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/payment")
public class PaymentController extends AbstractBaseContextController<PaymentDto, PaymentEntity> {
    public PaymentController(PaymentContext context, PaymentMapper mapper) {
        super(context, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(PaymentDto.class);
    }
}
