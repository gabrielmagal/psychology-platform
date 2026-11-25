package br.com.mindhaven.domain.entity;

import core.audit.AuditListener;
import br.com.mindhaven.domain.entity.enums.UserType;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.user.BaseUserEntity;
import lombok.experimental.SuperBuilder;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_info")
// Permite ao Hibernate carregar os usuários referenciados por uma página inteira de
// pacotes/sessões em poucas consultas, em vez de uma por registro.
@BatchSize(size = 32)
@SuperBuilder
@EntityListeners(AuditListener.class)
// O que é de qualquer usuário (nome, CPF, contato, conta) está em BaseUserEntity, no módulo
// platform-user. Aqui fica só o que este sistema acrescenta.
public class UserEntity extends BaseUserEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;
}
