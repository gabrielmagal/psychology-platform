package br.com.mindhaven.domain.entity;

import core.repository.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Documento emitido (recibo, declaração, contrato): o PDF exatamente como foi entregue,
 * com numeração por tipo e ano. Não é regerado — o que foi emitido fica guardado.
 */
@Entity
@Table(name = "issued_document")
@Getter
@Setter
@NoArgsConstructor
public class IssuedDocumentEntity extends BaseEntity {
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(name = "doc_year", nullable = false)
    private int docYear;

    @Column(name = "doc_number", nullable = false)
    private int docNumber;

    @Column(name = "title")
    private String title;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "professional_id")
    private UUID professionalId;

    /** O pagamento, a sessão ou o paciente a que o documento se refere. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false)
    private byte[] content;
}
