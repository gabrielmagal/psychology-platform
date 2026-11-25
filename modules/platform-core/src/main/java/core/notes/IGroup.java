package core.notes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Bloco do formulário em que o campo aparece ("Contato", "Emergência"...). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IGroup {
    String value();
}
