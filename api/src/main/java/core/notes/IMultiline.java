package core.notes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Campo de texto longo: o formulário mostra uma caixa de várias linhas. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IMultiline {
    int rows() default 5;
}
