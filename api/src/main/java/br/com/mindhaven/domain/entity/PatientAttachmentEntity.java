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

/** Arquivo anexado ao prontuário (laudo, encaminhamento), guardado no próprio banco. */
@Entity
@Table(name = "patient_attachment")
@Getter
@Setter
@NoArgsConstructor
public class PatientAttachmentEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @jakarta.persistence.Basic(fetch = jakarta.persistence.FetchType.LAZY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "description")
    private String description;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
}
