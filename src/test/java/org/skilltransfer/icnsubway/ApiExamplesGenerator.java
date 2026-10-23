package org.skilltransfer.icnsubway;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * docs/api-examples/의 예시 JSON을 만들고, 평소 test에서는 지금 코드의 응답과 같은지 비교한다.
 * - 다른 테스트와 나눈 메모리 DB(icn-examples)에서 시드 + 정해진 순서의 요청을 MockMvc로 보내므로 매번 같은 결과가 나온다.
 * - 요청마다 yaml과 맞춰 본다: 요청(메서드·주소)이 어느 작업인지, 기대 상태 코드인지, 그 상태가 선언됐는지,
 *   본문이 스키마($ref·allOf·items까지: 필수 필드, 자료형, enum, null, date-time)에 맞는지, 선언한 헤더가 있는지.
 *   문제가 하나라도 있으면 아무 파일도 쓰지 않고 실패한다(이름과 이유만 적는다).
 * - 응답은 메모리에서 파싱해 accessToken·비밀번호·실행 시각을 바꾼 뒤에만 쓴다(meta.replaced에 바꾼 곳을 적는다).
 *   Authorization 헤더 값과 원문 응답은 파일·로그 어디에도 쓰지 않는다(MockMvc 출력도 끈다).
 * 평소(test): 만든 예시를 docs/api-examples/*.json과 비교한다. 파일 목록이나 내용이 다르면 실패한다(코드가 바뀌었는데 예시가 낡음).
 * 만들기(API_EXAMPLES=1): build/api-examples/에 쓰고, docs와 다른 파일 이름을 출력한다. 사람이 읽어 본 뒤 docs로 복사한다.
 *   $env:API_EXAMPLES='1'; .\gradlew.bat test --offline --rerun --tests '*ApiExamplesGenerator*'; Remove-Item Env:API_EXAMPLES
 *   (docs/api-examples/README.md '다시 만드는 명령')
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:icn-examples;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class ApiExamplesGenerator {
    static final Path OUT = Path.of("build/api-examples");
    static final Path DOCS = Path.of("docs/api-examples");
    /** 만들기 모드에서 docs와 다른 파일 목록(이름·JSON 경로만)을 적는 곳. */
    static final Path CHANGES = Path.of("build/api-examples-changes.txt");
    static final String HIDDEN_TOKEN = "<숨김: 예시용, 실행할 수 없는 값>";
    /** 실행할 때마다 바뀌는 시각 대신 적는 고정값. 새 팁의 createdAt과 등록 때 verifiedAt. */
    static final String FIXED_TIME = "2026-10-21T00:00:00Z";
    /** 상태를 바꾼 뒤(PATCH 응답)의 verifiedAt. 등록 시각보다 뒤라는 순서만 살린다(실제 간격은 다르다). */
    static final String FIXED_STATUS_TIME = "2026-10-21T00:05:00Z";
    /** 시드 예시 팁 7·8의 시각(SeedData). 고정값이라 그대로 둔다. */
    static final Set<String> SEED_TIMES = Set.of("2026-10-12T03:00:00Z", "2026-09-30T03:00:00Z");
    static final String SOURCE = "ApiExamplesGenerator · 테스트 프로필 · 메모리 DB";
    static final String NOTE = "자동 테스트 예시이며 실행 서버의 실제 응답이 아님";
    private static final Set<Long> SEED_TIPS = Set.of(7L, 8L);
    private static final String STATUS_OPERATION = "PATCH /api/tips/{id}/status";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    private ApiSpec spec;
    private ObjectWriter writer;
    /** 인증 라벨 → 토큰. 메모리에만 둔다. */
    private final Map<String, String> tokens = new LinkedHashMap<>();
    /** 파일 이름 → 쓸 내용(숨김 처리 뒤). 문제가 없을 때만 파일로 쓴다. */
    private final Map<String, String> files = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    /** 보낼 요청. body는 Map(JSON으로 보냄) 또는 String(그대로 보냄). passwordNote는 비밀번호를 숨길 때 적는 설명. */
    private record Call(String method, String url, String auth, Object body, String contentType, String passwordNote) {
        static Call of(String method, String url, String auth) {
            return new Call(method, url, auth, null, null, null);
        }

        Call body(Object b) {
            return new Call(method, url, auth, b, contentType, passwordNote);
        }

        Call type(String t) {
            return new Call(method, url, auth, body, t, passwordNote);
        }

        Call password(String note) {
            return new Call(method, url, auth, body, contentType, note);
        }
    }

    @Test
    void generateOrCompare() throws Exception {
        boolean generate = "1".equals(System.getenv("API_EXAMPLES"));
        if (generate) clearOut(); // 실패해도 지난번 파일이 남아 복사되지 않게 먼저 비운다
        spec = ApiSpec.load();
        writer = json.writer().with(new DefaultPrettyPrinter(Separators.createDefaultInstance()
                        .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n")));
        tokens.put("없음", null);
        tokens.put("student01 토큰", login("student01"));
        tokens.put("student02 토큰", login("student02"));
        tokens.put("무효 토큰", "not-a-jwt");

        // 공개 조회: 팁을 만들기 전이라 시드만 보인다
        save("routes-list", Call.of("GET", "/api/routes", "없음"), 200);
        save("routes-list-401-invalid-token", Call.of("GET", "/api/routes", "무효 토큰"), 401);
        save("route-steps", Call.of("GET", "/api/routes/EXPRESS/steps", "없음"), 200);
        save("route-steps-401-invalid-token", Call.of("GET", "/api/routes/EXPRESS/steps", "무효 토큰"), 401);
        save("route-steps-404-unknown-route", Call.of("GET", "/api/routes/NOPE/steps", "없음"), 404);
        save("tips-for-step", Call.of("GET", "/api/tips?route=LOCAL&step=4", "없음"), 200);
        save("tips-for-step-with-token", Call.of("GET", "/api/tips?route=LOCAL&step=4", "student01 토큰"), 200);
        save("tips-for-step-400-out-of-range", Call.of("GET", "/api/tips?route=LOCAL&step=6", "없음"), 400);
        save("tips-for-step-401-invalid-token", Call.of("GET", "/api/tips?route=LOCAL&step=4", "무효 토큰"), 401);
        save("tip-detail", Call.of("GET", "/api/tips/7", "없음"), 200);
        save("tip-detail-400-invalid-id", Call.of("GET", "/api/tips/abc", "없음"), 400);
        save("tip-detail-401-invalid-token", Call.of("GET", "/api/tips/7", "무효 토큰"), 401);
        save("tip-detail-404-not-found", Call.of("GET", "/api/tips/999999", "없음"), 404);
        save("my-tips", Call.of("GET", "/api/me/tips", "student01 토큰"), 200);
        save("my-tips-401-no-token", Call.of("GET", "/api/me/tips", "없음"), 401);

        // 로그인·가입
        save("login-response", Call.of("POST", "/api/auth/login", "없음")
                .body(body("username", "student01", "password", ApiTestSupport.SAMPLE_PASSWORD)).password("표본 비밀번호"), 200);
        save("login-400-blank", Call.of("POST", "/api/auth/login", "없음")
                .body(body("username", "", "password", "")), 400);
        save("login-401-wrong-password", Call.of("POST", "/api/auth/login", "없음")
                .body(body("username", "student01", "password", "wrong-pass")).password("틀린 비밀번호"), 401);
        save("login-415-not-json", Call.of("POST", "/api/auth/login", "없음")
                .body("username=student01").type("text/plain"), 415);
        save("signup", Call.of("POST", "/api/auth/signup", "없음")
                .body(body("username", "example01", "password", "pass-word-1")).password("새 비밀번호(8자 이상)"), 201);
        save("signup-400-invalid", Call.of("POST", "/api/auth/signup", "없음")
                .body(body("username", "Ab", "password", "short")).password("5자 비밀번호"), 400);
        save("signup-400-unknown-field", Call.of("POST", "/api/auth/signup", "없음")
                .body(body("username", "example02", "password", "pass-word-1", "role", "ADMIN")).password("새 비밀번호(8자 이상)"), 400);
        save("signup-409-username-taken", Call.of("POST", "/api/auth/signup", "없음")
                .body(body("username", "student01", "password", "pass-word-1")).password("새 비밀번호(8자 이상)"), 409);
        save("signup-415-not-json", Call.of("POST", "/api/auth/signup", "없음")
                .body("username=example03").type("text/plain"), 415);

        // 팁 올리기: 처음 성공한 팁이 9번이 된다(tip_seq 시작 9)
        save("tip-create", Call.of("POST", "/api/tips", "student01 토큰")
                .body(body("route", "LOCAL", "stepNo", 4, "title", "넓은 개찰구", "location", "개찰구 왼쪽 끝", "note", null)), 201);
        save("tip-create-400-validation", Call.of("POST", "/api/tips", "student01 토큰")
                .body(body("route", "LOCAL", "stepNo", 6, "title", "   ", "location", "B1")), 400);
        save("tip-create-400-unknown-field", Call.of("POST", "/api/tips", "student01 토큰")
                .body(body("route", "LOCAL", "stepNo", 4, "title", "t", "location", "l", "status", "OUTDATED")), 400);
        save("tip-create-400-wrong-type", Call.of("POST", "/api/tips", "student01 토큰")
                .body(body("route", "LOCAL", "stepNo", "4", "title", "t", "location", "l")), 400);
        save("tip-create-401-no-token", Call.of("POST", "/api/tips", "없음")
                .body(body("route", "LOCAL", "stepNo", 4, "title", "t", "location", "l")), 401);
        save("tip-create-415-not-json", Call.of("POST", "/api/tips", "student01 토큰")
                .body("route=LOCAL&stepNo=4&title=t&location=l").type("text/plain"), 415);

        // 상태 바꾸기
        save("tip-status", Call.of("PATCH", "/api/tips/9/status", "student01 토큰")
                .body(body("status", "OUTDATED")), 200);
        save("tip-status-400-invalid-status", Call.of("PATCH", "/api/tips/9/status", "student01 토큰")
                .body(body("status", "DELETED")), 400);
        save("tip-status-401-no-token", Call.of("PATCH", "/api/tips/8/status", "없음")
                .body(body("status", "VERIFIED")), 401);
        save("tip-status-403-not-owner", Call.of("PATCH", "/api/tips/8/status", "student02 토큰")
                .body(body("status", "VERIFIED")), 403);
        save("tip-status-404-not-found", Call.of("PATCH", "/api/tips/999999/status", "student01 토큰")
                .body(body("status", "OUTDATED")), 404);
        save("tip-status-415-not-json", Call.of("PATCH", "/api/tips/9/status", "student01 토큰")
                .body("status=VERIFIED").type("text/plain"), 415);

        if (!problems.isEmpty()) {
            fail("예시 " + problems.size() + "건이 yaml·기대와 다르다(파일은 쓰지 않음, 원문 응답은 적지 않음):\n- "
                    + String.join("\n- ", problems));
        }
        List<String> differences = differencesFromDocs();
        if (generate) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                Files.writeString(OUT.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
            }
            String summary = "api-examples: " + files.size() + "개를 " + OUT + "에 저장. docs/api-examples와 다른 곳 "
                    + differences.size() + "건" + (differences.isEmpty() ? "" : ":\n- " + String.join("\n- ", differences));
            Files.writeString(CHANGES, summary + "\n", StandardCharsets.UTF_8);
            System.out.println(summary);
        } else if (!differences.isEmpty()) {
            fail("docs/api-examples가 지금 코드의 응답과 다르다(" + differences.size() + "건). 코드나 yaml을 바꿨으면 생성기로 다시 만들어"
                    + " 읽어 본 뒤 복사한다(docs/api-examples/README.md '다시 만드는 명령'):\n- " + String.join("\n- ", differences));
        }
    }

    /** 요청을 보내고 yaml과 맞춰 본 뒤, 숨길 값을 바꿔 files에 담는다. 실패는 problems에 이름·이유만 적는다. */
    private void save(String name, Call call, int expected) throws Exception {
        String file = name + ".json";
        if (files.containsKey(file)) throw new IllegalStateException("예시 이름이 겹친다: " + name);
        String path = call.url().split("\\?", 2)[0];
        String operation = spec.operationFor(call.method(), call.url());
        if (operation == null) {
            problems.add(name + ": 요청 " + call.method() + " " + path + "에 맞는 yaml 작업이 없다");
            return;
        }
        MockHttpServletResponse res = mvc.perform(request(call)).andReturn().getResponse();
        if (res.getStatus() != expected) {
            problems.add(name + ": 상태 " + expected + " 기대, 실제 " + res.getStatus());
            return;
        }
        Map<String, Object> declared = spec.responses(operation).get(String.valueOf(expected));
        if (declared == null) {
            problems.add(name + ": " + operation + "에 " + expected + " 응답 선언이 없다");
            return;
        }
        JsonNode body;
        try {
            body = json.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            problems.add(name + ": 응답 본문이 JSON이 아니다");
            return;
        }
        List<String> schemaProblems = spec.bodyProblems(declared, body, "response.body");
        if (!schemaProblems.isEmpty()) {
            problems.add(name + ": yaml 스키마와 다르다 " + schemaProblems);
            return;
        }
        ObjectNode headers = json.createObjectNode();
        for (String h : spec.declaredHeaders(declared)) {
            if (res.getHeader(h) == null) {
                problems.add(name + ": 선언한 헤더가 없다 " + h);
                return;
            }
            headers.put(h, res.getHeader(h));
        }
        if (headers.has(HttpHeaders.LOCATION) && body.has("id")
                && !headers.get(HttpHeaders.LOCATION).asString().equals(path + "/" + body.get("id").asString())) {
            problems.add(name + ": Location이 '" + path + "/{응답 id}'가 아니다");
            return;
        }

        List<String> replaced = new ArrayList<>();
        ObjectNode root = json.createObjectNode();
        ObjectNode req = root.putObject("request");
        req.put("method", call.method());
        req.put("url", call.url());
        req.put("auth", call.auth());
        if (call.contentType() != null) req.put("contentType", call.contentType());
        req.set("body", requestBody(call, replaced));
        ObjectNode resp = root.putObject("response");
        resp.put("status", res.getStatus());
        resp.set("headers", headers);
        String verifiedTime = operation.equals(STATUS_OPERATION) ? FIXED_STATUS_TIME : FIXED_TIME;
        resp.set("body", hide(body, "response.body", replaced, verifiedTime));
        ObjectNode meta = root.putObject("meta");
        meta.put("operation", operation);
        meta.put("source", SOURCE);
        meta.put("note", NOTE);
        if (!replaced.isEmpty()) {
            ArrayNode list = meta.putArray("replaced");
            replaced.forEach(list::add);
        }
        files.put(file, writer.writeValueAsString(root) + "\n");
    }

    private MockHttpServletRequestBuilder request(Call call) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(HttpMethod.valueOf(call.method()), call.url());
        String token = tokens.get(call.auth());
        if (!tokens.containsKey(call.auth())) throw new IllegalArgumentException("모르는 인증 라벨: " + call.auth());
        if (token != null) b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (call.body() != null) {
            b.contentType(call.contentType() == null ? "application/json" : call.contentType());
            b.content(call.body() instanceof String s ? s : json.writeValueAsString(call.body()));
        }
        return b;
    }

    /** 파일에 적을 요청 본문. 비어 있지 않은 password는 설명만 남긴다. 문자열 본문에는 비밀번호를 넣지 않는다. */
    private JsonNode requestBody(Call call, List<String> replaced) {
        if (call.body() == null) return json.nullNode();
        if (call.body() instanceof String s) {
            if (s.toLowerCase(Locale.ROOT).contains("password")) throw new IllegalStateException("문자열 본문에 password를 넣지 않는다: " + call.url());
            return json.getNodeFactory().stringNode(s);
        }
        ObjectNode node = json.valueToTree(call.body());
        JsonNode pw = node.get("password");
        if (pw != null && pw.isString() && !pw.stringValue().isEmpty()) {
            if (call.passwordNote() == null) throw new IllegalStateException("비밀번호 설명이 없다: " + call.url());
            node.put("password", "<숨김: " + call.passwordNote() + ">");
            replaced.add("request.body.password");
        }
        return node;
    }

    /**
     * accessToken은 숨김 값으로, 새로 만든 팁(시드 7·8이 아닌 팁)의 시각은 고정값으로 바꾼다.
     * createdAt은 FIXED_TIME, verifiedAt은 등록 응답이면 FIXED_TIME, 상태 변경 응답이면 FIXED_STATUS_TIME.
     */
    private JsonNode hide(JsonNode node, String where, List<String> replaced, String verifiedTime) {
        if (node instanceof ObjectNode o) {
            if (o.get("accessToken") != null) {
                o.put("accessToken", HIDDEN_TOKEN);
                replaced.add(where + ".accessToken");
            }
            JsonNode id = o.get("id");
            if (id != null && id.isNumber() && o.has("verifiedAt") && !SEED_TIPS.contains(id.longValue())) {
                for (String f : new String[] {"createdAt", "verifiedAt"}) {
                    if (o.get(f) != null && o.get(f).isString()) {
                        o.put(f, f.equals("verifiedAt") ? verifiedTime : FIXED_TIME);
                        replaced.add(where + "." + f);
                    }
                }
            }
            for (String f : new ArrayList<>(o.propertyNames())) hide(o.get(f), where + "." + f, replaced, verifiedTime);
        } else if (node instanceof ArrayNode a) {
            for (int i = 0; i < a.size(); i++) hide(a.get(i), where + "[" + i + "]", replaced, verifiedTime);
        }
        return node;
    }

    /** 만든 예시와 docs/api-examples/*.json의 차이: 파일 이름과 처음 다른 JSON 경로만 적는다(값은 적지 않는다). */
    private List<String> differencesFromDocs() throws IOException {
        List<String> out = new ArrayList<>();
        Set<String> docs = new TreeSet<>();
        if (Files.isDirectory(DOCS)) {
            try (Stream<Path> s = Files.list(DOCS)) {
                s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(docs::add);
            }
        }
        for (String n : docs) if (!files.containsKey(n)) out.add(n + ": 생성기가 만들지 않는 파일(docs에만 있음)");
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path doc = DOCS.resolve(e.getKey());
            if (!docs.contains(e.getKey())) {
                out.add(e.getKey() + ": docs/api-examples에 없다(새 예시)");
                continue;
            }
            String at = firstDifference(json.readTree(e.getValue()), json.readTree(Files.readString(doc, StandardCharsets.UTF_8)), "$");
            if (at != null) out.add(e.getKey() + ": " + at + " 가 다르다");
        }
        return out;
    }

    private static String firstDifference(JsonNode a, JsonNode b, String where) {
        if (a.equals(b)) return null;
        if (a.isObject() && b.isObject()) {
            Set<String> names = new LinkedHashSet<>(a.propertyNames());
            names.addAll(b.propertyNames());
            for (String n : names) {
                if (!a.has(n) || !b.has(n)) return where + "." + n;
                String d = firstDifference(a.get(n), b.get(n), where + "." + n);
                if (d != null) return d;
            }
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) return where + "(원소 수)";
            for (int i = 0; i < a.size(); i++) {
                String d = firstDifference(a.get(i), b.get(i), where + "[" + i + "]");
                if (d != null) return d;
            }
        }
        return where;
    }

    /** 다른 예시에 쓸 토큰을 받는다(이 요청은 저장하지 않는다). */
    private String login(String username) throws Exception {
        MockHttpServletResponse res = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(body("username", username, "password", ApiTestSupport.SAMPLE_PASSWORD))))
                .andReturn().getResponse();
        if (res.getStatus() != 200) fail(username + " 로그인 실패: 상태 " + res.getStatus());
        return json.readTree(res.getContentAsString(StandardCharsets.UTF_8)).get("accessToken").stringValue();
    }

    private static Map<String, Object> body(Object... kv) {
        return ApiTestSupport.body(kv);
    }

    private static void clearOut() throws IOException {
        Files.deleteIfExists(CHANGES);
        if (Files.exists(OUT)) {
            try (Stream<Path> files = Files.walk(OUT)) {
                for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        Files.createDirectories(OUT);
    }
}
