package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * 문서가 코드와 어긋나지 않게 지키는 검사. 서버를 띄우지 않고 파일과 핸들러 매핑만 본다.
 * Gradle은 docs/를 test의 입력으로 보지 않는다. 문서만 고쳤으면 --rerun을 붙여야 이 검사가 실제로 돈다.
 * 1) 코드의 /api/** 엔드포인트(메서드+경로)와 docs/api-spec.yaml paths가 양쪽으로 같다. 계약 밖 주소는 NOT_IN_CONTRACT에 적는다.
 * 2) docs/api-reference.md: 2절 표의 행과 3절 제목이 yaml의 작업과 같고(번호도 서로 같음), 3절 상태 코드 표가 yaml 응답과 같다.
 *    예시 링크와 docs/api-examples의 파일이 양쪽으로 맞고, 링크한 파일의 작업이 그 절의 작업이다.
 * 3) docs/api-examples/*.json: 요청이 meta.operation의 메서드·경로이고, 상태가 선언돼 있고, 본문이 스키마에 맞고,
 *    오류 code·message가 yaml 예시와 같고, 선언한 모든 (작업, 상태)에 예시가 있고, 시각·토큰·비밀번호가 숨겨져 있다.
 * 4) yaml의 예시 값이 스키마에 맞고, 오류 예시의 code·message가 예시 파일(지금 코드의 응답)에 실제로 나온다.
 * 5) docs/samples/의 실제 응답 표본에 토큰·비밀번호가 남아 있지 않다.
 * 6) yaml 스키마에 모르는 키워드가 없다({ } 흐름 표기 안의 쉼표 때문에 설명이 잘리고 엉뚱한 키가 생기는 실수를 잡는다).
 * 예시 파일이 지금 코드의 응답과 같은지는 ApiExamplesGenerator가 평소 test에서 비교한다.
 */
class ApiContractDocTest extends ApiTestSupport {
    /** 코드에는 있지만 계약(paths)에 넣지 않는 /api/** 주소와 이유. yaml 권한 요약에도 적어야 한다. */
    static final Map<String, String> NOT_IN_CONTRACT = Map.of(
            "GET /api/health", "시작 프로젝트 점검용(README·실행 기록이 서버 실행 확인에 쓴다)");
    static final Path REFERENCE = Path.of("docs/api-reference.md");
    static final Path EXAMPLES = Path.of("docs/api-examples");
    static final Path SAMPLES = Path.of("docs/samples");
    static final String LOGIN = "POST /api/auth/login";
    static final Set<String> AUTH_LABELS = Set.of("없음", "student01 토큰", "student02 토큰", "무효 토큰");
    static final Set<String> SCHEMA_KEYWORDS = Set.of("type", "format", "required", "properties", "items", "enum", "description",
            "nullable", "minimum", "maximum", "pattern", "example", "allOf", "additionalProperties", "$ref");
    /** 상태 코드별로 나올 수 있는 Error.code(401은 allowedCodes가 작업에 따라 정한다). */
    static final Map<Integer, Set<String>> CODES = Map.of(
            400, Set.of("VALIDATION_FAILED", "INVALID_REQUEST_BODY"),
            403, Set.of("FORBIDDEN"),
            404, Set.of("NOT_FOUND"),
            409, Set.of("USERNAME_TAKEN"),
            415, Set.of("UNSUPPORTED_MEDIA_TYPE"));
    /** JWT처럼 생긴 글자(헤더.페이로드). 예시·표본 파일에 실제 토큰이 남았는지 본다. */
    static final Pattern JWT_LIKE = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    static final Pattern DATE_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T.*");
    static final Pattern HEADING = Pattern.compile("^### (A\\d+)\\. ([A-Z]+) (\\S+) — ");
    static final Pattern STATUS_ROW = Pattern.compile("^\\| (\\d{3}) \\| ([^|]*) \\|");
    static final Pattern EXAMPLE_LINK = Pattern.compile("]\\(api-examples/([^)/]+\\.json)\\)");

    @Autowired ApplicationContext context;

    @Test
    void codeEndpointsMatchSpecPaths() throws Exception {
        Set<String> code = new TreeSet<>();
        RequestMappingHandlerMapping mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        mapping.getHandlerMethods().keySet().forEach(info -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                if (methods.isEmpty()) code.add("* " + path); // 메서드를 정하지 않은 매핑도 드러나게
                methods.forEach(m -> code.add(m.name() + " " + path));
            }
        });
        ApiSpec spec = ApiSpec.load();
        Set<String> documented = spec.operations().keySet();

        assertThat(code).as("예외 목록의 주소는 코드에 있어야 한다(없어졌으면 목록에서 뺀다)").containsAll(NOT_IN_CONTRACT.keySet());
        assertThat(documented).as("예외 목록의 주소는 yaml paths에 없어야 한다").doesNotContainAnyElementsOf(NOT_IN_CONTRACT.keySet());
        Set<String> contract = new TreeSet<>(code);
        contract.removeAll(NOT_IN_CONTRACT.keySet());
        assertThat(documented)
                .as("코드의 /api/** 엔드포인트와 docs/api-spec.yaml paths가 같아야 한다(새 API는 yaml에 먼저 적는다)")
                .containsExactlyInAnyOrderElementsOf(contract);
        String description = String.valueOf(ApiSpec.map(spec.root().get("info")).get("description"));
        for (String op : NOT_IN_CONTRACT.keySet()) {
            assertThat(description).as("계약 밖 주소는 yaml info.description에 적는다: " + op).contains(op);
        }
    }

    @Test
    void apiReferenceMatchesSpec() throws Exception {
        ApiSpec spec = ApiSpec.load();
        Map<String, String> table = new LinkedHashMap<>();     // 2절 표: A번호 → 작업
        Map<String, String> success = new LinkedHashMap<>();   // 2절 표의 '성공' 칸
        Map<String, String> headings = new LinkedHashMap<>();  // 3절 제목: A번호 → 작업
        Map<String, List<String[]>> rows = new LinkedHashMap<>();  // 3절 상태 코드 표: A번호 → [상태, code 칸]
        Map<String, List<String>> linksIn = new LinkedHashMap<>(); // 3절에서 링크한 예시: A번호 → 파일
        Set<String> links = new TreeSet<>();
        String section = "";
        String current = null;
        for (String line : Files.readAllLines(REFERENCE, StandardCharsets.UTF_8)) {
            if (line.startsWith("## ")) {
                section = line;
                current = null;
            }
            if (section.startsWith("## 2.") && line.startsWith("| A")) {
                String[] c = line.split("\\|");
                assertThat(table.put(c[1].trim(), withoutQuery(c[4].trim()))).as("2절 표의 번호가 겹친다: " + c[1].trim()).isNull();
                success.put(c[1].trim(), c[6].trim());
            }
            if (section.startsWith("## 3.")) {
                Matcher h = HEADING.matcher(line);
                if (h.find()) {
                    current = h.group(1);
                    headings.put(current, h.group(2) + " " + withoutQuery(h.group(3)));
                    rows.put(current, new ArrayList<>());
                    linksIn.put(current, new ArrayList<>());
                } else if (line.startsWith("### ")) {
                    current = null;
                }
                Matcher r = STATUS_ROW.matcher(line);
                if (current != null && r.find()) rows.get(current).add(new String[] {r.group(1), r.group(2).trim()});
            }
            Matcher l = EXAMPLE_LINK.matcher(line);
            while (l.find()) {
                links.add(l.group(1));
                if (current != null) linksIn.get(current).add(l.group(1));
            }
        }

        assertThat(new TreeSet<>(table.values())).as("2절 표의 '메서드 경로'가 yaml의 작업과 같아야 한다(쿼리 표기는 뺀다)")
                .containsExactlyInAnyOrderElementsOf(spec.operations().keySet());
        assertThat(table.values()).as("2절 표에 같은 작업이 두 번 나온다").doesNotHaveDuplicates();
        assertThat(headings).as("3절 제목(### A?. 메서드 경로 — )이 2절 표와 같은 번호·작업이어야 한다").isEqualTo(table);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : headings.entrySet()) {
            String a = e.getKey();
            String op = e.getValue();
            Map<String, Map<String, Object>> declared = spec.responses(op);
            Set<String> statuses = new TreeSet<>();
            for (String[] row : rows.get(a)) {
                statuses.add(row[0]);
                int status = Integer.parseInt(row[0]);
                for (String c : row[1].split("/")) {
                    String code = c.trim();
                    boolean ok = status < 300 ? code.equals("—") : allowedCodes(op, status).contains(code);
                    if (!ok) problems.add(a + " 상태 코드 표: " + status + "의 code 칸 '" + code + "'");
                }
            }
            if (!statuses.equals(new TreeSet<>(declared.keySet()))) {
                problems.add(a + " 상태 코드 표 " + statuses + " ≠ yaml " + op + " 응답 " + new TreeSet<>(declared.keySet()));
            }
            Matcher m = Pattern.compile("\\d{3}").matcher(success.getOrDefault(a, ""));
            if (!m.find() || !declared.containsKey(m.group()) || !m.group().startsWith("2")) {
                problems.add(a + " 2절 표의 성공 칸 '" + success.get(a) + "'이 yaml의 2xx 응답이 아니다");
            }
            for (String file : linksIn.get(a)) {
                Path p = EXAMPLES.resolve(file);
                if (!Files.exists(p)) continue; // 아래 links 검사에서 알린다
                String linkedOp = json.readTree(Files.readString(p, StandardCharsets.UTF_8)).path("meta").path("operation").asString();
                if (!linkedOp.equals(op)) problems.add(a + "의 예시 " + file + "는 " + linkedOp + "의 예시다");
            }
        }
        for (String file : links) {
            if (!Files.exists(EXAMPLES.resolve(file))) problems.add("링크한 예시 파일이 없다: api-examples/" + file);
        }
        for (Path file : exampleFiles()) {
            if (!links.contains(file.getFileName().toString())) problems.add("예시 파일을 링크하지 않았다: api-examples/" + file.getFileName());
        }
        assertThat(problems).as("docs/api-reference.md와 yaml·예시 파일의 어긋남").isEmpty();
    }

    @Test
    void examplesMatchSpecAndHideSecrets() throws Exception {
        ApiSpec spec = ApiSpec.load();
        List<Path> files = exampleFiles();
        assertThat(files).as("docs/api-examples/*.json이 있어야 한다(ApiExamplesGenerator로 만든다)").isNotEmpty();
        Set<String> covered = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (JWT_LIKE.matcher(text).find()) problems.add(name + ": 토큰처럼 생긴 값이 남아 있다");
            JsonNode root = json.readTree(text);
            JsonNode request = root.path("request");
            JsonNode response = root.path("response");
            JsonNode meta = root.path("meta");
            JsonNode body = response.path("body");

            checkTokens(root, name, problems, true);
            JsonNode requestBody = request.path("body");
            JsonNode password = requestBody.path("password");
            if (password.isString() && !password.stringValue().isEmpty() && !isHidden(password)) {
                problems.add(name + ": request.body.password가 숨겨지지 않았다");
            }
            if (requestBody.isString() && requestBody.stringValue().toLowerCase(Locale.ROOT).contains("password")) {
                problems.add(name + ": 문자열 요청 본문에 password가 있다");
            }
            checkNoPasswordFields(body, name, problems);

            String operation = meta.path("operation").asString();
            if (!spec.operations().containsKey(operation)) {
                problems.add(name + ": meta.operation '" + operation + "'이 yaml에 없다");
                continue;
            }
            if (!meta.path("note").asString().contains("실제 응답이 아님")) problems.add(name + ": meta.note에 '실제 응답이 아님'이 없다");
            if (!AUTH_LABELS.contains(request.path("auth").asString())) problems.add(name + ": 모르는 request.auth 라벨");
            String url = request.path("url").asString();
            if (!operation.equals(spec.operationFor(request.path("method").asString(), url))) {
                problems.add(name + ": request.method·url이 meta.operation(" + operation + ")의 메서드·경로가 아니다");
            }

            int status = response.path("status").asInt();
            Map<String, Object> declared = spec.responses(operation).get(String.valueOf(status));
            if (declared == null) {
                problems.add(name + ": " + operation + "에 " + status + " 응답이 선언돼 있지 않다");
                continue;
            }
            covered.add(operation + " " + status);
            spec.bodyProblems(declared, body, "response.body").forEach(p -> problems.add(name + ": " + p));
            for (String header : spec.declaredHeaders(declared)) {
                if (!response.path("headers").has(header)) problems.add(name + ": 선언한 헤더 " + header + "가 없다");
            }
            JsonNode location = response.path("headers").path("Location");
            if (!location.isMissingNode() && !location.asString().equals(url.split("\\?", 2)[0] + "/" + body.path("id").asString())) {
                problems.add(name + ": Location이 요청 경로 + '/' + 응답 id가 아니다");
            }
            if (status >= 400) checkErrorExample(spec, operation, status, declared, body, name, problems);
            checkTimes(body, "response.body", replacedPaths(meta), name, problems);
        }
        assertThat(problems).as("예시 파일과 yaml의 어긋남·숨겨야 할 값").isEmpty();
        Set<String> declaredAll = new TreeSet<>();
        spec.operations().keySet().forEach(op -> spec.responses(op).keySet().forEach(s -> declaredAll.add(op + " " + s)));
        assertThat(covered).as("yaml에 선언한 모든 (작업, 상태 코드)에 예시가 하나 이상 있어야 한다(ApiExamplesGenerator에 요청을 더한다)")
                .containsExactlyInAnyOrderElementsOf(declaredAll);
    }

    @Test
    void specExamplesMatchSchemasAndExampleFiles() throws Exception {
        ApiSpec spec = ApiSpec.load();
        Map<String, List<JsonNode>> bodies = new LinkedHashMap<>(); // '작업 상태' → 예시 파일의 응답 본문
        Set<String> fileMessages = new HashSet<>();                 // 예시 파일에 나온 'code message'
        for (Path file : exampleFiles()) {
            JsonNode root = json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            JsonNode body = root.path("response").path("body");
            bodies.computeIfAbsent(root.path("meta").path("operation").asString() + " " + root.path("response").path("status").asInt(),
                    k -> new ArrayList<>()).add(body);
            if (body.has("code")) fileMessages.add(body.path("code").asString() + " " + body.path("message").asString());
        }
        List<String> problems = new ArrayList<>();
        for (String op : spec.operations().keySet()) {
            Map<String, Object> request = spec.requestMedia(op);
            for (JsonNode example : spec.examples(request)) spec.check(request.get("schema"), example, op + " 요청 예시", problems);
            spec.responses(op).forEach((status, response) -> {
                Map<String, Object> media = spec.media(response);
                String where = op + " " + status + " 예시";
                int st = Integer.parseInt(status);
                for (JsonNode example : spec.examples(media)) {
                    spec.check(media.get("schema"), example, where, problems);
                    if (st >= 400) {
                        String code = example.path("code").asString();
                        if (!allowedCodes(op, st).contains(code)) problems.add(where + ": " + st + "에 맞지 않는 code " + code);
                        if (!fileMessages.contains(code + " " + example.path("message").asString())) {
                            problems.add(where + ": code·message가 어느 예시 파일(지금 코드의 응답)에도 없다");
                        }
                    } else if (bodies.getOrDefault(op + " " + status, List.of()).stream().noneMatch(example::equals)) {
                        problems.add(where + ": 같은 작업·상태의 예시 파일 본문과 같지 않다");
                    }
                }
            });
        }
        assertThat(problems).as("yaml 예시가 스키마·지금 코드의 응답과 맞아야 한다").isEmpty();
    }

    @Test
    void samplesHideSecrets() throws Exception {
        List<String> problems = new ArrayList<>();
        if (Files.isDirectory(SAMPLES)) {
            try (Stream<Path> s = Files.walk(SAMPLES)) {
                for (Path file : s.filter(Files::isRegularFile).sorted().toList()) {
                    String name = SAMPLES.relativize(file).toString();
                    if (name.equalsIgnoreCase("README.md")) continue;
                    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    if (JWT_LIKE.matcher(text).find()) problems.add(name + ": 토큰처럼 생긴 값이 있다");
                    if (name.endsWith(".json")) {
                        JsonNode root;
                        try {
                            root = json.readTree(text);
                        } catch (RuntimeException e) {
                            problems.add(name + ": JSON으로 읽을 수 없다");
                            continue;
                        }
                        checkTokens(root, name, problems, false);
                        checkPasswordValues(root, name, problems);
                    }
                }
            }
        }
        assertThat(problems).as("docs/samples의 실제 응답 표본에서 숨겨야 할 값(samples/README.md '저장하기 전에 숨긴다')").isEmpty();
    }

    /** 오류 예시: code가 그 상태에 맞고, (code, message)가 같은 작업·상태의 yaml 예시 중 하나와 같고, 같은 errors 키의 문구가 yaml 예시와 같다. */
    private static void checkErrorExample(ApiSpec spec, String operation, int status, Map<String, Object> declared, JsonNode body,
                                          String name, List<String> problems) {
        String code = body.path("code").asString();
        if (!allowedCodes(operation, status).contains(code)) problems.add(name + ": " + status + "에 맞지 않는 code " + code);
        List<JsonNode> yamlExamples = spec.examples(spec.media(declared));
        boolean same = yamlExamples.stream()
                .anyMatch(e -> e.path("code").equals(body.path("code")) && e.path("message").equals(body.path("message")));
        if (!same) problems.add(name + ": code·message가 yaml의 " + operation + " " + status + " 예시 어느 것과도 같지 않다");
        for (JsonNode e : yamlExamples) {
            if (!e.path("code").equals(body.path("code"))) continue;
            for (Map.Entry<String, JsonNode> err : e.path("errors").properties()) {
                JsonNode actual = body.path("errors").path(err.getKey());
                if (!actual.isMissingNode() && !actual.equals(err.getValue())) {
                    problems.add(name + ": errors." + err.getKey() + " 문구가 yaml 예시와 다르다");
                }
            }
        }
    }

    /** 그 작업·상태에 나올 수 있는 Error.code. 401은 로그인이면 LOGIN_FAILED, 그 밖은 UNAUTHORIZED. */
    static Set<String> allowedCodes(String operation, int status) {
        if (status == 401) return operation.equals(LOGIN) ? Set.of("LOGIN_FAILED") : Set.of("UNAUTHORIZED");
        return CODES.getOrDefault(status, Set.of());
    }

    /** 응답의 date-time 값은 시드 팁 시각이거나, meta.replaced에 적은 고정값이어야 한다(실행 시각이 남으면 안 된다). */
    private static void checkTimes(JsonNode node, String where, Set<String> replaced, String name, List<String> problems) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) checkTimes(e.getValue(), where + "." + e.getKey(), replaced, name, problems);
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) checkTimes(node.get(i), where + "[" + i + "]", replaced, name, problems);
        } else if (node.isString() && DATE_TIME.matcher(node.stringValue()).matches()) {
            String v = node.stringValue();
            boolean fixed = v.equals(ApiExamplesGenerator.FIXED_TIME) || v.equals(ApiExamplesGenerator.FIXED_STATUS_TIME);
            if (fixed ? !replaced.contains(where) : !ApiExamplesGenerator.SEED_TIMES.contains(v)) {
                problems.add(name + ": " + where + "가 시드 시각도, meta.replaced에 적은 고정값도 아니다");
            }
        }
    }

    private static Set<String> replacedPaths(JsonNode meta) {
        Set<String> out = new HashSet<>();
        JsonNode replaced = meta.path("replaced");
        if (replaced.isArray()) for (JsonNode v : replaced.values()) out.add(v.asString());
        return out;
    }

    private static boolean isHidden(JsonNode v) {
        return v.isString() && v.stringValue().startsWith("<숨김");
    }

    /**
     * accessToken은 어디에 있든 숨김 값이어야 한다. exact(예시 파일)면 생성기의 숨김 문구와 똑같아야 하고 Authorization 항목은
     * 아예 없어야 한다(request.auth 라벨만 쓴다). 표본 파일은 '&lt;숨김'으로 시작하면 된다(Authorization도 같다).
     */
    private static void checkTokens(JsonNode node, String where, List<String> problems, boolean exact) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (key.equals("accesstoken")) {
                    boolean ok = exact ? ApiExamplesGenerator.HIDDEN_TOKEN.equals(e.getValue().asString()) : isHidden(e.getValue());
                    if (!ok) problems.add(where + ": " + e.getKey() + " 값이 숨김 값이 아니다");
                } else if (key.equals("authorization")) {
                    if (exact) problems.add(where + ": Authorization 항목을 적지 않는다(request.auth 라벨만 쓴다)");
                    else if (!isHidden(e.getValue())) problems.add(where + ": " + e.getKey() + " 값이 숨김 값이 아니다");
                }
                checkTokens(e.getValue(), where, problems, exact);
            }
        } else if (node.isArray()) {
            for (JsonNode v : node.values()) checkTokens(v, where, problems, exact);
        }
    }

    /** 표본 파일: password로 시작하는 항목의 값은 비어 있거나 숨김 값이어야 한다(errors 안은 안내 문구라서 뺀다). */
    private static void checkPasswordValues(JsonNode node, String where, List<String> problems) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getKey().equals("errors")) continue;
                JsonNode v = e.getValue();
                if (e.getKey().toLowerCase(Locale.ROOT).startsWith("password")
                        && !(v.isNull() || (v.isString() && (v.stringValue().isEmpty() || isHidden(v))))) {
                    problems.add(where + ": " + e.getKey() + " 값이 숨겨지지 않았다");
                }
                checkPasswordValues(v, where, problems);
            }
        } else if (node.isArray()) {
            for (JsonNode v : node.values()) checkPasswordValues(v, where, problems);
        }
    }

    /**
     * 응답 본문에는 비밀번호·해시 항목이 없어야 한다. errors의 키는 입력 항목 이름(password 포함)이고 값은 안내 문구라서
     * 키 이름은 보지 않는다. 다만 문자열이 아닌 값이 있으면 문제로 적고 그 안까지 본다.
     */
    private static void checkNoPasswordFields(JsonNode node, String where, List<String> problems) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getKey().equals("errors") && e.getValue().isObject()) {
                    for (Map.Entry<String, JsonNode> err : e.getValue().properties()) {
                        if (err.getValue().isString()) continue;
                        problems.add(where + ": errors." + err.getKey() + " 값이 문자열이 아니다");
                        checkNoPasswordFields(err.getValue(), where, problems);
                    }
                    continue;
                }
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (key.startsWith("password") || key.contains("hash")) problems.add(where + ": 응답에 " + e.getKey() + " 항목이 있다");
                checkNoPasswordFields(e.getValue(), where, problems);
            }
        } else if (node.isArray()) {
            for (JsonNode v : node.values()) checkNoPasswordFields(v, where, problems);
        }
    }

    @Test
    void specSchemasUseOnlyKnownKeywords() throws Exception {
        List<String> unknown = new ArrayList<>();
        Map<String, Object> root = ApiSpec.load().root();
        walk(root, "$", unknown);
        ApiSpec.map(ApiSpec.map(root.get("components")).get("schemas"))
                .forEach((name, schema) -> checkSchema(schema, "$.components.schemas." + name, unknown));
        assertThat(unknown).as("스키마에 모르는 키워드(흐름 표기 { } 안의 설명에 쉼표가 있으면 따옴표로 감싼다)").isEmpty();
    }

    private static void walk(Object node, String where, List<String> unknown) {
        if (node instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if ("schema".equals(String.valueOf(k))) checkSchema(v, where + ".schema", unknown);
                else walk(v, where + "." + k, unknown);
            });
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) walk(l.get(i), where + "[" + i + "]", unknown);
        }
    }

    private static void checkSchema(Object node, String where, List<String> unknown) {
        if (!(node instanceof Map<?, ?> m)) return;
        m.forEach((k, v) -> {
            String key = String.valueOf(k);
            if (!SCHEMA_KEYWORDS.contains(key)) unknown.add(where + ": " + key);
            if (key.equals("properties") && v instanceof Map<?, ?> props) {
                props.forEach((p, s) -> checkSchema(s, where + ".properties." + p, unknown));
            }
            if (key.equals("items") || key.equals("additionalProperties")) checkSchema(v, where + "." + key, unknown);
            if (key.equals("allOf") && v instanceof List<?> parts) parts.forEach(s -> checkSchema(s, where + ".allOf", unknown));
        });
    }

    private static String withoutQuery(String path) {
        return path.split("\\?", 2)[0];
    }

    private static List<Path> exampleFiles() throws Exception {
        if (!Files.isDirectory(EXAMPLES)) return List.of();
        try (Stream<Path> s = Files.list(EXAMPLES)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }
}
