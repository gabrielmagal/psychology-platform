package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import br.com.mindhaven.domain.entity.enums.UserType;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_info")
// Permite ao Hibernate carregar os usuários referenciados por uma página inteira de
// pacotes/sessões em poucas consultas, em vez de uma por registro.
@BatchSize(size = 32)
@Builder
@EntityListeners(AuditListener.class)
public class UserEntity extends BaseEntity {

    @Column(name = "registered_by_keycloak_id")
    private String registeredByKeycloakId;

    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Email
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** Registro no Conselho Regional de Psicologia — sai nos recibos e declarações. */
    @Column(name = "crp", length = 30)
    private String crp;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "occupation")
    private String occupation;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    /** Responsável legal, para paciente menor de idade. */
    @Column(name = "guardian_name")
    private String guardianName;

    @Column(name = "guardian_phone", length = 50)
    private String guardianPhone;

    @Transient
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @OneToOne(mappedBy = "user", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private MercadoPagoInfoEntity mercadoPagoInfo;
}
