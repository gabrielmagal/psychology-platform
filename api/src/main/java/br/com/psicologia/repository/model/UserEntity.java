package br.com.psicologia.repository.model;

import br.com.psicologia.repository.model.enums.UserType;
import core.repository.model.BaseEntity;
import core.repository.model.interfaces.ILabel;
import core.repository.model.interfaces.IShowInForm;
import core.repository.model.interfaces.IShowInTable;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class UserEntity extends BaseEntity {
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "name", nullable = false)
    private String name;

    @Email
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @OneToMany(mappedBy = "patient")
    private List<SessionEntity> sessionEntities = new ArrayList<>();

    @OneToMany(mappedBy = "psychologist")
    private List<SessionEntity> sessionsHeld = new ArrayList<>();
}
