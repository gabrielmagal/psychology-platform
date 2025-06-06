package br.com.psicologia.repository.model;

import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_package")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionPackageEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "psychologist_id")
    private UserEntity psychologist;

    @OneToMany(mappedBy = "sessionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SessionEntity> session = new ArrayList<>();

    @OneToMany(mappedBy = "sessionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentEntity> payment = new ArrayList<>();

    @Column(name = "package_title")
    private String packageTitle;

    @Column(name = "total_sessions")
    private Integer totalSessions;
}