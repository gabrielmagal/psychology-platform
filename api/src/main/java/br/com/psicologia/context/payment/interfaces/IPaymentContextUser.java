package br.com.psicologia.context.payment.interfaces;

import br.com.psicologia.repository.model.PaymentEntity;
import br.com.psicologia.repository.model.UserEntity;
import core.context.IContextUser;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface IPaymentContextUser extends IContextUser<PaymentEntity> {
    List<PaymentEntity> findPaidBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId);
    BigDecimal sumPaidAmountBySessionPackage(SecurityContext securityContext, String tenant, UserEntity loggedUser, UUID sessionPackageId);
    void makePayment(SecurityContext securityContext, String tenant, UserEntity loggedUser, String paymentId);
}
