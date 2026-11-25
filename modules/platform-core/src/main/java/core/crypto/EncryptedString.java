package core.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra um campo de texto no banco (AES-256-GCM). Use com {@code @Convert(converter = EncryptedString.class)}.
 *
 * <p>Quem lê o banco diretamente — um backup vazado, um acesso indevido ao servidor de banco
 * — vê só texto cifrado. A chave vem de {@code app.crypto.key} (variável {@code APP_CRYPTO_KEY})
 * e <b>não</b> fica no banco.
 *
 * <p>Valores gravados antes da cifragem continuam legíveis: o que não começa com o prefixo é
 * devolvido como está e passa a ser cifrado na próxima gravação. Consequência assumida:
 * campo cifrado não pode ser usado em busca nem em filtro no banco.
 */
@Converter
public class EncryptedString implements AttributeConverter<String, String> {
    private static final Logger LOG = Logger.getLogger(EncryptedString.class);
    private static final String PREFIX = "enc:v1:";
    private static final String DEV_KEY = "chave-apenas-para-desenvolvimento";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;

    @Override
    public String convertToDatabaseColumn(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(out.array());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar campo protegido", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Chave trocada ou dado corrompido: falhar é melhor que devolver lixo como se fosse o texto.
            throw new IllegalStateException("Falha ao decifrar campo protegido. A chave app.crypto.key mudou?", e);
        }
    }

    private static SecretKeySpec key() throws Exception {
        SecretKeySpec current = key;
        if (current == null) {
            synchronized (EncryptedString.class) {
                if (key == null) {
                    String configured = ConfigProvider.getConfig()
                            .getOptionalValue("app.crypto.key", String.class).orElse(DEV_KEY);
                    if (DEV_KEY.equals(configured)) {
                        LOG.warn("app.crypto.key não foi definida: usando a chave de desenvolvimento. "
                                + "Defina APP_CRYPTO_KEY antes de guardar dados reais.");
                    }
                    // Qualquer texto vira uma chave de 256 bits; assim a variável pode ser uma frase longa.
                    key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                            .digest(configured.getBytes(StandardCharsets.UTF_8)), "AES");
                }
                current = key;
            }
        }
        return current;
    }
}
