package br.com.psicologia.controller;

import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.service.PaymentService;
import core.controller.AbstractBaseController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/payment")
public class PaymentController extends AbstractBaseController<PaymentDto, PaymentEntity> {
    public PaymentController(PaymentService service, PaymentMapper mapper) {
        super(service, mapper);
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return service.describeEntity(PaymentDto.class);
    }
}
