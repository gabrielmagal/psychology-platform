package br.com.mindhaven.domain.entity;

import br.com.mindhaven.infrastructure.interceptor.AuditListener;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import core.notes.ISensitive;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_info")
@BatchSize(size = 32)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
public class SessionEntity extends BaseEntity {

    @Column(name = "date_session")
    private LocalDate dateSession;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    // Anotação clínica privada: auditada como impressão digital, nunca copiada em claro.
    @ISensitive
    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes;

    @OneToMany(mappedBy = "session", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @BatchSize(size = 32)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private List<AnnotationEntity> annotation = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_package_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private SessionPackageEntity sessionPackage;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private br.com.mindhaven.domain.entity.enums.SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality")
    private br.com.mindhaven.domain.entity.enums.SessionModality modality;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    /** Quando o paciente confirmou que vai comparecer. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** Quando o psicólogo enviou o lembrete da sessão. */
    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    /** Registros antigos (e clientes que não enviam os campos novos) caem nos padrões. */
    @PrePersist
    @PreUpdate
    void applyDefaults() {
        if (status == null) {
            status = attendedAt != null
                    ? br.com.mindhaven.domain.entity.enums.SessionStatus.REALIZADA
                    : br.com.mindhaven.domain.entity.enums.SessionStatus.AGENDADA;
        }
        if (modality == null) modality = br.com.mindhaven.domain.entity.enums.SessionModality.PRESENCIAL;
        if (durationMinutes == null) durationMinutes = 50;
    }
}
