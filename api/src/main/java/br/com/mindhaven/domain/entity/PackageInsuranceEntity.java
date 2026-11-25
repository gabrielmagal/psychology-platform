package br.com.mindhaven.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Pacote atendido por convênio. Fica fora de {@link SessionPackageEntity} de propósito: o
 * formulário de pacotes grava o pacote inteiro e apagaria o vínculo a cada edição.
 */
@Entity
@Table(name = "package_insurance")
@Getter
@Setter
@NoArgsConstructor
public class PackageInsuranceEntity {
    @Id
    @Column(name = "package_id")
    private UUID packageId;

    @Column(name = "insurance_id", nullable = false)
    private UUID insuranceId;

    /** Número da carteirinha do paciente no convênio. */
    @Column(name = "card_number", length = 60)
    private String cardNumber;
}
