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

/** Modelo de texto do profissional para começar um documento já preenchido. */
@Entity
@Table(name = "document_template")
@Getter
@Setter
@NoArgsConstructor
public class DocumentTemplateEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "sections", columnDefinition = "TEXT")
    private String sections;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
