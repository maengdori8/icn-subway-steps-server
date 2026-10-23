package org.skilltransfer.icnsubway;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/api-spec.yaml을 SnakeYAML로 읽어 테스트에 필요한 것만 꺼낸다.
 * ApiExamplesGenerator(예시 만들기·비교)와 ApiContractDocTest(어긋남 검사)가 같이 쓴다.
 */
final class ApiSpec {
    static final Path FILE = Path.of("docs/api-spec.yaml");
    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    /** yaml 예시 값(Map·List·문자열·숫자)을 JSON 트리로 바꿀 때만 쓴다. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Object> root;

    private ApiSpec(Map<String, Object> root) {
        this.root = root;
    }

    static ApiSpec load() throws IOException {
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            return new ApiSpec(map(new Yaml(new SafeConstructor(new LoaderOptions())).load(r)));
        }
    }

    Map<String, Object> root() {
        return root;
    }

    /** 'GET /api/routes' 꼴의 작업 이름 → 작업. yaml에 적은 순서대로. */
    Map<String, Map<String, Object>> operations() {
        Map<String, Map<String, Object>> ops = new LinkedHashMap<>();
        map(root.get("paths")).forEach((path, item) -> map(item).forEach((method, op) -> {
            if (METHODS.contains(method)) ops.put(method.toUpperCase(Locale.ROOT) + " " + path, map(op));
        }));
        return ops;
    }

    /**
     * 요청(메서드, 주소)이 yaml의 어느 작업인지. 쿼리는 빼고, 경로 변수 {x}는 '/'가 없는 한 칸과 맞춘다.
     * 맞는 작업이 없거나 둘 이상이면 null.
     */
    String operationFor(String method, String url) {
        String path = url.split("\\?", 2)[0];
        List<String> hits = new ArrayList<>();
        for (String op : operations().keySet()) {
            String[] mp = op.split(" ", 2);
            if (!mp[0].equalsIgnoreCase(method)) continue;
            List<String> parts = new ArrayList<>();
            for (String seg : mp[1].split("/", -1)) parts.add(seg.matches("\\{[^/{}]+}") ? "[^/]+" : Pattern.quote(seg));
            if (path.matches(String.join("/", parts))) hits.add(op);
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    /** 그 작업이 선언한 응답: 상태 코드('200') → 응답($ref는 풀어서). 없는 작업이면 빈 Map. */
    Map<String, Map<String, Object>> responses(String operation) {
        Map<String, Object> op = operations().get(operation);
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (op == null) return out;
        map(op.get("responses")).forEach((status, response) -> out.put(String.valueOf(status), resolve(map(response))));
        return out;
    }

    /** 요청 본문의 application/json(스키마·예시). 본문이 없는 작업이면 빈 Map. */
    Map<String, Object> requestMedia(String operation) {
        Map<String, Object> op = operations().get(operation);
        if (op == null) return Map.of();
        return media(resolve(map(op.get("requestBody"))));
    }

    /** 응답이나 요청 본문의 application/json 항목(schema, example, examples). */
    Map<String, Object> media(Map<String, Object> responseOrBody) {
        return map(map(responseOrBody.get("content")).get("application/json"));
    }

    /** application/json 항목에 적은 예시 값(example, examples.*.value)을 JSON 트리로. */
    List<JsonNode> examples(Map<String, Object> media) {
        List<JsonNode> out = new ArrayList<>();
        if (media.containsKey("example")) out.add(JSON.valueToTree(media.get("example")));
        map(media.get("examples")).values().forEach(e -> out.add(JSON.valueToTree(resolve(map(e)).get("value"))));
        return out;
    }

    /** 응답 본문이 그 응답의 application/json 스키마에 맞는지. 어긋난 곳을 '경로: 이유'로 돌려준다(값은 적지 않는다). */
    List<String> bodyProblems(Map<String, Object> response, JsonNode body, String where) {
        List<String> problems = new ArrayList<>();
        Object schema = media(response).get("schema");
        if (schema == null) {
            if (body != null && !body.isMissingNode() && !body.isNull()) problems.add(where + ": yaml에 JSON 본문 선언이 없다");
            return problems;
        }
        if (body == null || body.isMissingNode()) {
            problems.add(where + ": 본문이 없다");
            return problems;
        }
        check(schema, body, where, problems, true);
        return problems;
    }

    /** 값이 스키마에 맞는지 본다: $ref·allOf, type, nullable, enum, format(date-time·int32·int64), minimum, pattern, required, properties, items. */
    void check(Object schema, JsonNode node, String where, List<String> problems) {
        check(schema, node, where, problems, true);
    }

    /**
     * whole=false는 allOf의 한 부분으로 볼 때다. 이때는 다른 부분의 필드를 'yaml에 없는 필드'로 보지 않는다.
     * properties를 정한 객체에 그 밖의 필드가 오면 문제로 본다(실제 응답에 yaml이 모르는 필드가 있으면 yaml을 고쳐야 하므로).
     */
    private void check(Object schemaNode, JsonNode node, String where, List<String> problems, boolean whole) {
        Map<String, Object> s = resolve(map(schemaNode));
        if (node.isNull()) {
            if (!Boolean.TRUE.equals(s.get("nullable"))) problems.add(where + ": null이면 안 된다(nullable 아님)");
            return;
        }
        if (s.get("allOf") instanceof List<?> parts) {
            for (Object part : parts) check(part, node, where, problems, false);
        }
        Object type = s.get("type");
        if (type != null && !hasType(String.valueOf(type), node)) {
            problems.add(where + ": 자료형이 " + type + "이어야 한다");
            return;
        }
        if (s.get("enum") instanceof List<?> values && values.stream().map(String::valueOf).noneMatch(scalar(node)::equals)) {
            problems.add(where + ": enum " + values + " 밖의 값");
        }
        Object format = s.get("format");
        if ("date-time".equals(format) && node.isString()) {
            try {
                OffsetDateTime.parse(node.stringValue());
            } catch (DateTimeParseException e) {
                problems.add(where + ": date-time(ISO-8601) 형식이 아니다");
            }
        }
        if ("int32".equals(format) && node.isIntegralNumber() && !node.canConvertToInt()) problems.add(where + ": int32 범위 밖");
        if ("int64".equals(format) && node.isIntegralNumber() && !node.canConvertToLong()) problems.add(where + ": int64 범위 밖");
        if (s.get("minimum") instanceof Number min && node.isNumber() && node.doubleValue() < min.doubleValue()) {
            problems.add(where + ": minimum " + min + "보다 작다");
        }
        if (s.get("pattern") instanceof String p && node.isString() && !Pattern.compile(p).matcher(node.stringValue()).find()) {
            problems.add(where + ": pattern에 맞지 않는다");
        }
        if (node.isObject()) {
            if (s.get("required") instanceof List<?> required) {
                for (Object r : required) {
                    if (!node.has(String.valueOf(r))) problems.add(where + ": 필수 필드 '" + r + "'가 없다");
                }
            }
            Map<String, Object> props = map(s.get("properties"));
            Object extra = s.get("additionalProperties");
            Set<String> known = whole ? propertyNames(s) : Set.of();
            for (String field : node.propertyNames()) {
                JsonNode value = node.get(field);
                if (props.containsKey(field)) check(props.get(field), value, where + "." + field, problems, true);
                else if (extra instanceof Map<?, ?>) check(extra, value, where + "." + field, problems, true);
                else if (whole && !known.contains(field) && (Boolean.FALSE.equals(extra) || !known.isEmpty())) {
                    problems.add(where + ": yaml에 없는 필드 '" + field + "'");
                }
            }
        }
        if (node.isArray() && s.get("items") != null) {
            for (int i = 0; i < node.size(); i++) check(s.get("items"), node.get(i), where + "[" + i + "]", problems, true);
        }
    }

    /** 이 스키마와 allOf 부분들이 정한 필드 이름 전부. */
    private Set<String> propertyNames(Map<String, Object> schema) {
        Map<String, Object> s = resolve(schema);
        Set<String> out = new LinkedHashSet<>(map(s.get("properties")).keySet());
        if (s.get("allOf") instanceof List<?> parts) parts.forEach(p -> out.addAll(propertyNames(map(p))));
        return out;
    }

    private static boolean hasType(String type, JsonNode node) {
        return switch (type) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "string" -> node.isString();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            default -> false;
        };
    }

    private static String scalar(JsonNode node) {
        return node.isString() ? node.stringValue() : node.toString();
    }

    /** 응답에 선언한 헤더 이름(예: 201의 Location). */
    Set<String> declaredHeaders(Map<String, Object> response) {
        return new LinkedHashSet<>(map(response.get("headers")).keySet());
    }

    /** {$ref: '#/components/...'}를 가리키는 대상으로 바꾼다. 이 파일 안의 참조만 읽는다. */
    Map<String, Object> resolve(Map<String, Object> node) {
        Map<String, Object> cur = node;
        for (int i = 0; cur.get("$ref") instanceof String ref; i++) {
            if (i > 20 || !ref.startsWith("#/")) throw new IllegalStateException("읽을 수 없는 $ref: " + ref);
            Object target = root;
            for (String part : ref.substring(2).split("/")) target = map(target).get(part);
            if (target == null) throw new IllegalStateException("$ref가 가리키는 곳이 없다: " + ref);
            cur = map(target);
        }
        return cur;
    }

    /** yaml의 매핑을 Map으로 본다. null이면 빈 Map. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        if (o == null) return Map.of();
        if (!(o instanceof Map<?, ?>)) throw new IllegalStateException("매핑이 아니다: " + o.getClass().getSimpleName());
        Map<String, Object> out = new LinkedHashMap<>();
        ((Map<Object, Object>) o).forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }
}
