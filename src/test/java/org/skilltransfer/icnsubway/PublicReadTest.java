package org.skilltransfer.icnsubway;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

/** V01~V09: 로그인 없이 보는 경로·단계·팁. */
class PublicReadTest extends ApiTestSupport {

    @Test
    void health() throws Exception {
        getWith("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
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
                .andExpect(jsonPath("$.steps[1].photoKey").value(nullValue()))
                .andExpect(jsonPath("$.steps[1].role").value("FIND"))
                .andExpect(jsonPath("$.steps[4].sign.en").value("Express Platform · To Seoul Station"));
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
        getWith("/api/tips/999999", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        getWith("/api/tips/abc", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    void publicGetWithInvalidOrExpiredTokenIs401() throws Exception {
        getWith("/api/routes", "Bearer not-a-jwt").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(jsonPath("$.errors").isMap());
        getWith("/api/tips?route=LOCAL&step=4", bearer(expiredToken(STUDENT01))).andExpect(status().isUnauthorized());
        getWith("/api/routes/EXPRESS/steps", bearer(expiredToken(STUDENT01))).andExpect(status().isUnauthorized());
    }
}
