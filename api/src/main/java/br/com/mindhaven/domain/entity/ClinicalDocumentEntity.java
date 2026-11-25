package br.com.mindhaven.domain.entity;

import core.crypto.EncryptedString;
import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Documento escrito pelo psicólogo (atestado, relatório, laudo, parecer): rascunho até a emissão. */
@Entity
@Table(name = "clinical_document")
@Getter
@Setter
@NoArgsConstructor
public class ClinicalDocumentEntity extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Convert(converter = EncryptedString.class)
    @Column(name = "sections", columnDefinition = "TEXT")
    private String sections;

    @Column(name = "issued_document_id")
    private UUID issuedDocumentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
