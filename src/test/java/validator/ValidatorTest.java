package validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    static class BaseUser {
        @NotNull
        protected String role;
    }

    static class User extends BaseUser {
        @NotNull(message = "Имя обязательно")
        @Size(min = 2, max = 50, message = "Неверная длина имени")
        private String name;

        @Email(message = "Некорректный email")
        private String email;

        @Range(min = 0, max = 150)
        private Integer age;

        public User(String name, String email, Integer age, String role) {
            this.name = name;
            this.email = email;
            this.age = age;
            this.role = role;
        }
    }

    static class ConfigErrorClass {
        @Size(min = 10, max = 5)
        String invalidSizeBounds;

        @Range(min = 0, max = 10)
        String notANumber;
    }

    @Test
    void testValidObjectReturnsEmptyErrors() {
        User user = new User("Alice", "alice@example.com", 25, "ADMIN");
        ValidationResult result = Validator.validate(user);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testNullObjectThrowsNPE() {
        assertThrows(NullPointerException.class, () -> Validator.validate(null));
    }

    @Test
    void testMultipleErrorsGathered() {
        User user = new User(null, "invalid email", -5, null);
        ValidationResult result = Validator.validate(user);

        assertFalse(result.isValid());
        assertEquals(4, result.getErrors().size());

        boolean hasEmailError = result.getErrors().stream().anyMatch(e -> e.fieldName().equals("email") && e.annotationName().equals("Email"));
        assertTrue(hasEmailError);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid@domain.com", "a.b@domain.org"})
    void testValidEmail(String email) {
        User user = new User("Bob", email, 30, "USER");
        assertTrue(Validator.validate(user).isValid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", " spaces @domain.com", "no-at-sign.com", "@empty-local.com", "empty-domain@.com", "no-dot@domain"})
    void testInvalidEmail(String email) {
        User user = new User("Bob", email, 30, "USER");
        ValidationResult result = Validator.validate(user);
        assertFalse(result.isValid());
        assertEquals("Email", result.getErrors().get(0).annotationName());
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "150, true",
            "-1, false",
            "151, false"
    })
    void testRangeBoundaries(int age, boolean isValid) {
        User user = new User("Alice", "test@test.ru", age, "USER");
        assertEquals(isValid, Validator.validate(user).isValid());
    }

    @Test
    void testConfigurationExceptions() {
        ConfigErrorClass obj = new ConfigErrorClass();
        obj.invalidSizeBounds = "Test";
        obj.notANumber = "String";

        ValidationConfigurationException ex = assertThrows(ValidationConfigurationException.class,
                () -> Validator.validate(obj));
        assertTrue(ex.getMessage().contains("Некорректные границы в @Size") || ex.getMessage().contains("можно применять только к числовым типам"));
    }
}
