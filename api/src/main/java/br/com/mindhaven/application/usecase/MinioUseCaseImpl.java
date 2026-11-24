package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.MinioUseCase;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

@ApplicationScoped
public class MinioUseCaseImpl implements MinioUseCase {

    private static final Logger LOG = Logger.getLogger(MinioUseCaseImpl.class);

    private volatile MinioClient client;

    @ConfigProperty(name = "minio.endpoint")
    String endpoint;

    @ConfigProperty(name = "minio.access-key")
    String accessKey;

    @ConfigProperty(name = "minio.secret-key")
    String secretKey;

    @ConfigProperty(name = "minio.bucket")
    String bucket;

    @ConfigProperty(name = "minio.secure")
    boolean secure;

    public String upload(String fileName, InputStream inputStream, long size, String contentType) throws Exception {
        MinioClient client = getClient();
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fileName)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build()
        );
        return endpoint + "/" + bucket + "/" + fileName;
    }

    public String uploadBase64(String fileName, String base64, String contentType) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64.replaceFirst("^data:[^,]+,", ""));
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            return upload(fileName, inputStream, imageBytes.length, contentType);
        }
    }

    public byte[] getObjectBytes(String fileName) {
        try {
            MinioClient client = getClient();
            try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(fileName).build());
                 var baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteObject(String fileName) {
        try {
            MinioClient client = getClient();
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(fileName).build());
        } catch (Exception _) {
        }
    }

    /**
     * O cliente era reconstruído a cada chamada. Cada {@link MinioClient} carrega um OkHttp
     * próprio, com pool de conexões e thread pool próprios — então listar uma página de
     * usuários (que busca a foto de cada um) criava e descartava um cliente HTTP por linha,
     * sem reaproveitar nenhuma conexão. Uma instância só, criada sob demanda, resolve.
     */
    private MinioClient getClient() {
        MinioClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = MinioClient.builder()
                            .endpoint(endpoint)
                            .credentials(accessKey, secretKey)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    @PreDestroy
    void close() {
        MinioClient local = client;
        if (local != null) {
            try {
                local.close();
            } catch (Exception e) {
                LOG.debug("Falha ao encerrar o cliente MinIO", e);
            }
        }
    }
}
