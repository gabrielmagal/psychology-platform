package br.com.mindhaven.application.service;

import core.error.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Salas de vídeo do atendimento online.
 *
 * <p>A sala é só um endereço num servidor Jitsi Meet (o público, por padrão, ou o da própria
 * clínica): nada é criado lá de antemão. O nome é sorteado e longo, para que ninguém chegue
 * à sala por adivinhação. O profissional pode trocar pelo link da ferramenta que preferir.
 */
@ApplicationScoped
public class MeetingRooms {
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Endereço do servidor de vídeo. Vazio desliga a sala automática. */
    @ConfigProperty(name = "clinic.video.base-url")
    Optional<String> baseUrl;

    @ConfigProperty(name = "clinic.video.room-prefix", defaultValue = "MindHaven")
    String roomPrefix;

    /** O link informado, validado; ou uma sala nova, se nenhum foi informado. */
    public String linkOrNew(String informed) {
        String link = clean(informed);
        return link != null ? link : newRoom();
    }

    /** Sala nova, ou {@code null} se a sala automática está desligada. */
    public String newRoom() {
        String base = baseUrl.map(String::trim).orElse("");
        if (base.isEmpty()) return null;
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return base.replaceAll("/+$", "") + "/" + roomPrefix + "-" + HexFormat.of().formatHex(bytes);
    }

    /**
     * Só aceita endereço http(s): o link vira um botão na tela do paciente, e qualquer outro
     * esquema (como {@code javascript:}) executaria código no navegador dele.
     */
    public static String clean(String link) {
        if (link == null || link.isBlank()) return null;
        String value = link.trim();
        String lower = value.toLowerCase();
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw BusinessException.invalid("O link do atendimento deve começar com https://");
        }
        if (value.length() > 500) throw BusinessException.invalid("O link do atendimento é longo demais.");
        return value;
    }
}
