package br.com.psicologia.adapter.controller;

import br.com.psicologia.application.service.MercadoPagoInfoService;
import br.com.psicologia.adapter.controller.dto.MercadoPagoInfoDto;
import br.com.psicologia.mapper.BaseMapper;
import br.com.psicologia.domain.entity.MercadoPagoInfoEntity;
import core.controller.AbstractBaseController;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/mercado-pago-info")
public class MercadoPagoInfoController extends AbstractBaseController<MercadoPagoInfoDto, MercadoPagoInfoEntity> {

    private final MercadoPagoInfoService mercadoPagoInfoService;

    @Inject
    public MercadoPagoInfoController(MercadoPagoInfoService service, BaseMapper<MercadoPagoInfoDto, MercadoPagoInfoEntity> mapper) {
        super(service, mapper);
        this.mercadoPagoInfoService = service;
    }

    @GET
    @Path("/payment-preference/{id}")
    public Response getTotalPaidAmount( @PathParam("id") UUID id) {
        return Response.ok(mercadoPagoInfoService.createPaymentPreference(id)).build();
    }

    @GET
    @Path("/entity-description")
    public Map<String, Object> entityDescription() {
        return describeEntity(MercadoPagoInfoDto.class);
    }
}
