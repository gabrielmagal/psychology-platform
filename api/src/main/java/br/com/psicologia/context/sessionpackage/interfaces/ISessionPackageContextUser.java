package br.com.psicologia.context.sessionpackage.interfaces;

import br.com.psicologia.model.PaymentEntity;
import br.com.psicologia.model.SessionPackageEntity;
import core.context.IContextUser;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ISessionPackageContextUser extends IContextUser<SessionPackageEntity> {
    List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId);
    BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UUID sessionPackageId);
}
