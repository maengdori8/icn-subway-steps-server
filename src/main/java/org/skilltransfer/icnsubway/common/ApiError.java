package org.skilltransfer.icnsubway.common;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** 모든 오류 응답의 모양: {code, message, errors}. 스택 추적·SQL·토큰은 넣지 않는다. */
public record ApiError(String code, String message, Map<String, String> errors) {

    /** 보안 필터처럼 컨트롤러 밖에서 오류를 쓸 때 사용한다. */
    public static void write(HttpServletResponse response, JsonMapper json, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ApiError(code, message, Map.of()));
    }
}
