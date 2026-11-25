package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Alguém esperando uma vaga. Pode ainda não ser paciente: nome e telefone bastam. */
@Entity
@Table(name = "waitlist_entry")
@Getter
@Setter
@NoArgsConstructor
public class WaitlistEntryEntity extends BaseEntity {
    public static final String WAITING = "AGUARDANDO";
    public static final String CONTACTED = "CONTATADO";
    public static final String SCHEDULED = "AGENDADO";
    public static final String GAVE_UP = "DESISTIU";

    /** Profissional esperado. Vazio = qualquer profissional da clínica. */
    @Column(name = "professional_id")
    private UUID professionalId;

    /** Preenchido quando quem espera já tem cadastro. */
    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "phone", length = 50)
    private String phone;

    /** Dias e horários que servem: "terça ou quinta à tarde". */
    @Column(name = "preference")
    private String preference;

    @Column(name = "modality", length = 20)
    private String modality;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    private String status = WAITING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
