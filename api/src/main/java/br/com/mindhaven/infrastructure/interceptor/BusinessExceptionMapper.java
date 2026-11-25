package br.com.mindhaven.infrastructure.interceptor;

import core.error.BusinessException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Traduz exceções em respostas que a tela consegue explicar ao usuário.
 *
 * <ul>
 *   <li>Regra de negócio e permissão: o status certo e a mensagem escrita para o usuário.</li>
 *   <li>Restrição do banco (CPF repetido, registro em uso): 409 dizendo qual é o conflito —
 *       antes isso chegava como "erro interno" sem pista nenhuma.</li>
 *   <li>Qualquer outra coisa: 500 com um código. O detalhe técnico vai só para o log, junto
 *       do mesmo código, para o suporte localizar.</li>
 * </ul>
 */
public class BusinessExceptionMapper {
    private static final Logger LOG = Logger.getLogger(BusinessExceptionMapper.class);

    @ServerExceptionMapper
    public Response forbidden(SecurityException exception) {
        return respond(403, exception.getMessage(), null);
    }

    @ServerExceptionMapper
    public Response business(BusinessException exception) {
        return respond(exception.getStatus(), exception.getMessage(), null);
    }

    @ServerExceptionMapper
    public Response unexpected(RuntimeException exception) {
        if (exception instanceof WebApplicationException web) {
            return web.getResponse();
        }
        // Restrições do banco costumam vir embrulhadas (rollback, proxy do CDI): procura na cadeia.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return respond(409, describe(violation), null);
            }
            if (cause.getCause() == cause) break;
        }
        String errorId = UUID.randomUUID().toString();
        LOG.errorf(exception, "Erro não tratado (código %s)", errorId);
        return respond(500, "Erro interno.", errorId);
    }

    /** Explica a restrição violada em termos do cadastro, não do banco. */
    private static String describe(ConstraintViolationException violation) {
        String constraint = violation.getConstraintName() == null ? "" : violation.getConstraintName().toLowerCase(Locale.ROOT);
        String sql = violation.getSQLException() == null || violation.getSQLException().getMessage() == null
                ? "" : violation.getSQLException().getMessage().toLowerCase(Locale.ROOT);
        String where = constraint + " " + sql;

        if (where.contains("cpf")) return "Já existe um cadastro com este CPF.";
        if (where.contains("phone_number")) return "Já existe um cadastro com este telefone.";
        if (where.contains("keycloak_id")) return "Este usuário já está cadastrado.";
        if (where.contains("not-null") || where.contains("not null") || where.contains("null value")) {
            return "Há um campo obrigatório sem preenchimento. Revise o formulário e tente de novo.";
        }
        if (where.contains("foreign key") || where.contains("fkey") || where.contains("violates foreign")) {
            return "Este registro está sendo usado por outros (pacotes, sessões ou pagamentos) e não pode ser "
                    + "excluído. Remova primeiro o que depende dele.";
        }
        if (where.contains("unique") || where.contains("duplicate")) {
            return "Já existe um registro com esses mesmos dados.";
        }
        return "Os dados informados conflitam com um registro existente.";
    }

    private static Response respond(int status, String message, String errorId) {
        Map<String, Object> body = errorId == null
                ? Map.of("message", message == null ? "Operação não permitida." : message)
                : Map.of("message", message, "errorId", errorId);
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
