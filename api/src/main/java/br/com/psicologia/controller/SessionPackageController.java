package br.com.psicologia.controller;

import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.controller.dto.SessionPackageDto;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.mapper.SessionPackageMapper;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import br.com.psicologia.service.SessionPackageService;
import core.controller.AbstractBaseController;
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

    private final SessionPackageService sessionPackageService;
    private final PaymentMapper paymentMapper;

    public SessionPackageController(SessionPackageService service, SessionPackageMapper mapper, PaymentMapper paymentMapper) {
        super(service, mapper);
        sessionPackageService = service;
        this.paymentMapper = paymentMapper;
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return service.describeEntity(SessionPackageDto.class);
    }

    @GET
    @Path("/{id}/total-paid")
    public Response getTotalPaidAmount(@PathParam("id") UUID id) {
        String tenant = headers.getHeaderString("Tenant");
        BigDecimal total = sessionPackageService.sumPaidAmountBySessionPackage(tenant, id);
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
        List<PaymentEntity> payments = sessionPackageService.findPaidBySessionPackage(tenant, sessionPackageId);

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
