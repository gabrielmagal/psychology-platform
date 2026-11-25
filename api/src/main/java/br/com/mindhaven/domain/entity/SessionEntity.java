package br.com.mindhaven.domain.entity;

import core.audit.AuditListener;
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

    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // Anotação clínica privada: auditada como impressão digital, nunca copiada em claro.
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
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

    /** Evolução da sessão em texto corrido — o registro clínico do atendimento. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "evolution", columnDefinition = "TEXT")
    private String evolution;

    // Registro clínico estruturado (Resolução CFP 001/2009). Ver SessionRecord.
    /** O que o paciente trouxe: a demanda e o relato da sessão. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "demand", columnDefinition = "TEXT")
    private String demand;

    /** O que o profissional observou: estado emocional, comportamento, humor. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    /** Procedimentos e técnicas utilizados. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "interventions", columnDefinition = "TEXT")
    private String interventions;

    /** Encaminhamentos, combinados e foco da próxima sessão. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "plan", columnDefinition = "TEXT")
    private String plan;

    /** Complementos datados, feitos depois que o registro foi concluído. */
    @ISensitive
    @Convert(converter = core.crypto.EncryptedString.class)
    @Column(name = "addenda", columnDefinition = "TEXT")
    private String addenda;

    /** Sinal de atenção: algo nesta sessão pede acompanhamento de perto. */
    @Column(name = "risk_alert")
    private Boolean riskAlert;

    /** Quando o registro foi concluído. Depois disso ele só recebe adendos. */
    @Column(name = "record_signed_at")
    private LocalDateTime recordSignedAt;

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

    /** Quando o profissional enviou o lembrete da sessão. */
    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    /** Sessões criadas juntas, numa repetição, têm o mesmo identificador de série. */
    @Column(name = "series_id")
    private java.util.UUID seriesId;

    // Convênio: guia autorizada, valor a receber e as datas do faturamento e do repasse.
    @Column(name = "insurance_guide", length = 60)
    private String insuranceGuide;

    @Column(name = "insurance_amount")
    private java.math.BigDecimal insuranceAmount;

    @Column(name = "insurance_billed_at")
    private LocalDate insuranceBilledAt;

    @Column(name = "insurance_paid_at")
    private LocalDate insurancePaidAt;

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
