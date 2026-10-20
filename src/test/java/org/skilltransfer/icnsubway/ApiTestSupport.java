package org.skilltransfer.icnsubway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** 모든 API 테스트가 같은 컨텍스트(메모리 DB, build/test-data 키)를 쓴다. 테스트끼리 표본 팁 7·8은 바꾸지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestSupport {
    /** 교재가 정한 로컬 실습용 공개 표본 비밀번호(실제 비밀번호 아님). */
    static final String SAMPLE_PASSWORD = "study-1234";
    static final long STUDENT01 = 101L;
    static final long STUDENT02 = 102L;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    String toJson(Object o) {
        return json.writeValueAsString(o);
    }

    String text(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    ResultActions postJson(String url, String token, Object payload) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(payload instanceof String s ? s : toJson(payload));
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mvc.perform(req);
    }

    ResultActions patchJson(String url, String token, Object payload) throws Exception {
        var req = patch(url).contentType(MediaType.APPLICATION_JSON).content(payload instanceof String s ? s : toJson(payload));
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mvc.perform(req);
    }

    ResultActions getWith(String url, String authorization) throws Exception {
        var req = get(url);
        if (authorization != null) req.header(HttpHeaders.AUTHORIZATION, authorization);
        return mvc.perform(req);
    }

    String login(String username, String password) throws Exception {
        String res = text(postJson("/api/auth/login", null, body("username", username, "password", password)).andExpect(status().isOk()));
        return JsonPath.read(res, "$.accessToken");
    }

    String student01() throws Exception { return login("student01", SAMPLE_PASSWORD); }
    String student02() throws Exception { return login("student02", SAMPLE_PASSWORD); }

    static String uniqueName() {
        return "tester_" + String.format("%04d", SEQ.incrementAndGet()); // 메모리 DB는 JVM마다 새로 만든다
    }

    /** 새 계정을 만들고 그 토큰을 돌려준다. */
    String newUserToken() throws Exception {
        String name = uniqueName();
        postJson("/api/auth/signup", null, body("username", name, "password", "pass-word-1")).andExpect(status().isCreated());
        return login(name, "pass-word-1");
    }

    long createTip(String token, String route, int stepNo, String title) throws Exception {
        String res = text(postJson("/api/tips", token, body("route", route, "stepNo", stepNo, "title", title, "location", "B1"))
                .andExpect(status().isCreated()));
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    Map<String, Object> tipRow(long id) {
        return jdbc.queryForMap("select route_code, step_no, title, location, note, status, verified_at, created_at, owner_id, example "
                + "from tip where id = ?", id);
    }

    int tipCount() {
        return jdbc.queryForObject("select count(*) from tip", Integer.class);
    }

    /** 서버와 같은 키(build/test-data/.jwt-secret)로 원하는 토큰을 만든다: 만료·발급자 다름 등. */
    static SecretKey serverKey() throws Exception {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(Files.readString(Path.of("build/test-data/.jwt-secret")).trim()));
    }

    static String token(SecretKey key, long subject, String issuer, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder().issuer(issuer).subject(String.valueOf(subject))
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    static String expiredToken(long subject) throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
        return token(serverKey(), subject, "skilltransfer-ch07", past, past.plusSeconds(1800));
    }
}
