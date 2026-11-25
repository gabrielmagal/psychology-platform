package core.notes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint chamado por um sistema externo, sem usuário logado (ex.: aviso de pagamento).
 * O filtro de autorização não exige token nesses métodos — então o próprio método tem de
 * provar a origem da chamada por outro meio (um segredo compartilhado, uma assinatura).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IPublic {
}
