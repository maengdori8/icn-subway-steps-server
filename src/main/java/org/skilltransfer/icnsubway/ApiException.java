package org.skilltransfer.icnsubway;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 서비스가 던지는 예상된 실패. ApiAdvice가 {code, message, errors}로 바꾼다. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public static ApiException validation(Map<String, String> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인하세요.", errors);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message, Map.of());
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message, Map.of());
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> errors() { return errors; }
}
