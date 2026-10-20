package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** V10~V18: 회원가입·로그인·토큰 검사. */
class AuthApiTest extends ApiTestSupport {

    @Test
    void signupReturns201WithoutPassword() throws Exception {
        String name = uniqueName();
        String res = text(postJson("/api/auth/signup", null, body("username", name, "password", "pass-word-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.id").value(greaterThanOrEqualTo(103)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()));
        assertThat(res).doesNotContain("pass-word-1").doesNotContain("$2");
        String hash = jdbc.queryForObject("select password_hash from app_user where username = ?", String.class, name);
        assertThat(hash).startsWith("$2").isNotEqualTo("pass-word-1");
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        postJson("/api/auth/signup", null, body("username", "student01", "password", "pass-word-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void signupValidation() throws Exception {
        for (String bad : new String[] {"Student", "abc", "a".repeat(25), "ab-cd", "", "학생1234"}) {
            postJson("/api/auth/signup", null, body("username", bad, "password", "pass-word-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.username").value("소문자 영문·숫자·밑줄 4~24자로 입력하세요."));
        }
        // 7자, 한글 25자(75바이트), 없음
        for (String bad : new String[] {"seven77", "가".repeat(25)}) {
            postJson("/api/auth/signup", null, body("username", uniqueName(), "password", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value("8자 이상, UTF-8 72바이트 이하로 입력하세요."));
        }
        postJson("/api/auth/signup", null, body("username", uniqueName()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        // 경계: 8자, 한글 24자(72바이트)는 통과
        postJson("/api/auth/signup", null, body("username", uniqueName(), "password", "eight888")).andExpect(status().isCreated());
        postJson("/api/auth/signup", null, body("username", uniqueName(), "password", "가".repeat(24))).andExpect(status().isCreated());
    }

    @Test
    void signupRejectsUnknownField() throws Exception {
        postJson("/api/auth/signup", null, body("username", uniqueName(), "password", "pass-word-1", "role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.errors.role").value("허용하지 않는 항목입니다."));
    }

    @Test
    void sampleAccountLoginIssuesHs256TokenFor1800Seconds() throws Exception {
        String res = text(postJson("/api/auth/login", null, body("username", "student01", "password", SAMPLE_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(101))
                .andExpect(jsonPath("$.user.username").value("student01")));
        String token = JsonPath.read(res, "$.accessToken");
        Jws<Claims> jws = Jwts.parser().verifyWith(serverKey()).build().parseSignedClaims(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jws.getPayload().getIssuer()).isEqualTo("skilltransfer-ch07");
        assertThat(jws.getPayload().getSubject()).isEqualTo("101");
        long ttl = (jws.getPayload().getExpiration().getTime() - jws.getPayload().getIssuedAt().getTime()) / 1000;
        assertThat(ttl).isEqualTo(1800);
    }

    @Test
    void wrongPasswordAndUnknownUserGetSame401() throws Exception {
        String wrongPw = text(postJson("/api/auth/login", null, body("username", "student01", "password", "wrong-pass"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED")));
        String noUser = text(postJson("/api/auth/login", null, body("username", "nobody_here", "password", "wrong-pass"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED")));
        assertThat(noUser).isEqualTo(wrongPw);
        postJson("/api/auth/login", null, body("username", "student01", "password", "가".repeat(30)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankLoginIs400() throws Exception {
        postJson("/api/auth/login", null, body("username", "", "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("아이디를 입력하세요."))
                .andExpect(jsonPath("$.errors.password").value("비밀번호를 입력하세요."));
    }

    @Test
    void loginIgnoresStaleAuthorizationHeader() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .header("Authorization", bearer(expiredToken()))
                        .contentType("application/json")
                        .content(toJson(body("username", "student01", "password", SAMPLE_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void protectedApisWithoutTokenAre401() throws Exception {
        getWith("/api/me/tips", null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        postJson("/api/tips", null, body("route", "LOCAL", "stepNo", 1, "title", "t", "location", "l"))
                .andExpect(status().isUnauthorized());
        patchJson("/api/tips/8/status", null, body("status", "VERIFIED")).andExpect(status().isUnauthorized());
    }

    @Test
    void badTokensAre401() throws Exception {
        String good = student01();
        String tampered = good.substring(0, good.length() - 2) + (good.endsWith("AA") ? "BB" : "AA");
        String[] payloadParts = good.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"skilltransfer-ch07\",\"sub\":\"102\"}".getBytes(StandardCharsets.UTF_8));
        String swappedPayload = payloadParts[0] + "." + forgedPayload + "." + payloadParts[2];
        String unsigned = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + forgedPayload + ".";
        var otherKey = Keys.hmacShaKeyFor(new byte[32]);
        var key = serverKey();
        String iss = "skilltransfer-ch07";
        Instant now = Instant.now();
        Instant later = now.plusSeconds(1800);
        String[] headers = {
                "Basic c3R1ZGVudDAxOnN0dWR5LTEyMzQ=",
                "Bearer ",
                bearer(tampered),
                bearer(swappedPayload),
                bearer(unsigned),
                bearer(token(otherKey, STUDENT01, "student01", iss, now, later)),       // 다른 키
                bearer(token(key, STUDENT01, "student01", "someone-else", now, later)), // 발급자 다름
                bearer(expiredToken()),                                                 // 만료
                bearer(token(key, STUDENT01, "student01", iss, now, null)),             // exp 없음
                bearer(Jwts.builder().issuer(iss).subject("101").claim("username", "student01")
                        .expiration(java.util.Date.from(later)).signWith(key, Jwts.SIG.HS256).compact()), // iat 없음
                bearer(Jwts.builder().issuer(iss).subject("101").issuedAt(java.util.Date.from(now))
                        .expiration(java.util.Date.from(later)).signWith(key, Jwts.SIG.HS256).compact()), // username 없음
                bearer(token(key, 999_999L, "ghost", iss, now, later)),                 // 없는 사용자
                bearer(token(key, STUDENT01, "student02", iss, now, later)),            // id와 이름이 다름
                bearer(token(key, STUDENT01, "student01", iss, now.minusSeconds(864_000), later)) // 계정이 생기기 전에 발급
        };
        for (String h : headers) {
            getWith("/api/me/tips", h).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            getWith("/api/routes", h).andExpect(status().isUnauthorized());
        }
        // 대조군: 같은 방법으로 만든 정상 토큰은 통과한다(위 401이 다른 이유로 난 것이 아님)
        getWith("/api/me/tips", bearer(token(key, STUDENT01, "student01", iss, now, later))).andExpect(status().isOk());
        getWith("/api/me/tips", bearer(good)).andExpect(status().isOk());
    }

    /** DB만 초기화하고 키를 남겨 같은 id가 다른 계정에 다시 배정돼도, 예전 토큰은 새 계정으로 인증되지 않는다. */
    @Test
    void oldTokenDoesNotAuthenticateReassignedUserId() throws Exception {
        String oldName = uniqueName();
        postJson("/api/auth/signup", null, body("username", oldName, "password", "pass-word-1")).andExpect(status().isCreated());
        String oldToken = login(oldName, "pass-word-1");
        Long id = jdbc.queryForObject("select id from app_user where username = ?", Long.class, oldName);

        // 다른 이름으로 같은 id 재배정
        jdbc.update("delete from app_user where id = ?", id);
        jdbc.update("insert into app_user (id, username, password_hash, created_at) values (?, ?, ?, ?)",
                id, uniqueName(), "x", java.sql.Timestamp.from(Instant.now()));
        getWith("/api/me/tips", bearer(oldToken)).andExpect(status().isUnauthorized());

        // 같은 이름이라도 토큰 발급 뒤에 새로 만든 계정이면 거절
        jdbc.update("delete from app_user where id = ?", id);
        jdbc.update("insert into app_user (id, username, password_hash, created_at) values (?, ?, ?, ?)",
                id, oldName, "x", java.sql.Timestamp.from(Instant.now().plusSeconds(5)));
        getWith("/api/me/tips", bearer(oldToken)).andExpect(status().isUnauthorized());
    }
}
