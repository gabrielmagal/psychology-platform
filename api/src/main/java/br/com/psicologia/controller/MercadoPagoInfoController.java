package br.com.psicologia.controller;

import br.com.psicologia.context.mercadopagoinfo.MercadoPagoInfoContext;
import br.com.psicologia.controller.dto.MercadoPagoInfoDto;
import br.com.psicologia.mapper.MercadoPagoInfoMapper;
import br.com.psicologia.repository.model.MercadoPagoInfoEntity;
import core.controller.AbstractBaseContextController;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Path("/mercado-pago-info")
public class MercadoPagoInfoController extends AbstractBaseContextController<MercadoPagoInfoDto, MercadoPagoInfoEntity> {

    private final MercadoPagoInfoContext context;

    public MercadoPagoInfoController(MercadoPagoInfoContext context, MercadoPagoInfoMapper mapper) {
        super(context, mapper);
        this.context = context;
    }

    @GET
    @Path("/payment-preference/{id}")
    public Response getTotalPaidAmount(@Context SecurityContext securityContext, @PathParam("id") UUID id) {
        String tenant = headers.getHeaderString("Tenant");
        return Response.ok(context.createPaymentPreference(securityContext, tenant, id)).build();
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(MercadoPagoInfoDto.class);
    }
}
