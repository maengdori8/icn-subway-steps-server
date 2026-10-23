package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V30~V37: 상태 변경·내 팁. 실패한 요청 뒤에는 DB 행과 팁 개수가 그대로인지 다시 본다. */
class PermissionRegressionTest extends ApiTestSupport {

    @Test
    void ownerChangesStatusAndVerifiedAtIsRefreshed() throws Exception {
        String a = student01();
        long id = createTip(a, "EXPRESS", 4, "Gate tip");
        jdbc.update("update tip set verified_at = ? where id = ?", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), id);
        Map<String, Object> before = tipRow(id);

        String res = text(patchJson("/api/tips/" + id + "/status", a, body("status", "OUTDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTDATED"))
                .andExpect(jsonPath("$.title").value("Gate tip"))
                .andExpect(jsonPath("$.mine").value(true)));
        Instant changedAt = Instant.parse(JsonPath.read(res, "$.verifiedAt"));
        assertThat(changedAt).isAfter(Instant.parse("2026-01-01T00:00:00Z"));

        Map<String, Object> after = tipRow(id);
        assertThat(after.get("STATUS")).isEqualTo("OUTDATED");
        for (String col : List.of("TITLE", "LOCATION", "NOTE", "OWNER_ID", "CREATED_AT", "ROUTE_CODE", "STEP_NO", "EXAMPLE")) {
            assertThat(after.get(col)).as(col).isEqualTo(before.get(col));
        }

        patchJson("/api/tips/" + id + "/status", a, body("status", "VERIFIED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        // 같은 상태를 다시 보내도 200 (작성자 재확인)
        patchJson("/api/tips/" + id + "/status", a, body("status", "VERIFIED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        getWith("/api/tips/" + id, null).andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    /**
     * 8장 P02: 같은 상태를 다시 보내면 상태와 다른 값은 그대로이고 verifiedAt만 그 요청 시각으로 다시 기록한다
     * (docs/state-rules.md 전이 표의 VERIFIED→VERIFIED, OUTDATED→OUTDATED 행). 응답의 시각과 DB의 시각도 같아야 한다.
     */
    @Test
    void sameStatusAgainKeepsOtherValuesAndRefreshesVerifiedAt() throws Exception {
        String a = student01();
        long id = createTip(a, "LOCAL", 3, "Repeat tip");
        for (String next : List.of("VERIFIED", "OUTDATED")) {
            if (next.equals("OUTDATED")) {
                patchJson("/api/tips/" + id + "/status", a, body("status", "OUTDATED")).andExpect(status().isOk());
            }
            jdbc.update("update tip set verified_at = ? where id = ?", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), id);
            Map<String, Object> before = tipRow(id);
            int count = tipCount();

            Instant sentAfter = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            String res = text(patchJson("/api/tips/" + id + "/status", a, body("status", next))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) id))
                    .andExpect(jsonPath("$.status").value(next))
                    .andExpect(jsonPath("$.ownerId").value(101)));
            Instant answeredBefore = Instant.now();
            Instant verifiedAt = Instant.parse(JsonPath.read(res, "$.verifiedAt"));
            assertThat(verifiedAt).as(next + " 응답 verifiedAt은 이 요청 시각").isBetween(sentAfter, answeredBefore);

            Map<String, Object> after = tipRow(id);
            assertThat(after.get("STATUS")).isEqualTo(next);
            assertThat(asInstant(after.get("VERIFIED_AT"))).as(next + " DB verified_at = 응답 verifiedAt").isEqualTo(verifiedAt);
            for (String col : List.of("TITLE", "LOCATION", "NOTE", "OWNER_ID", "CREATED_AT", "ROUTE_CODE", "STEP_NO", "EXAMPLE")) {
                assertThat(after.get(col)).as(next + " " + col).isEqualTo(before.get(col));
            }
            assertThat(tipCount()).isEqualTo(count);
        }
    }

    private static Instant asInstant(Object value) {
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof Instant instant) return instant;
        throw new AssertionError("시각 열의 자료형이 예상과 다름: " + value);
    }

    @Test
    void otherUserGets403AndNothingChanges() throws Exception {
        String a = student01();
        String b = student02();
        long verifiedTip = createTip(a, "EXPRESS", 5, "Platform tip");
        long outdatedTip = createTip(a, "EXPRESS", 5, "Old platform tip");
        patchJson("/api/tips/" + outdatedTip + "/status", a, body("status", "OUTDATED")).andExpect(status().isOk());

        int count = tipCount();
        for (long id : new long[] {verifiedTip, outdatedTip, 8L}) {
            Map<String, Object> before = tipRow(id);
            for (String next : new String[] {"VERIFIED", "OUTDATED"}) {
                patchJson("/api/tips/" + id + "/status", b, body("status", next))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                        .andExpect(jsonPath("$.message").value("이 팁을 올린 사람만 상태를 바꿀 수 있습니다."));
            }
            assertThat(tipRow(id)).isEqualTo(before);
        }
        assertThat(tipCount()).isEqualTo(count);
    }

    @Test
    void invalidStatusBodiesAre400AndNothingChanges() throws Exception {
        String a = student01();
        long id = createTip(a, "LOCAL", 5, "Board tip");
        Map<String, Object> before = tipRow(id);
        for (Object payload : new Object[] {body("status", "DELETED"), body("status", "verified"), body(), body("status", null)}) {
            patchJson("/api/tips/" + id + "/status", a, payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.status").value("VERIFIED 또는 OUTDATED 중 하나를 보내세요."));
        }
        patchJson("/api/tips/" + id + "/status", a, body("status", "OUTDATED", "title", "changed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.errors.title").exists());
        patchJson("/api/tips/" + id + "/status", a, "").andExpect(status().isBadRequest());
        assertThat(tipRow(id)).isEqualTo(before);
    }

    @Test
    void missingTipIs404AndOrderIs401Then400Then404Then403() throws Exception {
        String a = student01();
        String b = student02();
        patchJson("/api/tips/999999/status", a, body("status", "OUTDATED"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        patchJson("/api/tips/999999/status", null, body("status", "WRONG")).andExpect(status().isUnauthorized());
        patchJson("/api/tips/999999/status", a, body("status", "WRONG")).andExpect(status().isBadRequest());
        patchJson("/api/tips/8/status", b, body("status", "WRONG")).andExpect(status().isBadRequest());
    }

    /**
     * 경로의 id가 정수가 아니거나 long 범위 밖이면 400 VALIDATION_FAILED errors.id다(GET /api/tips/{id}와 같음).
     * 경로 변수는 본문보다 먼저 읽으므로 본문이 틀려도 errors에는 id만 있다. 토큰 검사(401)는 그보다 먼저다.
     * 없는 번호는 0·음수도 404다. 어느 경우도 팁 행과 개수는 그대로다.
     */
    @Test
    void badTipIdIs400AndMissingIdIs404() throws Exception {
        String a = student01();
        Map<String, Object> before = tipRow(8L);
        int count = tipCount();
        for (String id : new String[] {"abc", "1.5", "9223372036854775808"}) {
            patchJson("/api/tips/" + id + "/status", a, body("status", "OUTDATED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.id").value("형식이 맞지 않습니다."));
            patchJson("/api/tips/" + id + "/status", a, body("status", 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.id").exists())
                    .andExpect(jsonPath("$.errors.status").doesNotExist());
            patchJson("/api/tips/" + id + "/status", null, body("status", "OUTDATED")).andExpect(status().isUnauthorized());
        }
        for (String id : new String[] {"0", "-1", "999999"}) {
            patchJson("/api/tips/" + id + "/status", a, body("status", "OUTDATED"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("팁을 찾을 수 없습니다."));
        }
        assertThat(tipRow(8L)).isEqualTo(before);
        assertThat(tipCount()).isEqualTo(count);
    }

    @Test
    void unauthenticatedStatusChangeIs401AndNothingChanges() throws Exception {
        Map<String, Object> before = tipRow(8L);
        patchJson("/api/tips/8/status", null, body("status", "VERIFIED")).andExpect(status().isUnauthorized());
        patchJson("/api/tips/8/status", expiredToken(), body("status", "VERIFIED")).andExpect(status().isUnauthorized());
        assertThat(tipRow(8L)).isEqualTo(before);
    }

    @Test
    void myTipsListsOnlyMine() throws Exception {
        String a = student01();
        long newer = createTip(a, "LOCAL", 1, "Mine 1");
        long newest = createTip(a, "EXPRESS", 2, "Mine 2");
        String res = text(getWith("/api/me/tips", bearer(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips[*].ownerId").value(everyItem(is(101))))
                .andExpect(jsonPath("$.tips[*].mine").value(everyItem(is(true))))
                .andExpect(jsonPath("$.tips[*].id").value(hasItems((int) newest, (int) newer, 8)))
                .andExpect(jsonPath("$.tips[*].id").value(not(hasItem(7)))));
        List<Integer> ids = JsonPath.read(res, "$.tips[*].id");
        assertThat(ids).isSortedAccordingTo((x, y) -> Integer.compare(y, x));

        getWith("/api/me/tips", bearer(student02()))
                .andExpect(jsonPath("$.tips[?(@.id == 7)].example").value(contains(true)));
        getWith("/api/me/tips", bearer(newUserToken())).andExpect(status().isOk()).andExpect(jsonPath("$.tips.length()").value(0));
    }
}
