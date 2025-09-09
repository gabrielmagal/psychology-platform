package core.service;

import core.notes.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.*;

public abstract class AbstractEntityDescriptionService {
    private final Set<Class<?>> visited = new HashSet<>();

    public Map<String, Object> describeEntity(Class<?> entityClass) {
        if (!visited.add(entityClass)) {
            return Map.of(
                    "type", entityClass.getSimpleName(),
                    "properties", Map.of()
            );
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            result.put("type", entityClass.getSimpleName());
            result.put("properties", properties);

            Set<String> ignoredFields = Set.of("serialVersionUID");

            for (Field field : getAllFields(entityClass)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;

                String name = field.getName();
                if (name.startsWith("$$") || ignoredFields.contains(name)) continue;

                if (Collection.class.isAssignableFrom(field.getType())) {
                    if (!field.isAnnotationPresent(OneToMany.class) && !field.isAnnotationPresent(IManageAsSubEntity.class)) continue;

                    var genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType parameterizedType) {
                        var actualType = parameterizedType.getActualTypeArguments()[0];
                        if (actualType instanceof Class<?> relatedClass) {
                            Map<String, Object> nestedMeta = describeEntity(relatedClass);
                            Map<String, Object> prop = new LinkedHashMap<>();
                            prop.put("type", "List");
                            prop.put("oneToMany", true);
                            prop.put("relatedType", decapitalize(relatedClass.getSimpleName().replace("Dto", "")));
                            prop.put("label", Optional.ofNullable(field.getAnnotation(ILabel.class)).map(ILabel::value).orElse(field.getName()));

                            IShowInForm showInForm = field.getAnnotation(IShowInForm.class);
                            boolean showInFormValue = showInForm == null || showInForm.value();
                            prop.put("showInForm", showInFormValue);

                            prop.put("metadata", nestedMeta);

                            if (field.isAnnotationPresent(IManageAsSubEntity.class)) {
                                prop.put("subEntity", true);
                                String path = "/" + decapitalize(field.getName());
                                prop.put("path", path);
                                String label = field.getAnnotation(IManageAsSubEntity.class).label();
                                if (!label.isBlank()) {
                                    prop.put("label", label);
                                }
                                String parentField = field.getAnnotation(IManageAsSubEntity.class).parentField();
                                if (!parentField.isBlank()) {
                                    prop.put("parentField", parentField);
                                }
                            }
                            properties.put(field.getName(), prop);
                        }
                    }
                    continue;
                }

                Map<String, Object> prop = new LinkedHashMap<>();
                Class<?> fieldType = field.getType();
                prop.put("type", fieldType.getSimpleName());

                if (fieldType.isEnum()) {
                    prop.put("enum", true);
                    Map<String, String> enumValues = new LinkedHashMap<>();
                    for (Object enumConst : fieldType.getEnumConstants()) {
                        String nameEnum = enumConst.toString();
                        try {
                            Field enumField = fieldType.getField(nameEnum);
                            ILabel label = enumField.getAnnotation(ILabel.class);
                            enumValues.put(nameEnum, label != null ? label.value() : nameEnum);
                        } catch (NoSuchFieldException e) {
                            enumValues.put(nameEnum, nameEnum);
                        }
                    }
                    prop.put("enumValues", enumValues);
                }

                boolean isDtoField = fieldType.getSimpleName().endsWith("Dto");
                String relatedType = Optional.ofNullable(field.getAnnotation(IRelatedType.class))
                        .map(IRelatedType::value)
                        .orElseGet(() -> isDtoField ? decapitalize(fieldType.getSimpleName().replace("Dto", "")) : null);

                if (field.isAnnotationPresent(OneToOne.class)) {
                    prop.put("oneToOne", true);
                    prop.put("relatedType", relatedType);
                    boolean required = true;
                    if (field.isAnnotationPresent(JoinColumn.class)) {
                        required = !field.getAnnotation(JoinColumn.class).nullable();
                    }
                    prop.put("required", required);
                } else if (field.isAnnotationPresent(ManyToOne.class)) {
                    prop.put("manyToOne", true);
                    prop.put("relatedType", relatedType);
                    prop.put("required", !field.getAnnotation(ManyToOne.class).optional());
                } else if (isDtoField) {
                    prop.put("oneToOne", true);
                    prop.put("relatedType", relatedType);
                    prop.put("required", false);
                }

                if (field.isAnnotationPresent(ILabel.class)) {
                    prop.put("label", field.getAnnotation(ILabel.class).value());
                }

                if (field.isAnnotationPresent(IShowInForm.class)) {
                    prop.put("showInForm", field.getAnnotation(IShowInForm.class).value());

                    if ((field.isAnnotationPresent(OneToOne.class) || isDtoField) && isDtoField) {
                        try {
                            Map<String, Object> nestedMeta = describeEntity(fieldType);
                            prop.put("metadata", nestedMeta);
                        } catch (Exception e) {
                            System.err.println("Erro ao carregar metadata para " + fieldType.getSimpleName());
                        }
                    }
                }

                if (field.isAnnotationPresent(IShowInTable.class)) {
                    prop.put("showInTable", field.getAnnotation(IShowInTable.class).value());
                }

                if (field.isAnnotationPresent(IShowField.class)) {
                    prop.put("showField", field.getAnnotation(IShowField.class).value());
                }

                if (field.isAnnotationPresent(IFile.class)) {
                    prop.put("file", true);
                    prop.put("base64", true);
                }

                if (field.isAnnotationPresent(IPhoto.class)) {
                    prop.put("photo", true);
                    prop.put("base64", true);
                }

                if (field.isAnnotationPresent(Lob.class)) {
                    prop.put("lob", true);
                    if (fieldType.equals(byte[].class)) {
                        prop.put("fileUpload", true);
                    }
                }

                if (field.isAnnotationPresent(Column.class)) {
                    Column col = field.getAnnotation(Column.class);
                    prop.put("nullable", col.nullable());
                    prop.put("unique", col.unique());
                    if (col.length() > 0) prop.put("maxLength", col.length());
                    prop.put("required", !col.nullable());
                }

                if (fieldType.equals(Instant.class)) {
                    prop.put("type", "LocalDateTime");
                }

                if (field.isAnnotationPresent(NotNull.class)) {
                    prop.put("required", true);
                }

                if (field.isAnnotationPresent(NotBlank.class)) {
                    prop.put("required", true);
                    prop.put("notBlank", true);
                }
                if (field.isAnnotationPresent(Size.class)) {
                    Size size = field.getAnnotation(Size.class);
                    prop.put("minLength", size.min());
                    prop.put("maxLength", size.max());
                }
                if (field.isAnnotationPresent(Email.class)) {
                    prop.put("email", true);
                }

                properties.put(name, prop);
            }

            return result;
        } finally {
            visited.remove(entityClass);
        }
    }

    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        while (type != null && !type.getName().equals("java.lang.Object")) {
            fields.addAll(Arrays.asList(type.getDeclaredFields()));
            type = type.getSuperclass();
        }
        return fields;
    }

    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }
}
