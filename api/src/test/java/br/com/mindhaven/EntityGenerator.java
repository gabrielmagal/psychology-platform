package br.com.mindhaven;

import br.com.mindhaven.domain.entity.*;
import br.com.mindhaven.domain.entity.enums.PaymentMethod;
import br.com.mindhaven.domain.entity.enums.UserType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class EntityGenerator {
    public AnnotationEntity getAnnotationEntity() {
        AnnotationEntity annotationEntity = AnnotationEntity.builder()
                .mainFeeling("Sentimento principal")
                .significantEvents("Eventos significativos")
                .currentPhase("Fase atual")
                .dominantThought("Pensamento dominante")
                .intervention("Intervenção")
                .build();
        annotationEntity.setId(UUID.randomUUID());
        return annotationEntity;
    }

    public MercadoPagoInfoEntity getMercadoPagoInfoEntity() {
        MercadoPagoInfoEntity mercadoPagoInfoEntity = MercadoPagoInfoEntity.builder()
                .accessToken("token")
                .refreshToken("refresh")
                .scope("scope")
                .expiresIn(Integer.MAX_VALUE)
                .tokenCreatedAt(java.time.Instant.now())
                .build();
        mercadoPagoInfoEntity.setId(UUID.randomUUID());
        return mercadoPagoInfoEntity;
    }

    public UserEntity getUserEntity() {
        UserEntity userEntity = UserEntity.builder()
                .registeredByKeycloakId(UUID.randomUUID().toString())
                .keycloakId(UUID.randomUUID().toString())
                .cpf("16941036761")
                .firstName("Gabriel")
                .lastName("Almeida")
                .email("gabrielalmeida@gmail.com")
                .phoneNumber("41987654321")
                .birthDate(LocalDate.now())
                .profileImage("teste.png")
                .userType(UserType.ADMINISTRADOR)
                .build();
        userEntity.setId(UUID.randomUUID());
        return userEntity;
    }

    public PaymentEntity getPaymentEntity() {
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentId(UUID.randomUUID().toString())
                .amount(BigDecimal.valueOf(100.00))
                .paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.PIX)
                .paid(true)
                .observation("Campo de observação")
                .receipt("Recibo de pagamento")
                .receiptName("recibo.png")
                .receiptType("image/png")
                .build();
        paymentEntity.setId(UUID.randomUUID());
        return paymentEntity;
    }

    public SessionEntity getSessionEntity() {
        SessionEntity sessionEntity = SessionEntity.builder()
                .dateSession(LocalDate.now())
                .title("Título da sessão")
                .summary("Resumo da sessão")
                .privateNotes("Notas privadas da sessão")
                .attendedAt(LocalDateTime.now())
                .build();
        sessionEntity.setId(UUID.randomUUID());
        return sessionEntity;
    }

    public SessionPackageEntity getSessionPackageEntity() {
        SessionPackageEntity sessionPackageEntity = SessionPackageEntity.builder()
                .psychologistId(UUID.randomUUID())
                .packageTitle("Pacote de sessões")
                .totalSessions(10)
                .build();
        sessionPackageEntity.setId(UUID.randomUUID());
        return sessionPackageEntity;
    }
}
