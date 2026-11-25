package br.com.mindhaven.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Foto de perfil do usuário, guardada no banco. */
@Entity
@Table(name = "user_photo")
@Getter
@Setter
@NoArgsConstructor
public class UserPhotoEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
