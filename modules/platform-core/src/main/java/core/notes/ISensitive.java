package core.notes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um campo cujo <b>conteúdo</b> não deve ser copiado para a trilha de auditoria.
 *
 * <p>A auditoria serializava a entidade inteira em {@code audit_log.new_value}. Na prática
 * isso duplicava, em texto puro e numa tabela sem controle de acesso próprio, exatamente o
 * que o sistema tem de mais sensível: anotações clínicas de pacientes e os tokens de acesso
 * do Mercado Pago. Um vazamento do {@code audit_log} passava a valer tanto quanto um
 * vazamento das tabelas de origem — e uma exclusão de dados a pedido do titular não
 * alcançava as cópias.
 *
 * <p>Campos anotados continuam sendo auditados: o registro guarda uma impressão digital
 * ({@code sha256:…}) em vez do valor. Dá para provar que o campo mudou, e quando, sem
 * guardar o que ele dizia.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ISensitive {
}
