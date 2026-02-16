package br.com.psicologia.domain.entity;

import br.com.psicologia.infrastructure.interceptor.AuditListener;
import core.repository.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mercado_pago_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditListener.class)
public class MercadoPagoInfoEntity extends BaseEntity {

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "id")
    private UserEntity user;

    @Column(name = "access_token", nullable = false, length = 255)
    private String accessToken;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    @Column(name = "scope")
    private String scope;

    @Column(name = "expires_in")
    private Integer expiresIn;

    @Column(name = "token_created_at")
    private Instant tokenCreatedAt;
}
