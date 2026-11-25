package core.error;

/**
 * Erro de regra de negócio cuja mensagem foi escrita para o usuário final. Vira resposta
 * HTTP com o status indicado (ver {@code BusinessExceptionMappers}).
 */
public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** Entrada inválida (400). */
    public static BusinessException invalid(String message) {
        return new BusinessException(400, message);
    }

    /** A operação conflita com o estado atual, como um horário já ocupado (409). */
    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    public int getStatus() {
        return status;
    }
}
