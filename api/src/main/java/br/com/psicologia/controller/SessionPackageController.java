package br.com.psicologia.controller;

import br.com.psicologia.context.sessionpackage.SessionPackageContext;
import br.com.psicologia.controller.dto.PaymentDto;
import br.com.psicologia.controller.dto.SessionPackageDto;
import br.com.psicologia.mapper.PaymentMapper;
import br.com.psicologia.mapper.SessionPackageMapper;
import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.SessionPackageEntity;
import core.context.IContext;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/session-package")
public class SessionPackageController extends AbstractBaseContextController<SessionPackageDto, SessionPackageEntity> {

    private final SessionPackageContext context;
    private final PaymentMapper paymentMapper;

    public SessionPackageController(SessionPackageContext context, SessionPackageMapper mapper, PaymentMapper paymentMapper) {
        super((IContext<SessionPackageEntity>) context, mapper);
        this.context = context;
        this.paymentMapper = paymentMapper;
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(SessionPackageDto.class);
    }

    @GET
    @Path("/{id}/total-paid")
    public Response getTotalPaidAmount(@Context SecurityContext securityContext, @PathParam("id") UUID id) {
        String tenant = headers.getHeaderString("Tenant");
        BigDecimal total = context.sumPaidAmountBySessionPackage(securityContext, tenant, id);
        return Response.ok(
                java.util.Map.of("sessionPackageId", id, "totalPaid", total)
        ).build();
    }

    @GET
    @Path("/{id}/payments")
    public Response listPayments(@Context SecurityContext securityContext, @PathParam("id") UUID sessionPackageId) {
        String tenant = headers.getHeaderString("Tenant");
        if (tenant == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Tenant é obrigatório.")
                    .build();
        }
        List<PaymentEntity> payments = context.findPaidBySessionPackage(securityContext, tenant, sessionPackageId);

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
