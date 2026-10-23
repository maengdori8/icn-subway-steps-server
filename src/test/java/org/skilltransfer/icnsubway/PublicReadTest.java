package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** V01~V09: 로그인 없이 보는 경로·단계·팁. */
class PublicReadTest extends ApiTestSupport {

    @Autowired ApplicationContext context;

    @Test
    void health() throws Exception {
        getWith("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void h2ConsoleIsOffOutsideDevProfile() {
        assertThat(context.containsBean("h2ConsoleSecurity")).isFalse();
        assertThat(context.containsBean("h2Console")).isFalse();
    }

    @Test
    void routesListShowsTwoRoutesAndT2ComingSoon() throws Exception {
        getWith("/api/routes", null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.routes.length()").value(3))
                .andExpect(jsonPath("$.routes[0].code").value("EXPRESS"))
                .andExpect(jsonPath("$.routes[0].available").value(true))
                .andExpect(jsonPath("$.routes[0].stepCount").value(5))
                .andExpect(jsonPath("$.routes[0].name.ko").value("직통열차"))
                .andExpect(jsonPath("$.routes[0].name.en").value("Express Train"))
                .andExpect(jsonPath("$.routes[1].code").value("LOCAL"))
                .andExpect(jsonPath("$.routes[1].stepCount").value(5))
                .andExpect(jsonPath("$.routes[2].code").value("T2"))
                .andExpect(jsonPath("$.routes[2].available").value(false))
                .andExpect(jsonPath("$.routes[2].stepCount").value(0));
    }

    @Test
    void expressStepsHaveBilingualSignsAndNoElevatorYet() throws Exception {
        getWith("/api/routes/EXPRESS/steps", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("EXPRESS"))
                .andExpect(jsonPath("$.steps.length()").value(5))
                .andExpect(jsonPath("$.steps[0].stepNo").value(1))
                .andExpect(jsonPath("$.steps[0].role").value("HERE"))
                .andExpect(jsonPath("$.steps[0].icon").value("PLANE"))
                .andExpect(jsonPath("$.steps[0].photoKey").value("arrivals"))
                .andExpect(jsonPath("$.steps[0].sign.ko").value("제1여객터미널 입국장"))
                .andExpect(jsonPath("$.steps[0].sign.en").value("Terminal 1 Arrivals"))
                .andExpect(jsonPath("$.steps[0].title.en").value("Walk out of the arrivals hall"))
                .andExpect(jsonPath("$.steps[0].instructions.en.length()").value(2))
                .andExpect(jsonPath("$.steps[0].instructions.ko[0]").value("짐을 찾고 도착 출구로 나옵니다."))
                .andExpect(jsonPath("$.steps[0].elevator").value(nullValue()))
                .andExpect(jsonPath("$.steps[1].photoKey").value("transportCenter"))
                .andExpect(jsonPath("$.steps[1].role").value("FIND"))
                .andExpect(jsonPath("$.steps[3].photoKey").value("expressGate"))
                .andExpect(jsonPath("$.steps[4].photoKey").value("expressPlatform"))
                .andExpect(jsonPath("$.steps[4].sign.en").value("Express Platform · To Seoul Station"));
    }

    /** LOCAL 단계와 표본 팁 7이 docs/data-model.md 시드 표와 같은지. */
    @Test
    void localStepsAndSampleTip7MatchSeedTable() throws Exception {
        getWith("/api/routes/LOCAL/steps", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[*].stepNo").value(contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.steps[0].photoKey").value("arrivals"))
                .andExpect(jsonPath("$.steps[1].photoKey").value("transportCenter"))
                .andExpect(jsonPath("$.steps[2].photoKey").value("localConcourse"))
                .andExpect(jsonPath("$.steps[3].photoKey").value("localGate"))
                .andExpect(jsonPath("$.steps[4].photoKey").value("localPlatform"))
                .andExpect(jsonPath("$.steps[2].sign.ko").value("일반열차"))
                .andExpect(jsonPath("$.steps[3].sign.en").value("All-stop Gate"))
                .andExpect(jsonPath("$.steps[4].sign.en").value("Platform 1·2 · To Seoul Station"))
                .andExpect(jsonPath("$.steps[4].title.en").value("Board at platform 1 or 2"));
        getWith("/api/tips/7", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ask at the info center if your card fails"))
                .andExpect(jsonPath("$.location").value("B1, right of the ticket machines"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").value("2026-10-12T03:00:00Z"));
    }

    /** 엘리베이터 문구는 en·ko가 둘 다 있을 때만 준다. 한쪽만 채운 행을 {en, ko:null}로 주면 앱이 그 경로의 단계 화면 전체를 못 읽는다. */
    @Test
    void elevatorIsGivenOnlyWhenBothLanguagesAreFilled() throws Exception {
        String sql = "update route_step set elevator_en = ?, elevator_ko = ? where route_code = 'LOCAL' and step_no = 2";
        try {
            jdbc.update(sql, "Left of the escalator", null);
            getWith("/api/routes/LOCAL/steps", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.steps[1].elevator").value(nullValue()));
            for (String blank : new String[] {" ", "\u00A0", "\uFEFF\u0085"}) {
                jdbc.update(sql, blank, "에스컬레이터 왼쪽");
                getWith("/api/routes/LOCAL/steps", null).andExpect(status().isOk())
                        .andExpect(jsonPath("$.steps[1].elevator").value(nullValue()));
            }
            jdbc.update(sql, "Left of the escalator", "에스컬레이터 왼쪽");
            getWith("/api/routes/LOCAL/steps", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.steps[1].elevator.en").value("Left of the escalator"))
                    .andExpect(jsonPath("$.steps[1].elevator.ko").value("에스컬레이터 왼쪽"));
        } finally {
            jdbc.update(sql, (Object) null, (Object) null); // 시드 상태(현장 확인 전 null)로 되돌린다
        }
    }

    @Test
    void comingSoonRouteHasNoSteps() throws Exception {
        getWith("/api/routes/T2/steps", null).andExpect(status().isOk()).andExpect(jsonPath("$.steps.length()").value(0));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        getWith("/api/routes/NOPE/steps", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        getWith("/api/routes/express/steps", null).andExpect(status().isNotFound());
    }

    @Test
    void tipsForStepAnonymousAreNotMine() throws Exception {
        getWith("/api/tips?route=LOCAL&step=4", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips[?(@.id == 8)].title").value(contains("Elevator for big suitcases")))
                .andExpect(jsonPath("$.tips[?(@.id == 8)].status").value(contains("OUTDATED")))
                .andExpect(jsonPath("$.tips[?(@.id == 8)].verifiedAt").value(contains("2026-09-30T03:00:00Z")))
                .andExpect(jsonPath("$.tips[?(@.id == 8)].ownerId").value(contains(101)))
                .andExpect(jsonPath("$.tips[*].mine").value(everyItem(is(false))));
    }

    @Test
    void tipsForStepWithTokenMarkMine() throws Exception {
        getWith("/api/tips?route=LOCAL&step=4", bearer(student01()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips[?(@.id == 8)].mine").value(contains(true)));
        getWith("/api/tips?route=LOCAL&step=4", bearer(student02()))
                .andExpect(jsonPath("$.tips[?(@.id == 8)].mine").value(contains(false)));
    }

    @Test
    void emptyStepGivesEmptyList() throws Exception {
        getWith("/api/tips?route=EXPRESS&step=1", null).andExpect(status().isOk()).andExpect(jsonPath("$.tips").isArray());
    }

    @Test
    void tipsQueryValidation() throws Exception {
        for (String q : new String[] {"step=1", "route=T2&step=1", "route=express&step=1", "route=SUBWAY&step=1"}) {
            getWith("/api/tips?" + q, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.route").value("EXPRESS 또는 LOCAL 중 하나를 보내세요."));
        }
        for (String q : new String[] {"route=LOCAL", "route=LOCAL&step=0", "route=LOCAL&step=6", "route=LOCAL&step=abc"}) {
            getWith("/api/tips?" + q, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.step").exists());
        }
        getWith("/api/tips?route=LOCAL&step=6", null).andExpect(jsonPath("$.errors.step").value("1~5 사이의 단계 번호를 보내세요."));
        // 정수가 아닌 step(int 범위 밖 포함)은 따로 안내한다. route가 틀리면 step 범위는 보지 않는다.
        for (String step : new String[] {"abc", "1.5", "3000000000"}) {
            getWith("/api/tips?route=LOCAL&step=" + step, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.step").value("단계 번호는 숫자로 보내세요."));
        }
        getWith("/api/tips?route=T2&step=99", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.route").exists()).andExpect(jsonPath("$.errors.step").doesNotExist());
        // 정하지 않은 쿼리 항목은 무시한다
        getWith("/api/tips?route=LOCAL&step=4&sort=new", null).andExpect(status().isOk());
    }

    @Test
    void tipDetail() throws Exception {
        getWith("/api/tips/7", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("EXPRESS"))
                .andExpect(jsonPath("$.stepNo").value(3))
                .andExpect(jsonPath("$.example").value(true))
                .andExpect(jsonPath("$.ownerId").value(102))
                .andExpect(jsonPath("$.note").value(nullValue()))
                .andExpect(jsonPath("$.mine").value(false));
        // 없는 번호는 0·음수도 404(400 아님)
        for (String id : new String[] {"999999", "0", "-1"}) {
            getWith("/api/tips/" + id, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        getWith("/api/tips/abc", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.id").exists());
        // 정수가 아니거나 long 범위를 넘는 id도 400(docs/api-spec.yaml GET /api/tips/{id})
        for (String id : new String[] {"1.5", "9223372036854775808"}) {
            getWith("/api/tips/" + id, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.id").value("형식이 맞지 않습니다."));
        }
    }

    @Test
    void publicGetWithInvalidOrExpiredTokenIs401() throws Exception {
        // 헤더가 있는데 무효인 401은 필터의 문구다(헤더가 없는 보호 API의 '로그인이 필요합니다.'와 다름)
        getWith("/api/routes", "Bearer not-a-jwt").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.message").value("로그인이 만료되었거나 올바르지 않습니다."));
        getWith("/api/tips?route=LOCAL&step=4", bearer(expiredToken())).andExpect(status().isUnauthorized());
        getWith("/api/routes/EXPRESS/steps", bearer(expiredToken())).andExpect(status().isUnauthorized());
        getWith("/api/tips/7", bearer(expiredToken())).andExpect(status().isUnauthorized());
        // 무효 헤더는 입력 오류(400)·없는 대상(404)보다 먼저다
        getWith("/api/tips?route=T2", bearer(expiredToken())).andExpect(status().isUnauthorized());
        getWith("/api/routes/NOPE/steps", bearer(expiredToken())).andExpect(status().isUnauthorized());
    }
}
