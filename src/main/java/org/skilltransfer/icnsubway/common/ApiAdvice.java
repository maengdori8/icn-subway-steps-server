package org.skilltransfer.icnsubway.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/** 컨트롤러에서 나온 실패를 {code, message, errors}로 바꾼다. */
@RestControllerAdvice
public class ApiAdvice {
    private static final Logger log = LoggerFactory.getLogger(ApiAdvice.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.errors()));
    }

    /** 깨진 JSON, 본문 없음, 정하지 않은 항목(status·ownerId 등), 자료형이 다른 값. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        Map<String, String> errors = Map.of();
        if (e.getCause() instanceof UnrecognizedPropertyException u) {
            errors = Map.of(u.getPropertyName(), "허용하지 않는 항목입니다.");
        } else if (e.getCause() instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            String field = m.getPath().getLast().getPropertyName();
            if (field != null) errors = Map.of(field, "형식이 맞지 않습니다.");
        }
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST_BODY", "요청 본문을 읽을 수 없습니다.", errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_FAILED", "입력값을 확인하세요.", Map.of(e.getName(), "형식이 맞지 않습니다.")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "주소를 찾을 수 없습니다.", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.", Map.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Content-Type: application/json 으로 보내세요.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            // 위에서 따로 다루지 않은 Spring MVC의 요청 오류(406 등)는 그 상태 그대로 돌려준다.
            return ResponseEntity.status(er.getStatusCode()).body(new ApiError("BAD_REQUEST", "요청을 처리할 수 없습니다.", Map.of()));
        }
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "서버 오류가 발생했습니다.", Map.of()));
    }
}
