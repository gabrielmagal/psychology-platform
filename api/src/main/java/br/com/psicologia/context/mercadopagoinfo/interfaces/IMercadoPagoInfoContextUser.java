package br.com.psicologia.context.mercadopagoinfo.interfaces;

import br.com.psicologia.repository.model.MercadoPagoInfoEntity;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import core.context.IContextUser;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

public interface IMercadoPagoInfoContextUser extends IContextUser<MercadoPagoInfoEntity> {
    String createPaymentPreference(SecurityContext context, String tenant, UUID packageId);
}
