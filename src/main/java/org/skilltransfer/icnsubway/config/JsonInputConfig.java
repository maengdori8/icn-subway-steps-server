package org.skilltransfer.icnsubway.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * 요청 JSON의 자료형을 계약(docs/api-spec.yaml)대로 읽는다: stepNo는 정수, title·location·note·status 등은 문자열.
 * Jackson 기본값은 3.7 → 3, "3" → 3, ""·" " → null, 123 → "123", true → "true"로 조용히 바꿔 저장하므로 이 변환을 끈다.
 * JSON null은 변환이 아니므로 그대로 null이다(필수 항목이면 서비스가 400 VALIDATION_FAILED).
 * 실패하면 ApiAdvice가 400 INVALID_REQUEST_BODY와 errors.{항목} "형식이 맞지 않습니다."로 바꾼다.
 */
@Configuration
class JsonInputConfig {
    @Bean
    JsonMapperBuilderCustomizer strictScalarInput() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Integer, c -> c
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)) // 공백뿐인 문자열도 이 설정을 따른다
                .withCoercionConfig(LogicalType.Textual, c -> c
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
