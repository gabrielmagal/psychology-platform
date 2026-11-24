package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "session_package")
@BatchSize(size = 32)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id"
)
public class SessionPackageEntity extends BaseEntity {

    @Column(name = "psychologist_id")
    private UUID psychologistId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    // @BatchSize permite ao Hibernate resolver as associações de uma página inteira em
    // poucas consultas (IN (...)) em vez de uma consulta por registro listado.
    @OneToMany(mappedBy = "sessionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private Set<SessionEntity> session = new HashSet<>();

    @OneToMany(mappedBy = "sessionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private Set<PaymentEntity> payment = new HashSet<>();

    @Column(name = "package_title")
    private String packageTitle;

    @Column(name = "total_sessions")
    private Integer totalSessions;
}