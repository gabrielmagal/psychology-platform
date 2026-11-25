package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
import br.com.mindhaven.domain.entity.PaymentEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.payment.PayableSource;
import br.com.mindhaven.payment.PaymentGateway.Payer;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Liga o módulo de pagamento a este sistema: aqui, o que se paga online é um
 * <b>pacote de sessões</b>. É o único lugar que conhece os dois lados.
 */
@ApplicationScoped
@Transactional
public class SessionPackagePayments implements PayableSource {
    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    SessionPackageUseCase packages;

    @Override
    public Payable load(String tenant, UserEntity user, UUID payableId) {
        // Mesma regra da tela de pacotes: admin, profissional dono ou o próprio paciente.
        return toPayable(packages.findById(tenant, user, payableId));
    }

    @Override
    public Payable loadForSystem(String tenant, UUID payableId) {
        dao.defineSchema(tenant);
        return toPayable(em.find(SessionPackageEntity.class, payableId));
    }

    /** O provedor confirmou: registra o pagamento do pacote, que passa a contar no financeiro. */
    void onChargePaid(@Observes ChargePaid event) {
        dao.defineSchema(event.tenant());
        SessionPackageEntity pkg = em.find(SessionPackageEntity.class, event.payableId());
        if (pkg == null) return;

        PaymentEntity payment = new PaymentEntity();
        payment.setSessionPackage(pkg);
        payment.setAmount(event.amount());
        payment.setPaid(true);
        payment.setPaymentDate(event.paidOn());
        payment.setPaymentMethod(methodOf(event.billingType()));
        payment.setPaymentId(event.chargeId().toString());
        payment.setObservation("Pagamento online");
        em.persist(payment);
        em.flush();
    }

    private Payable toPayable(SessionPackageEntity pkg) {
        if (pkg == null) return null;
        BigDecimal paid = em.createQuery("select coalesce(sum(p.amount), 0) from PaymentEntity p "
                        + "where p.sessionPackage.id = :pkg and p.paid = true", BigDecimal.class)
                .setParameter("pkg", pkg.getId()).getSingleResult();
        UserEntity patient = pkg.getPatient();
        String title = pkg.getPackageTitle() == null || pkg.getPackageTitle().isBlank()
                ? "Pacote de sessões" : pkg.getPackageTitle();
        return new Payable(
                pkg.getId(),
                title,
                pkg.getPrice() == null ? null : pkg.getPrice().subtract(paid),
                pkg.getProfessionalId(),
                new Payer((patient.getFirstName() + " " + patient.getLastName()).trim(),
                        patient.getCpf(), patient.getEmail(), patient.getPhoneNumber()));
    }

    private static PaymentMethod methodOf(String billingType) {
        if (billingType == null) return PaymentMethod.OTHER;
        return switch (billingType) {
            case "PIX" -> PaymentMethod.PIX;
            case "BOLETO" -> PaymentMethod.BOLETO;
            case "CREDIT_CARD", "DEBIT_CARD" -> PaymentMethod.CARD;
            case "TRANSFER", "DEPOSIT" -> PaymentMethod.TRANSFER;
            default -> PaymentMethod.OTHER;
        };
    }
}
