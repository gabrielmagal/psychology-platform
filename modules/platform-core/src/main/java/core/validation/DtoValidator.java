package core.validation;

import core.notes.ILabel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Confere, no servidor, as mesmas regras que o formulário mostra ao usuário.
 *
 * <p>As anotações já existiam nos DTOs, mas só eram lidas para desenhar a tela: uma chamada
 * direta à API passava com qualquer coisa e o erro só aparecia no banco, como "erro interno".
 * A mensagem cita os campos pelo rótulo da tela ({@link ILabel}).
 */
public final class DtoValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private DtoValidator() {
    }

    /** @return lista de problemas em linguagem de usuário; vazia quando está tudo certo */
    public static List<String> problems(Object dto) {
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        if (dto == null) return List.of("Nenhum dado foi enviado.");

        for (Class<?> type = dto.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(dto);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                String label = field.isAnnotationPresent(ILabel.class)
                        ? field.getAnnotation(ILabel.class).value() : field.getName();
                boolean empty = value == null || (value instanceof String text && text.isBlank());

                if (empty) {
                    if (field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class)) {
                        missing.add(label);
                    }
                    continue;
                }
                if (value instanceof String text) {
                    if (field.isAnnotationPresent(Email.class) && !EMAIL.matcher(text).matches()) {
                        invalid.add(label + " não é um e-mail válido");
                    }
                    Size size = field.getAnnotation(Size.class);
                    if (size != null && (text.length() < size.min() || text.length() > size.max())) {
                        invalid.add(label + " deve ter entre " + size.min() + " e " + size.max() + " caracteres");
                    }
                    if ("cpf".equals(field.getName()) && !text.matches("\\d{11}")) {
                        invalid.add(label + " deve ter 11 dígitos");
                    }
                }
                if (value instanceof Number number) {
                    Min min = field.getAnnotation(Min.class);
                    Max max = field.getAnnotation(Max.class);
                    if (min != null && number.longValue() < min.value()) invalid.add(label + " deve ser no mínimo " + min.value());
                    if (max != null && number.longValue() > max.value()) invalid.add(label + " deve ser no máximo " + max.value());
                }
            }
        }

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add((missing.size() == 1 ? "Preencha o campo " : "Preencha os campos ") + String.join(", ", missing) + ".");
        }
        for (String item : invalid) problems.add(item + ".");
        return problems;
    }
}
