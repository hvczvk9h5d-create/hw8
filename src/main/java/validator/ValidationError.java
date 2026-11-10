package validator;

public record ValidationError(String fieldName, String annotationName, String message) {
}
