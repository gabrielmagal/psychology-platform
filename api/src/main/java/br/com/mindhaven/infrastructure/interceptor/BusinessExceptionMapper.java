package br.com.mindhaven.infrastructure.interceptor;

import br.com.mindhaven.application.BusinessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

/**
 * Traduz as exceções de regra de negócio em respostas HTTP com a mensagem.
 *
 * <p>Sem isso, um {@code SecurityException("Psicólogo só pode...")} ou um conflito de
 * horário chegavam ao navegador como 500 genérico, e a mensagem escrita para o usuário se
 * perdia. Só estes dois tipos são traduzidos: qualquer outra exceção continua sendo um 500
 * registrado em log, para que defeito de verdade não se disfarce de erro de validação.
 */
public class BusinessExceptionMapper {
    @ServerExceptionMapper
    public Response forbidden(SecurityException exception) {
        return respond(403, exception.getMessage());
    }

    @ServerExceptionMapper
    public Response business(BusinessException exception) {
        return respond(exception.getStatus(), exception.getMessage());
    }

    private static Response respond(int status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", message == null ? "Operação não permitida." : message))
                .build();
    }
}
