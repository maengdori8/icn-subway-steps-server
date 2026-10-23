# API 명세서 · ICN Subway Steps 서버

문서 정보: 2026-10-21 · 기준 docs/api-spec.yaml · 작성 Claude Code · 검토: (본인 이름)

## 0. 읽는 법

- 기준은 [api-spec.yaml](api-spec.yaml)(OpenAPI 3.0.3)이다. 이 문서는 그 내용을 풀어 쓴 안내다. 둘이 다르면 yaml이 맞고 이 문서를 고친다.
- 화면·기능 흐름과 앱이 오류를 어떻게 보여 주는지는 [functional-spec.md](functional-spec.md)(F01~F10), 표·열·시드는 [data-model.md](data-model.md), 토큰·키는 [auth-design.md](auth-design.md)에 있다.
- 예시 JSON은 [api-examples/](api-examples/README.md)에 있다. 자동 테스트(MockMvc)로 만든 것이며 실행 서버의 실제 응답이 아니다(5절).
- 테스트 ApiContractDocTest가 이 문서를 yaml과 맞춰 본다: 2절 표의 행과 3절 제목(번호·메서드 경로), 3절 상태 코드 표의 상태와 code, 예시 링크(링크한 파일이 있는지, 모든 예시 파일을 링크했는지, 그 절의 API 예시인지). 예시 파일이 지금 코드의 응답과 같은지는 ApiExamplesGenerator가 평소 테스트에서 비교한다.
- 이 문서만 고친 뒤에는 `.\gradlew.bat test --offline --rerun --tests '*ApiContractDocTest*'`처럼 `--rerun`을 붙인다. Gradle은 docs/를 테스트 입력으로 보지 않아서, 붙이지 않으면 테스트를 건너뛰고 BUILD SUCCESSFUL만 나온다.
- 표의 A1~A9는 이 문서에서 쓰는 API 번호다.

## 1. 공통

### 주소와 인증

| 항목 | 값 |
| --- | --- |
| 기본 주소 | PC `http://127.0.0.1:8097`, Android 에뮬레이터 `http://10.0.2.2:8097`(서버가 127.0.0.1에만 열려 실제 휴대폰은 접속 불가) |
| 인증 방식 | 로그인 응답의 accessToken을 `Authorization: Bearer <accessToken>` 헤더로 보낸다 |
| 토큰 | JWT HS256, 발급자 skilltransfer-ch07, 1800초(로그인 응답 expiresIn). 만료되면 401 → 다시 로그인 |
| 헤더 검사 | 헤더가 없으면 익명이다. 헤더가 있으면 /api/auth/**를 뺀 모든 요청에서 검사하고, 무효면 공개 GET도 401이다 |
| JSON | 요청·응답 모두 camelCase, UTF-8. 응답 Content-Type은 `application/json;charset=UTF-8`. 시각은 UTC ISO-8601 문자열(예 `2026-10-12T03:00:00Z`) |
| Accept | `application/json` 또는 `*/*`(Postman 기본값)로 둔다. JSON을 받지 않는 Accept만 보내면 406(본문 없음)이 되거나 오류가 Error 모양으로 나오지 않는다(6절 B3) |

401·403이 나오는 조건 전체는 auth-design.md '401과 403' 표에 있다.

### 오류 형식

오류는 `{code, message, errors}`다. 보안 필터·진입점이 쓰는 401·403과, 컨트롤러까지 온 요청에서 ApiAdvice가 바꾼 오류가 이 모양이다(이 모양이 아닌 응답은 6절 B3). message는 한국어 한 가지이고, 앱은 code와 errors의 키를 보고 자기 번역 문구를 보인다. errors는 항목 이름 → 안내 문구이고, 항목 오류가 없으면 `{}`다. 스택 추적·SQL·토큰은 넣지 않는다.

```json
{ "code": "VALIDATION_FAILED", "message": "입력값을 확인하세요.", "errors": { "title": "1~80자로 입력하세요." } }
```

### 공통 규칙과 적용되는 곳

| 규칙 | 적용되는 곳 | 내용 |
| --- | --- | --- |
| 정하지 않은 항목은 400 | JSON 본문 4곳(A5·A6·A8·A9) | 400 INVALID_REQUEST_BODY, errors.{항목} '허용하지 않는 항목입니다.'. 이름은 대소문자 구분(Title, owner_id도 400). 쿼리의 정하지 않은 항목은 무시한다 |
| 자료형은 스키마대로 | JSON 본문 4곳 | integer에 실수·문자열·배열, string에 숫자·불리언·배열·객체를 보내면 바꿔 저장하지 않고 400 INVALID_REQUEST_BODY '형식이 맞지 않습니다.'. 깨진 JSON·빈 본문(공백뿐인 본문 포함)·본문 전체가 `null`·JSON 뒤 글자는 errors `{}`. 첫 문제 하나만 알린다. 본문 객체 안 항목 값의 `null`은 형식 오류가 아니라 필수 항목 검사(VALIDATION_FAILED)로 간다. 같은 키가 두 번 오면 마지막 값을 쓴다 |
| 본문은 JSON | JSON 본문 4곳 | Content-Type이 JSON이 아니면 본문이 비어 있어도 415 UNSUPPORTED_MEDIA_TYPE. Content-Type이 아예 없으면 본문이 있을 때 415, 본문도 비어 있으면 400 INVALID_REQUEST_BODY |
| 숫자 형식 | 쿼리 step(A3), 경로 id(A4·A6) | 읽지 못하면 400 VALIDATION_FAILED(errors.step·errors.id). INVALID_REQUEST_BODY가 아니다. step은 앞뒤 공백(ASCII 공백·탭·줄바꿈)을 지우고 10진 정수로 읽는다(`+4`·`04`도 4, `0x4`는 400). 경로 id는 Spring 숫자 변환이라 가운데를 포함한 공백을 모두 지우고 `0x`·`#`로 시작하면 16진수로 읽는다(`0x7`·`%237`·`%207`·`+7`은 7, `1%202`는 12. 6절 B4) |
| 앞뒤 공백 제거, 코드 포인트로 글자 수 | A5의 title·location·note만 | 공백은 유니코드 White_Space와 U+FEFF다(목록은 data-model.md tip 절). 이모지 1개 = 1자 |
| 공백을 지우지 않음 | 그 밖의 문자열 값 전부 | route·status·경로 코드는 그대로 비교(대소문자 구분). username은 가입에서 그대로 정규식 검사, 로그인에서 그대로 찾는다. password는 공백도 글자로 센다(공백뿐인 비밀번호도 가입·로그인이 된다, 6절 D4). 숫자인 step·id는 위 '숫자 형식' 줄 |
| 판단 순서 401→400→404→403 | 보호된 팁 쓰기(A5·A6) | 401 UNAUTHORIZED(토큰) → 400 경로 id 형식 → 415 → 400 본문 읽기(INVALID_REQUEST_BODY) → 400 항목 검사(VALIDATION_FAILED, 모아서 줌) → 404 → 403. A5에는 404·403이 없다 |
| 로그인·가입의 순서 | A8·A9 | 415 → 400 본문 읽기 → 400 항목 검사 → 401 LOGIN_FAILED(A8) / 409 USERNAME_TAKEN(A9) |

### 401은 두 가지다

| 구분 | UNAUTHORIZED | LOGIN_FAILED |
| --- | --- | --- |
| 주는 곳 | 보안 필터·진입점(컨트롤러보다 먼저) | AuthService(A8만, 400 검사 뒤) |
| 언제 | 보호 API에 헤더 없음 → '로그인이 필요합니다.' / 헤더가 무효·만료(공개 GET 포함) → '로그인이 만료되었거나 올바르지 않습니다.' | 아이디가 없거나 비밀번호가 다름 → '아이디 또는 비밀번호가 맞지 않아요.' |
| 앱 | 로그아웃하고 로그인 안내 | 아이디는 남기고 비밀번호를 비움 |

### 선언하지 않은 메서드·주소, 점검용 주소

- 익명이면 보안 규칙이 먼저라서 401 UNAUTHORIZED다(예 `DELETE /api/tips/7`, 오타 주소 `/api/tip`). 공개는 GET만이라 HEAD와 CORS 사전 요청(OPTIONS)도 익명이면 401이다.
  - 예외: `/error`(Spring Boot 오류 처리 주소)는 모든 메서드를 허용해서 401이 아니다. 직접 부르면 Spring Boot 기본 오류 본문이 나온다(6절 B3). dev 프로필의 `/h2-console/**`는 별도 보안 체인이 모두 허용한다(H2 콘솔).
- 유효한 토큰이 있으면 없는 주소는 404 NOT_FOUND '주소를 찾을 수 없습니다.', 있는 주소의 다른 메서드(PUT·DELETE 등)는 405 METHOD_NOT_ALLOWED다.
  - 예외: /api/auth/** 아래는 필터가 헤더를 보지 않으므로, 선언한 POST 두 개 말고는 토큰이 있어도 401이다(예 `GET /api/auth/login`).
  - 토큰이 있는 HEAD는 같은 주소의 GET처럼 판단한다(본문 없이 같은 상태 코드, 예 HEAD /api/tips/999999는 404).
  - 토큰이 있는 OPTIONS는 선언한 주소 모양이면 200과 Allow 헤더다(없는 팁 번호도 200, 예 OPTIONS /api/tips/999999). 없는 주소면 404 NOT_FOUND다(예 OPTIONS /api/tip).
- 컨트롤러에서 처리하지 못한 오류는 500 INTERNAL_ERROR, 따로 다루지 않은 그 밖의 Spring 요청 오류는 원래 4xx 상태와 BAD_REQUEST다. 둘 다 ApiAdvice가 다루는 범위이고, 보안 필터 안에서 난 예외(예 토큰을 확인하다 난 DB 오류)는 이 모양이 아니다(6절 B3).
- 계약 밖 점검용 주소: GET /(text/plain 안내 문구), GET /api/health(`{status: UP, project}`). 서버가 켜졌는지 볼 때만 쓰고 앱은 쓰지 않는다. yaml paths에는 없다.

## 2. API 목록

| 번호 | 기능 | 화면 | 메서드 경로 | 권한 | 성공 |
| --- | --- | --- | --- | --- | --- |
| A1 | F01 경로 목록 | S01 | GET /api/routes | 공개 | 200 |
| A2 | F02 단계 안내 | S02 | GET /api/routes/{route}/steps | 공개 | 200 |
| A3 | F04 단계 팁 보기 | S02 | GET /api/tips?route=&step= | 공개(토큰이 있으면 mine 계산) | 200 |
| A4 | 보조: 팁 하나 | 없음 | GET /api/tips/{id} | 공개(토큰이 있으면 mine 계산) | 200 |
| A5 | F05 팁 올리기 | S03 | POST /api/tips | 인증 | 201 + Location |
| A6 | F07 팁 상태 바꾸기 | S02·S06 | PATCH /api/tips/{id}/status | 인증 + 올린 사람 | 200 |
| A7 | F08 내 팁 보기 | S06 | GET /api/me/tips | 인증 | 200 |
| A8 | F06 로그인 | S04 | POST /api/auth/login | 공개(Authorization 헤더를 보지 않음) | 200 |
| A9 | F10 회원가입 | 없음 | POST /api/auth/signup | 공개(Authorization 헤더를 보지 않음) | 201(Location 없음) |

## 3. API별 설명

상태 코드 표의 '앱 처리'는 functional-spec.md가 정한 처리를 짧게 적은 것이다(앱 구현을 확인한 결과가 아니다). 자세한 화면 처리는 functional-spec.md의 그 기능 절에 있고, 지금 앱 구현과 다른 곳은 6절 '앱과 맞출 것'에 적는다.

### A1. GET /api/routes — 경로 목록

- **하는 일:** 경로를 표시 순서(route.sort_order)대로 준다.
- **권한:** 공개. 토큰은 쓰지 않지만 헤더가 붙으면 검사한다.
- **요청:** 경로 변수·쿼리·본문이 없다.
- **성공 200** `{routes: [Route]}`. Route는 code, name{en, ko}, summary{en, ko}, available, stepCount.
  - stepCount: 열이 아니라 그 경로의 route_step 개수를 센 값이다. T2는 0.
  - available: false면 준비 중(T2)이다. 팁 조회·등록은 400으로 막는다. 단계 조회(A2)는 available을 보지 않고 그 경로의 행을 준다(T2는 행이 아직 없어 빈 목록).

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 정상 | 경로 카드 목록 |
| 401 | UNAUTHORIZED | Authorization 헤더가 무효·만료 | 앱은 토큰을 붙이지 않으므로 생기지 않는다 |

- **주의:** 출발 터미널과 표지판 병기 줄은 서버가 주지 않는다(앱 상수, functional-spec F01).
- **예시:** [routes-list.json](api-examples/routes-list.json), [routes-list-401-invalid-token.json](api-examples/routes-list-401-invalid-token.json)

### A2. GET /api/routes/{route}/steps — 단계 안내

- **하는 일:** 한 경로의 단계를 stepNo 순서로 준다.
- **권한:** 공개. 헤더가 무효면 404보다 먼저 401이다.
- **요청:** 경로 변수 route — EXPRESS·LOCAL·T2. 그대로 비교한다(`express`는 404).
- **성공 200** `{route, steps: [Step]}`. Step은 stepNo, role(HERE·FIND), icon(PLANE·ESCALATOR·TRAIN), photoKey(null 가능), sign{ko, en}, title{en, ko}, instructions{en[], ko[]}, elevator{en, ko}(null 가능).
  - instructions: DB에 줄바꿈으로 이어 저장한 값을 줄마다 나눈 배열이다.
  - elevator: elevator_en·elevator_ko가 둘 다 있을 때만 준다. 한쪽이 없거나 공백뿐이면 null이다.
  - T2는 200과 빈 steps다(T2 단계 행이 아직 없어서).

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 정상(T2는 빈 목록) | 단계 카드 |
| 401 | UNAUTHORIZED | 헤더가 무효·만료 | 생기지 않음(토큰을 붙이지 않음) |
| 404 | NOT_FOUND | 없는 경로 코드(대소문자 다름 포함) '경로를 찾을 수 없습니다.' | 다른 실패와 같은 전체 화면 안내와 다시 시도(정상 사용에서는 생기지 않음) |

- **주의:** 단계 문구·표지판·층수는 현장 확인 전 초안이다(data-model.md 시드 절).
- **예시:** [route-steps.json](api-examples/route-steps.json), [route-steps-401-invalid-token.json](api-examples/route-steps-401-invalid-token.json), [route-steps-404-unknown-route.json](api-examples/route-steps-404-unknown-route.json)

### A3. GET /api/tips?route=&step= — 한 단계의 팁 목록

- **하는 일:** 한 단계의 팁을 id 내림차순(새 팁 먼저)으로 준다. 0건이면 빈 배열이다.
- **권한:** 공개. 토큰이 있으면 mine을 계산한다. 헤더가 무효면 400보다 먼저 401이다.
- **요청(쿼리):**

| 항목 | 필수 | 규칙 | 정하는 쪽 |
| --- | --- | --- | --- |
| route | 예 | 안내 중인 경로 EXPRESS·LOCAL. 그대로 비교 | 앱(S02의 경로) |
| step | 예 | 1~그 경로의 stepCount 정수. 앞뒤 ASCII 공백 허용 | 앱(S02의 단계) |

- **성공 200** `{tips: [Tip]}`. Tip은 id, route, stepNo, title, location, note(null 가능), status(VERIFIED·OUTDATED), verifiedAt, createdAt, ownerId, mine, example.
  - mine: 열이 아니라 응답 때 계산한다. 토큰의 사용자 id와 tip.owner_id가 같으면 true, 토큰이 없으면 모두 false.
  - example: 시드 예시 팁이면 true. 사용자가 올린 팁은 항상 false.
  - verifiedAt: 작성자가 마지막으로 상태를 정한 시각. 앱은 서울 날짜로 바꿔 배지에 쓴다.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 정상 | 팁 영역(VERIFIED 묶음 먼저) |
| 400 | VALIDATION_FAILED | errors.route: route 없음·T2·대소문자 다름·없는 코드. errors.step: 없음·정수 아님·범위 밖. 두 항목을 모아 준다. 범위는 route가 맞을 때만 본다 | 앱 결함, 일반 실패 안내 |
| 401 | UNAUTHORIZED | 헤더가 무효·만료 | 로그아웃(functional-spec 4절 '로그인 끝남') |

- **예시:** [tips-for-step.json](api-examples/tips-for-step.json), [tips-for-step-with-token.json](api-examples/tips-for-step-with-token.json), [tips-for-step-400-out-of-range.json](api-examples/tips-for-step-400-out-of-range.json), [tips-for-step-401-invalid-token.json](api-examples/tips-for-step-401-invalid-token.json)

### A4. GET /api/tips/{id} — 팁 하나(보조)

- **하는 일:** 팁 하나를 준다. A5의 Location이 가리키는 주소다. 앱 화면은 쓰지 않는다.
- **권한:** 공개. 토큰이 있으면 mine을 계산한다.
- **요청:** 경로 변수 id — 정수(int64).
- **성공 200** Tip(A3과 같은 모양).

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 정상 | (화면 없음) |
| 400 | VALIDATION_FAILED | errors.id: 정수로 읽을 수 없음(abc, 1.5) 또는 int64 범위 밖. 16진수·공백이 섞인 값은 읽는다(1절 '숫자 형식', 6절 B4) | (화면 없음) |
| 401 | UNAUTHORIZED | 헤더가 무효·만료 | (화면 없음) |
| 404 | NOT_FOUND | 없는 번호(0·음수 포함) '팁을 찾을 수 없습니다.' | (화면 없음) |

- **예시:** [tip-detail.json](api-examples/tip-detail.json), [tip-detail-400-invalid-id.json](api-examples/tip-detail-400-invalid-id.json), [tip-detail-401-invalid-token.json](api-examples/tip-detail-401-invalid-token.json), [tip-detail-404-not-found.json](api-examples/tip-detail-404-not-found.json)

### A5. POST /api/tips — 팁 올리기

- **하는 일:** 한 단계에 팁을 올린다.
- **권한:** 인증(Bearer 필수). 올린 사람은 본문이 아니라 검증한 토큰의 사용자다.
- **요청 본문(TipCreateRequest):**

| 항목 | 필수 | 규칙 | 정하는 쪽 |
| --- | --- | --- | --- |
| route | 예 | EXPRESS·LOCAL. 그대로 비교 | 앱(S02에서 넘어옴) |
| stepNo | 예 | JSON 정수, 1~그 경로의 stepCount | 앱(S02에서 넘어옴) |
| title | 예 | 앞뒤 공백 제거 뒤 1~80 코드 포인트 | 사용자 |
| location | 예 | 앞뒤 공백 제거 뒤 1~80 코드 포인트 | 사용자 |
| note | 아니요 | 문자열 또는 null. 앞뒤 공백 제거 뒤 0~150 코드 포인트, 빈 값은 null로 저장 | 사용자 |

- **서버가 정하는 값:** id, ownerId(토큰의 사용자), status(VERIFIED), verifiedAt = createdAt(지금), example(false), mine. 본문에 보내면 400 INVALID_REQUEST_BODY다.
- **성공 201** Tip + `Location: /api/tips/{id}`(상대 경로). title·location은 공백을 지운 값으로 저장하고 돌려준다.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 201 | — | 등록됨 | 그 단계로 돌아가 새 팁으로 스크롤 |
| 400 | VALIDATION_FAILED | 항목 규칙 위반. 모든 항목을 모아 준다. stepNo 범위는 route가 맞을 때만 본다 | errors 키(title·location·note)별 칸 오류 |
| 400 | INVALID_REQUEST_BODY | 정하지 않은 항목, 자료형 다름(stepNo "4"·3.7), 깨진 JSON. 첫 문제 하나 | 앱 결함, 일반 실패 안내 |
| 401 | UNAUTHORIZED | 헤더 없음·무효·만료. 본문 검사보다 먼저 | '로그인이 끝났어요', 입력값 유지 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 본문이 JSON이 아님(401 검사 뒤) | 앱 결함 |

- **주의:** 앱도 같은 규칙(코드 포인트, 같은 공백 범위)으로 먼저 검사한다. stepNo가 int 범위를 넘으면 errors가 비어 나온다(6절 B2).
- **예시:** [tip-create.json](api-examples/tip-create.json), [tip-create-400-validation.json](api-examples/tip-create-400-validation.json), [tip-create-400-unknown-field.json](api-examples/tip-create-400-unknown-field.json), [tip-create-400-wrong-type.json](api-examples/tip-create-400-wrong-type.json), [tip-create-401-no-token.json](api-examples/tip-create-401-no-token.json), [tip-create-415-not-json.json](api-examples/tip-create-415-not-json.json)

### A6. PATCH /api/tips/{id}/status — 팁 상태 바꾸기

- **하는 일:** 올린 사람이 상태를 VERIFIED(작성자 확인)·OUTDATED(정보 오래됨)로 정한다. 그때마다 verifiedAt을 지금으로 바꾼다. 같은 상태를 다시 보내도 200이다(작성자 재확인).
- **권한:** 인증 + 올린 사람. 서비스가 DB의 owner_id와 토큰의 사용자 id를 비교한다. 예시 팁도 올린 사람이면 서버는 허용한다(앱이 버튼을 숨김).
- **요청:** 경로 변수 id(int64), 본문 `{status}` — VERIFIED 또는 OUTDATED, 그대로 비교(소문자·앞뒤 공백은 400). 다른 항목은 400.
- **성공 200** 바뀐 Tip. title·location·note·ownerId·createdAt은 그대로다.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 바뀜 | 배지·날짜 갱신 |
| 400 | VALIDATION_FAILED / INVALID_REQUEST_BODY | errors.id(경로 id 형식, 본문보다 먼저) → 본문 읽기 오류 → errors.status(없음·null·허용하지 않는 값). 팁을 찾기 전에 판단 | '상태를 바꾸지 못했어요' |
| 401 | UNAUTHORIZED | 헤더 없음·무효·만료. 가장 먼저 | 로그아웃, S02는 팁을 익명으로 다시 읽음 |
| 403 | FORBIDDEN | 올린 사람이 아님 '이 팁을 올린 사람만 상태를 바꿀 수 있습니다.' | 그 팁 카드에 안내와 [다시 로그인], 버튼 막음 |
| 404 | NOT_FOUND | 없는 팁 번호(0·음수 포함) | '상태를 바꾸지 못했어요' |
| 415 | UNSUPPORTED_MEDIA_TYPE | 본문이 JSON이 아님(경로 id 검사 뒤, 본문 검사 앞) | 앱 결함 |

- **주의:** 권한을 확인하기 전에는 값을 바꾸지 않는다. 실패한 요청 뒤 팁 행은 그대로다(PermissionRegressionTest).
- **예시:** [tip-status.json](api-examples/tip-status.json), [tip-status-400-invalid-status.json](api-examples/tip-status-400-invalid-status.json), [tip-status-401-no-token.json](api-examples/tip-status-401-no-token.json), [tip-status-403-not-owner.json](api-examples/tip-status-403-not-owner.json), [tip-status-404-not-found.json](api-examples/tip-status-404-not-found.json), [tip-status-415-not-json.json](api-examples/tip-status-415-not-json.json)
  - tip-status.json의 verifiedAt(00:05)은 등록 예시(00:00)보다 뒤인 고정값이다. 실제로는 요청한 시각이 들어간다(api-examples/README.md '바꿔 적은 값').

### A7. GET /api/me/tips — 내가 올린 팁

- **하는 일:** 토큰의 사용자가 올린 팁을 id 내림차순으로 준다. 예시 팁도 내 것이면 포함한다(앱이 목록에서 뺀다).
- **권한:** 인증.
- **요청:** 경로 변수·본문이 없다. 쿼리는 무시한다.
- **성공 200** `{tips: [Tip]}`. mine은 모두 true다.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 정상(없으면 빈 배열) | 내 팁 목록, 없으면 빈 상태 안내 |
| 401 | UNAUTHORIZED | 헤더 없음·무효·만료 | 로그아웃하고 로그인 안내 |

- **예시:** [my-tips.json](api-examples/my-tips.json), [my-tips-401-no-token.json](api-examples/my-tips-401-no-token.json)

### A8. POST /api/auth/login — 로그인

- **하는 일:** 아이디·비밀번호를 확인하고 JWT를 발급한다.
- **권한:** 공개. Authorization 헤더는 보지 않는다(오래된 토큰이 붙어도 로그인된다).
- **요청 본문(LoginRequest):**

| 항목 | 필수 | 규칙 | 정하는 쪽 |
| --- | --- | --- | --- |
| username | 예 | 없음·빈 값·공백뿐이면 400(Java isBlank 기준이라 U+00A0만 있는 값은 400이 아님). 공백을 지우지 않고 그대로 찾는다(`" student01"`은 401) | 사용자 |
| password | 예 | 없음·빈 값이면 400. 공백뿐인 값은 빈 값이 아니라서 그대로 비교한다(그 값으로 가입한 계정이면 200, 아니면 401) | 사용자 |

- **성공 200** accessToken, tokenType("Bearer"), expiresIn(1800), user{id, username}.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 200 | — | 로그인 | 토큰을 앱 메모리에 두고 온 곳으로 돌아감 |
| 400 | VALIDATION_FAILED / INVALID_REQUEST_BODY | 빈 칸 / 정하지 않은 항목·문자열이 아닌 값·읽을 수 없는 JSON | 로그인 오류 안내(공통) |
| 401 | LOGIN_FAILED | 아이디 없음·비밀번호 다름(UTF-8 72바이트 초과 포함). 어느 쪽이 틀렸는지 알리지 않음 | 아이디 유지, 비밀번호 비움 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 본문이 JSON이 아님 | 앱 결함 |

- **주의:** 없는 아이디도 미리 만든 가짜 해시와 한 번 비교해 응답 시간으로 아이디 존재가 드러나지 않게 한다. 표본 계정과 비밀번호는 requirements.md '표본 자료'.
- **예시:** [login-response.json](api-examples/login-response.json), [login-400-blank.json](api-examples/login-400-blank.json), [login-401-wrong-password.json](api-examples/login-401-wrong-password.json), [login-415-not-json.json](api-examples/login-415-not-json.json)

### A9. POST /api/auth/signup — 회원가입(API만)

- **하는 일:** 계정을 만든다. 앱 화면은 없다(계정을 얻는 방법은 미정, functional-spec 9절 1).
- **권한:** 공개. Authorization 헤더는 보지 않는다.
- **요청 본문(SignupRequest):**

| 항목 | 필수 | 규칙 | 정하는 쪽 |
| --- | --- | --- | --- |
| username | 예 | `^[a-z0-9_]{4,24}$`. 공백을 지우지 않는다. 유일해야 함 | 사용자 |
| password | 예 | 8자(코드 포인트, 공백도 셈) 이상, UTF-8 72바이트 이하 | 사용자 |

- **성공 201** `{id, username}`. 비밀번호·해시는 응답하지 않는다. 사용자 조회 API가 없어 Location 헤더는 주지 않는다.

| 코드 | code | 언제 | 앱 처리 |
| --- | --- | --- | --- |
| 201 | — | 가입됨(id는 103부터) | (화면 없음) |
| 400 | VALIDATION_FAILED / INVALID_REQUEST_BODY | 두 항목의 규칙 위반을 모아 줌 / 정하지 않은 항목(role 등)·문자열이 아닌 값 | (화면 없음) |
| 409 | USERNAME_TAKEN | 이미 있는 username(400 검사 뒤) | (화면 없음) |
| 415 | UNSUPPORTED_MEDIA_TYPE | 본문이 JSON이 아님 | (화면 없음) |

- **예시:** [signup.json](api-examples/signup.json), [signup-400-invalid.json](api-examples/signup-400-invalid.json), [signup-400-unknown-field.json](api-examples/signup-400-unknown-field.json), [signup-409-username-taken.json](api-examples/signup-409-username-taken.json), [signup-415-not-json.json](api-examples/signup-415-not-json.json)

## 4. 오류 코드

앱이 각 code를 어떻게 보여 주는지는 functional-spec.md 5절에 있다.

| code | 상태 | 나오는 API | 뜻 |
| --- | --- | --- | --- |
| VALIDATION_FAILED | 400 | A3·A5·A6·A8·A9, 경로·쿼리 숫자 형식(A3 step, A4·A6 id) | 항목 규칙 위반. errors에 항목별 문구 |
| INVALID_REQUEST_BODY | 400 | A5·A6·A8·A9 | 본문을 읽을 수 없음(정하지 않은 항목, 자료형, 깨진 JSON) |
| BAD_REQUEST | 원래 4xx | 어디서나 | 따로 다루지 않은 Spring 요청 오류 |
| UNAUTHORIZED | 401 | 헤더가 무효인 모든 요청(/api/auth/** 제외), 헤더가 없는 A5·A6·A7, 익명의 선언하지 않은 메서드·주소(/error·dev의 /h2-console/** 제외), /api/auth/** 아래의 선언하지 않은 요청(토큰이 있어도) | 토큰 문제(또는 인증이 필요한 주소) |
| LOGIN_FAILED | 401 | A8 | 아이디 또는 비밀번호가 맞지 않음 |
| FORBIDDEN | 403 | A6 | 로그인했지만 올린 사람이 아님 |
| NOT_FOUND | 404 | A2(경로), A4·A6(팁), 토큰이 있을 때 없는 주소 | 대상이나 주소가 없음 |
| METHOD_NOT_ALLOWED | 405 | 토큰이 있을 때 있는 주소의 다른 메서드 | 지원하지 않는 요청 방식 |
| USERNAME_TAKEN | 409 | A9 | 이미 있는 아이디 |
| UNSUPPORTED_MEDIA_TYPE | 415 | A5·A6·A8·A9 | 본문이 JSON이 아님 |
| INTERNAL_ERROR | 500 | 컨트롤러까지 온 모든 요청 | 처리하지 못한 서버 오류(보안 필터 안의 예외는 이 모양이 아님, 6절 B3) |

## 5. 실제 응답 확인

- [api-examples/](api-examples/README.md)는 자동 테스트 예시다. 테스트 프로필·메모리 DB에서 MockMvc로 만든 것이라 실행 서버의 실제 응답이 아니다. 토큰·비밀번호·실행 시각은 바꿔 적었다(각 파일의 meta.replaced). 평소 테스트(ApiExamplesGenerator)가 같은 요청을 다시 보내 지금 코드의 응답과 같은지 비교하므로, 코드의 응답이 바뀌면 테스트가 실패하고 예시를 다시 만들어야 한다.
- [samples/](samples/README.md)는 교재 10장 자료표의 '실제 응답 표본' 자리다. 학생이 실행 서버(127.0.0.1:8097)에서 Postman으로 모은다. **아직 모으지 않았다(미완료).** 모으는 방법·파일 이름·토큰 숨기기는 samples/README.md에 있다.
- 두 폴더의 같은 이름 파일을 비교해 구조(키 이름, 자료형, null 여부, 상태 코드, Location 헤더)가 같은지 본다. id·시각·토큰 값은 달라도 된다.

## 6. 검토 필요 목록

2026-10-21 API 계약 감사(Claude·Codex 두 감사, 코드로 다시 확인)와 그 뒤의 반박 검토(Codex RC-01~11, Claude 검증 RV-01~10)에서 나온 것이다. 문서 설명 오류(A)로 고친 것과 판정표는 reports/verification.md의 같은 날짜 절에 있다.

### C. 코드가 이미 그렇게 동작해서 yaml에 더한 것(사람이 확인한 뒤 커밋)

| # | yaml에 더한 것 | 근거(코드 · 테스트) |
| --- | --- | --- |
| C1 | JSON 본문 4곳(A5·A6·A8·A9)에 415 응답(components.responses.UnsupportedMediaType) | ApiAdvice.mediaType · TipWriteTest.nonJsonBodyIs415AfterTokenCheck, AuthApiTest.authBodiesMustBeJsonAndSignupIgnoresStaleHeader, 예시 tip-create-415-not-json.json |
| C2 | 공통 규칙: 선언하지 않은 메서드·주소(익명 401(/error·dev의 H2 콘솔 제외), 토큰이 있으면 404·405, /api/auth/** 아래는 늘 401, 토큰이 있는 HEAD·OPTIONS), 500 INTERNAL_ERROR, BAD_REQUEST(둘 다 ApiAdvice 범위) | SecurityConfig의 anyRequest().authenticated(), JwtAuthenticationFilter.shouldNotFilter, ApiAdvice · AuthApiTest.undeclaredMethodsAndPaths |
| C3 | 권한 요약: 계약 밖 점검용 GET /, GET /api/health(paths에는 넣지 않음) | HelloController, SecurityConfig · PublicReadTest.health, ApiContractDocTest의 예외 목록 |

정할 것: (1) GET /api/health를 paths에 정식으로 넣을지(Codex는 D로 봄). (2) 익명의 HEAD·OPTIONS·다른 메서드가 405 대신 401인 지금 동작을 그대로 둘지(바꾸면 D).

### B. 구현 결함(고치지 않고 보고만)

| # | 문제 | 근거 | 고친다면 |
| --- | --- | --- | --- |
| B1 | 401에 WWW-Authenticate 헤더, 405에 Allow 헤더가 없다(HTTP 표준 RFC 9110은 둘 다 요구). 앱은 이 헤더를 쓰지 않아 지금 영향은 없다 | SecurityConfig 진입점, JwtAuthenticationFilter, ApiAdvice.method. MockMvc로 확인 | 401에 `WWW-Authenticate: Bearer`, 405에 예외의 Allow를 붙이고 yaml headers에 선언 |
| B2 | 본문 stepNo가 int 범위를 넘으면(3000000000) 400 INVALID_REQUEST_BODY인데 errors가 `{}`라 항목 이름이 없다 | Jackson이 InputCoercionException을 던지고 ApiAdvice가 MismatchedInputException만 본다. MockMvc로 확인 | ApiAdvice가 Jackson 예외의 경로로 errors.stepNo를 채움 |
| B3 | Error 모양이 아닌 응답이 있다. ① 방화벽이 거절한 주소(`;`, `%2F`): MockMvc에서 400·빈 본문. ② Accept가 JSON을 받지 않음: 406·빈 본문이고, 그때 서비스가 오류를 내면(예 로그인 실패) 처리하지 못한 예외(MockMvc에서 ServletException). ③ `/error`를 직접 부름: GET·POST·DELETE·HEAD는 500과 Spring Boot 기본 본문 `{timestamp, status, error}`(status 999, Accept가 HTML이면 Whitelabel 페이지), OPTIONS는 200. 무효 토큰이 붙으면 401 Error다. ④ 보안 필터 안에서 난 예상하지 못한 예외(예 토큰을 확인하다 난 DB 오류): ApiAdvice를 거치지 않아 MockMvc에서는 예외가 그대로 나온다. 같은 DB 오류가 컨트롤러 안에서 나면 500 INTERNAL_ERROR다 | ①② MockMvc로 확인. ③④ 스크래치 사본의 MockMvc 탐침(④는 메모리 DB의 app_user 표 이름을 바꿔 DB 오류를 흉내 냄). 실행 서버에서 ①②④에 Tomcat·Spring Boot 기본 오류 응답(500, `{timestamp, status, error, path}`)이 나오는지는 확인하지 않았다(추정) | RequestRejectedHandler·ErrorController를 Error 모양으로, 필터의 예상하지 못한 예외는 500 INTERNAL_ERROR로(DB 장애를 401로 바꾸지 않는다). 지금은 yaml 문구를 'ApiAdvice가 바꾼 오류'로 좁혀 두었다 |
| B4 | 경로 변수 id(A4·A6)를 계약(10진 정수)보다 넓게 읽는다. Spring 숫자 변환이 공백 문자를 가운데까지 모두 지우고 `0x`·`0X`·`#`로 시작하면 16진수로 읽는다: `GET /api/tips/0x7`·`%237`·`%207`·`7%20`·`+7`·`007`은 팁 7(200), `1%202`는 12(404). 소유자 토큰의 `PATCH /api/tips/0x8/status`는 200이고 실제로 바뀐다. 쿼리 step은 `0x4`를 400으로 막아 두 규칙도 다르다 | TipController의 `@PathVariable long id`(NumberUtils.parseNumber). 스크래치 사본의 MockMvc 탐침(Claude 검증 RV-06과 같은 결과) | id를 문자열로 받아 `^-?[0-9]+$`만 허용한 뒤 long으로 바꾼다(앱은 10진 숫자만 보내 영향 없음). 고치면 yaml 공통 규칙·TipId 설명의 B4 문장을 지운다 |

### D. 계약 변경이 필요한 것(고치지 않고 보고만)

| # | 바꿀 것 | 이유 |
| --- | --- | --- |
| D1 | 본문 stepNo와 쿼리 step에 `format: int32`를 적을지 | 지금 yaml은 범위 없는 integer라 3000000000도 형식상 맞다. 적으면 타입 범위가 바뀐다 |
| D2 | 공개 GET의 security 표기를 하나로 맞출지(A1·A2는 `[]`, A3·A4는 `[{}, bearerAuth]`) | 동작은 같다(헤더가 있으면 넷 다 검사). 권한 표기라 사람이 정한다 |
| D3 | 같은 키가 두 번 온 본문을 거절할지(지금은 마지막 값을 씀) | 막으면 400의 의미가 바뀐다 |
| D4 | 공백뿐인 비밀번호(예 공백 8칸)로 가입하는 것을 막을지(지금은 201이고 그 값으로 로그인도 된다) | 막으면 가입 400의 조건이 바뀐다. 앱에는 가입 화면이 없다 |

### E. 앱과 맞출 것(서버 밖, 사람이 정함)

| # | 다른 점 | 근거 | 정할 것 |
| --- | --- | --- | --- |
| E1 | A5 400 VALIDATION_FAILED의 앱 처리. functional-spec F05와 이 문서 A5 표는 'errors 키(title·location·note)별 칸 오류'인데, 지금 앱은 errors 키를 모아 두기만 하고 화면이 쓰지 않는다. 401이 아니면 모두 '올리지 못했어요'다 | 앱 lib/repositories/subway_repository.dart(errors 키를 fieldErrors에 담음), lib/screens/add_tip_screen.dart(401만 따로 처리). Codex 반박 검토 RC-03 | (1) 명세대로 앱이 칸 오류로 잇는다(앱 저장소 작업) 또는 (2) functional-spec F05와 이 표를 '일반 등록 실패 안내'로 바꾼다. 앱이 같은 규칙으로 먼저 검사하므로 정상 사용에서는 드물다 |
