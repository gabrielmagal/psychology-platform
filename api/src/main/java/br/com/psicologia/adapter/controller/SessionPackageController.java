package br.com.psicologia.adapter.controller;

import br.com.psicologia.application.service.PaymentService;
import br.com.psicologia.application.service.SessionPackageService;
import br.com.psicologia.adapter.controller.dto.PaymentDto;
import br.com.psicologia.adapter.controller.dto.SessionPackageDto;
import br.com.psicologia.mapper.BaseMapper;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.domain.entity.PaymentEntity;
import br.com.psicologia.domain.entity.SessionPackageEntity;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/session-package")
public class SessionPackageController extends AbstractBaseController<SessionPackageDto, SessionPackageEntity> {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @Inject
    public SessionPackageController(SessionPackageService service, BaseMapper<SessionPackageDto, SessionPackageEntity> mapper,
                                    PaymentService paymentService, PaymentMapper paymentMapper) {
        super(service, mapper);
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(SessionPackageDto.class);
    }

    @GET
    @Path("/{id}/total-paid")
    public Response getTotalPaidAmount(@PathParam("id") UUID id) {
        String tenant = headers.getHeaderString("Tenant");
        BigDecimal total = paymentService.sumPaidAmountBySessionPackage(id);
        return Response.ok(
                java.util.Map.of("sessionPackageId", id, "totalPaid", total)
        ).build();
    }

    @GET
    @Path("/{id}/payments")
    public Response listPayments(@PathParam("id") UUID sessionPackageId) {
        String tenant = headers.getHeaderString("Tenant");
        if (tenant == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Tenant é obrigatório.")
                    .build();
        }
        List<PaymentEntity> payments = paymentService.findPaidBySessionPackage(sessionPackageId);

        List<PaymentDto> paymentDtos = payments.stream()
                .map(paymentMapper::toDto)
                .toList();

        BigDecimal total = payments.stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Response.ok(
                java.util.Map.of(
                        "sessionPackageId", sessionPackageId,
                        "totalPaid", total,
                        "payments", paymentDtos
                )
        ).build();
    }
}
