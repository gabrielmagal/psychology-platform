package br.com.mindhaven.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Página de apresentação de um profissional: o que ele escolhe mostrar a quem ainda não o
 * conhece. Nome, foto e registro vêm do cadastro; aqui fica só o que é do perfil.
 */
@Entity
@Table(name = "public_profile")
@Getter
@Setter
@NoArgsConstructor
public class PublicProfileEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Uma linha: "Psicóloga clínica · Terapia cognitivo-comportamental". */
    @Column(name = "headline", length = 160)
    private String headline;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    // Listas guardadas uma por linha: são curtas, lidas sempre juntas e nunca filtradas no banco.
    @Column(name = "approaches", columnDefinition = "TEXT")
    private String approaches;

    @Column(name = "specialties", columnDefinition = "TEXT")
    private String specialties;

    @Column(name = "audiences", columnDefinition = "TEXT")
    private String audiences;

    @Column(name = "languages", columnDefinition = "TEXT")
    private String languages;

    @Column(name = "education", columnDefinition = "TEXT")
    private String education;

    /** Ano em que começou a atuar. */
    @Column(name = "practicing_since")
    private Integer practicingSince;

    @Column(name = "session_price")
    private BigDecimal sessionPrice;

    @Column(name = "session_minutes")
    private Integer sessionMinutes;

    @Column(name = "attends_online")
    private boolean attendsOnline;

    @Column(name = "attends_in_person")
    private boolean attendsInPerson;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "accepting_clients")
    private boolean acceptingClients = true;

    /** Mostrar nota e comentários recebidos. É escolha do profissional, desligada por padrão. */
    @Column(name = "show_reviews")
    private boolean showReviews;

    /** Enquanto não publica, só o dono e a administração veem o perfil. */
    @Column(name = "published")
    private boolean published;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
