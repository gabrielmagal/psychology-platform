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

/** Instrumento (escala ou formulário) criado pelo profissional. A definição é um JSON. */
@Entity
@Table(name = "form_template")
@Getter
@Setter
@NoArgsConstructor
public class FormTemplateEntity extends BaseEntity {
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
