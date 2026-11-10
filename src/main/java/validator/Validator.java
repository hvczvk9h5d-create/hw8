package validator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class Validator {

    private Validator() {
    }

    public static ValidationResult validate(Object object) {
        if (object == null) {
            throw new NullPointerException("Проверяемый объект не может быть null");
        }

        List<ValidationError> errors = new ArrayList<>();

        for (Class<?> clazz = object.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                if (!field.trySetAccessible()) {
                    throw new ValidationConfigurationException("Не удалось получить доступ к полю: " + field.getName() + " в классе " + clazz.getName());
                }

                Object value;
                try {
                    value = field.get(object);
                } catch (IllegalAccessException e) {
                    throw new ValidationConfigurationException("Ошибка при чтении поля: " + field.getName(), e);
                }

                processNotNull(field, value, errors);
                processSize(field, value, errors);
                processRange(field, value, errors);
                processEmail(field, value, errors);
            }
        }

        return new ValidationResult(errors);
    }

    private static void processNotNull(Field field, Object value, List<ValidationError> errors) {
        NotNull annotation = field.getAnnotation(NotNull.class);
        if (annotation != null && value == null) {
            errors.add(new ValidationError(field.getName(), "NotNull", annotation.message()));
        }
    }

    private static void processSize(Field field, Object value, List<ValidationError> errors) {
        Size annotation = field.getAnnotation(Size.class);
        if (annotation == null) return;

        if (!CharSequence.class.isAssignableFrom(field.getType())) {
            throw new ValidationConfigurationException("@Size можно применять только к CharSequence. Поле: " + field.getName());
        }
        if (annotation.min() < 0 || annotation.min() > annotation.max()) {
            throw new ValidationConfigurationException("Некорректные границы в @Size для поля: " + field.getName());
        }

        if (value != null) {
            int length = ((CharSequence) value).length();
            if (length < annotation.min() || length > annotation.max()) {
                errors.add(new ValidationError(field.getName(), "Size", annotation.message()));
            }
        }
    }

    private static void processRange(Field field, Object value, List<ValidationError> errors) {
        Range annotation = field.getAnnotation(Range.class);
        if (annotation == null) return;

        Class<?> type = field.getType();
        boolean isNumeric = type == byte.class || type == short.class || type == int.class ||
                type == long.class || type == float.class || type == double.class ||
                Number.class.isAssignableFrom(type);

        if (!isNumeric) {
            throw new ValidationConfigurationException("@Range можно применять только к числовым типам. Поле: " + field.getName());
        }
        if (annotation.min() > annotation.max() || Double.isNaN(annotation.min()) || Double.isNaN(annotation.max()) ||
                Double.isInfinite(annotation.min()) || Double.isInfinite(annotation.max())) {
            throw new ValidationConfigurationException("Некорректные границы в @Range для поля: " + field.getName());
        }

        if (value != null) {
            double dValue = ((Number) value).doubleValue();
            if (Double.isNaN(dValue) || dValue < annotation.min() || dValue > annotation.max()) {
                errors.add(new ValidationError(field.getName(), "Range", annotation.message()));
            }
        }
    }

    private static void processEmail(Field field, Object value, List<ValidationError> errors) {
        Email annotation = field.getAnnotation(Email.class);
        if (annotation == null) return;

        if (!CharSequence.class.isAssignableFrom(field.getType())) {
            throw new ValidationConfigurationException("@Email можно применять только к CharSequence. Поле: " + field.getName());
        }

        if (value != null) {
            String strValue = value.toString();
            if (strValue.isBlank() || strValue.contains(" ")) {
                errors.add(new ValidationError(field.getName(), "Email", annotation.message()));
                return;
            }

            int atIndex = strValue.indexOf('@');
            if (atIndex <= 0 || atIndex == strValue.length() - 1) {
                errors.add(new ValidationError(field.getName(), "Email", annotation.message()));
                return;
            }

            String domainPart = strValue.substring(atIndex + 1);
            if (domainPart.indexOf('@') != -1) {
                errors.add(new ValidationError(field.getName(), "Email", annotation.message()));
                return;
            }

            int dotIndex = domainPart.indexOf('.');
            if (dotIndex <= 0 || dotIndex == domainPart.length() - 1) {
                errors.add(new ValidationError(field.getName(), "Email", annotation.message()));
            }
        }
    }
}
