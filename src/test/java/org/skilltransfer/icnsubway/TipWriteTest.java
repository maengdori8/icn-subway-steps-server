package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

/** V20~V28: 팁 등록. 글자 수는 앞뒤 공백 제거 뒤 코드 포인트로 센다. */
class TipWriteTest extends ApiTestSupport {
    private static final String EMOJI = "😀"; // 코드 포인트 1개, UTF-16 2단위

    @Test
    void createReturns201WithLocationAndServerOwnedFields() throws Exception {
        String token = student01();
        String res = text(postJson("/api/tips", token,
                body("route", "LOCAL", "stepNo", 2, "title", "  넓은 개찰구  ", "location", " 개찰구 왼쪽 끝　", "note", "   "))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/tips/\\d+")))
                .andExpect(jsonPath("$.id").value(greaterThanOrEqualTo(9)))
                .andExpect(jsonPath("$.route").value("LOCAL"))
                .andExpect(jsonPath("$.stepNo").value(2))
                .andExpect(jsonPath("$.title").value("넓은 개찰구"))
                .andExpect(jsonPath("$.location").value("개찰구 왼쪽 끝"))
                .andExpect(jsonPath("$.note").value(nullValue()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value(101))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.example").value(false)));
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        String verifiedAt = JsonPath.read(res, "$.verifiedAt");

        getWith("/api/tips/" + id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("넓은 개찰구"))
                .andExpect(jsonPath("$.verifiedAt").value(verifiedAt))
                .andExpect(jsonPath("$.mine").value(false));
        getWith("/api/tips?route=LOCAL&step=2", null).andExpect(jsonPath("$.tips[0].id").value(id));
    }

    @Test
    void titleAndLocationBoundary80() throws Exception {
        String token = student01();
        postJson("/api/tips", token, tip("가".repeat(80), "나".repeat(80), null)).andExpect(status().isCreated());
        postJson("/api/tips", token, tip("가".repeat(81), "나".repeat(81), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.title").value("80자 이하로 입력하세요."))
                .andExpect(jsonPath("$.errors.location").value("80자 이하로 입력하세요."));
        // 앞뒤 공백은 세지 않는다
        postJson("/api/tips", token, tip("  " + "가".repeat(80) + "  ", "B1", null)).andExpect(status().isCreated());
    }

    @Test
    void emojiCountsAsOneCharacter() throws Exception {
        String token = student01();
        String res = text(postJson("/api/tips", token, tip(EMOJI.repeat(80), EMOJI.repeat(80), EMOJI.repeat(150)))
                .andExpect(status().isCreated()));
        String saved = JsonPath.read(res, "$.title");
        assertThat(saved.codePointCount(0, saved.length())).isEqualTo(80);
        assertThat(saved.length()).isEqualTo(160);
        postJson("/api/tips", token, tip(EMOJI.repeat(81), "B1", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").value("80자 이하로 입력하세요."));
        postJson("/api/tips", token, tip("B1", "B1", EMOJI.repeat(151)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").value("150자 이하로 입력하세요."));
    }

    @Test
    void blankTitleOrLocation() throws Exception {
        String token = student01();
        for (String blank : new String[] {"", "   ", " 　\t"}) {
            postJson("/api/tips", token, tip(blank, blank, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("1~80자로 입력하세요."))
                    .andExpect(jsonPath("$.errors.location").value("1~80자로 입력하세요."));
        }
        postJson("/api/tips", token, body("route", "LOCAL", "stepNo", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.location").exists())
                .andExpect(jsonPath("$.errors.note").doesNotExist());
    }

    @Test
    void noteBoundary150() throws Exception {
        String token = student01();
        postJson("/api/tips", token, tip("t", "l", "가".repeat(150))).andExpect(status().isCreated());
        postJson("/api/tips", token, tip("t", "l", "가".repeat(151)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").value("150자 이하로 입력하세요."));
    }

    @Test
    void serverOwnedFieldsInBodyAre400() throws Exception {
        String token = student01();
        int before = tipCount();
        for (String field : new String[] {"status", "ownerId", "owner_id", "verifiedAt", "id", "example", "mine"}) {
            var payload = tip("t", "l", null);
            payload.put(field, field.equals("status") ? "OUTDATED" : 1);
            postJson("/api/tips", token, payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                    .andExpect(jsonPath("$.errors." + field).value("허용하지 않는 항목입니다."));
        }
        assertThat(tipCount()).isEqualTo(before);
    }

    @Test
    void unreadableBodiesAre400() throws Exception {
        String token = student01();
        postJson("/api/tips", token, "{").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        postJson("/api/tips", token, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        postJson("/api/tips", token, "{\"route\":\"LOCAL\",\"stepNo\":\"abc\",\"title\":\"t\",\"location\":\"l\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.stepNo").exists());
    }

    @Test
    void routeAndStepMustExist() throws Exception {
        String token = student01();
        for (Object route : new Object[] {"T2", "SUBWAY", "local", null}) {
            var payload = tip("t", "l", null);
            payload.put("route", route);
            postJson("/api/tips", token, payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.route").exists());
        }
        for (Object stepNo : new Object[] {0, 6, -1, null}) {
            var payload = tip("t", "l", null);
            payload.put("stepNo", stepNo);
            postJson("/api/tips", token, payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.stepNo").exists());
        }
    }

    @Test
    void createWithoutTokenIs401AndSavesNothing() throws Exception {
        int before = tipCount();
        postJson("/api/tips", null, tip("t", "l", null)).andExpect(status().isUnauthorized());
        postJson("/api/tips", expiredToken(STUDENT01), tip("t", "l", null)).andExpect(status().isUnauthorized());
        // 401이 입력 오류보다 먼저다
        postJson("/api/tips", null, tip("", "", null)).andExpect(status().isUnauthorized());
        assertThat(tipCount()).isEqualTo(before);
    }

    private static java.util.Map<String, Object> tip(String title, String location, String note) {
        return body("route", "LOCAL", "stepNo", 3, "title", title, "location", location, "note", note);
    }
}
