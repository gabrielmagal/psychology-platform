package br.com.psicologia.repository.model;

import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_care")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity extends BaseEntity {

    @Column(name = "date_session")
    private LocalDate dateSession;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    @Lob
    @Column(name = "private_notes")
    private String privateNotes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private UserEntity patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "psychologist_id")
    private UserEntity psychologist;

    @OneToMany(mappedBy = "session", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<AnnotationEntity> annotation = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "session_package_id")
    private SessionPackageEntity sessionPackage;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;
}
