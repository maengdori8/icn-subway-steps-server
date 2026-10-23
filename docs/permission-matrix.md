# 권한표 · ICN Subway Steps 서버

문서 정보: 2026-10-21 · 교재 8장 P00 · 작성 Claude Code, 검토: (본인 이름)

근거:
- docs/api-spec.yaml
- docs/auth-design.md
- reports/change-review.md
- 소스: SecurityConfig.java, JwtAuthenticationFilter.java, TipController.java, TipService.java, AuthService.java, ApiAdvice.java

## 계정과 등록자

- A = student01(id 101), B = student02(id 102). 시드에 들어 있는 표본 계정이다. 비밀번호는 이 문서에 적지 않는다.
- 등록자는 팁을 올린 사람이다. DB에는 tip.owner_id로 저장된다.
- 서버는 등록자를 검증한 토큰의 사용자로 정한다(TipService.java:84). 요청 본문으로는 받지 않는다. 본문에 ownerId를 넣으면 400 INVALID_REQUEST_BODY다(Requests.java:4-5).
- 이 앱에서 '변경'은 팁 상태 바꾸기(PATCH /api/tips/{id}/status) 하나다. 팁 내용 수정(PUT), 삭제(DELETE), 파일 첨부 API는 없다(docs/requirements.md '범위').

## 교재 1.2 표를 이 앱에 맞춘 요약

| 요청한 사람 | 경로·단계·팁 조회 | 새 팁 등록 | 내 팁 보기 | 내가 올린 팁 상태 변경 | 남이 올린 팁 상태 변경 |
| --- | --- | --- | --- | --- | --- |
| 로그인 안 함 | 200(공개) | 401 | 401 | 401 | 401 |
| 로그인한 사용자 | 200(공개) | 201 | 200 | 200 | 403 |

## 요청별 기대 응답

이 표에서 '팁'은 A가 올린 팁이다.

| 메서드 · 경로 | 화면 | 로그인 없음 | 등록자 A | 다른 사용자 B | 근거 |
| --- | --- | --- | --- | --- | --- |
| GET /api/routes | S01 | 200 | 200 | 200 | SecurityConfig.java:49 |
| GET /api/routes/{route}/steps | S02 | 200 | 200 | 200 | SecurityConfig.java:49 |
| GET /api/tips?route={EXPRESS·LOCAL}&step={1~단계 수} (예: route=LOCAL&step=2) | S02 | 200, mine 모두 false | 200, A의 팁은 mine true | 200, A의 팁은 mine false | SecurityConfig.java:50, TipService.java:134-138. route·step이 비었거나 틀리면 400 VALIDATION_FAILED(TipService.java:42-56) |
| GET /api/tips/{id} | (POST의 Location) | 200, mine false | 200, mine true | 200, mine false | SecurityConfig.java:50 |
| POST /api/tips | S03 | 401 UNAUTHORIZED | 201 + Location, ownerId 101 | 201 + Location, ownerId 102(B 자신의 새 팁) | SecurityConfig.java:53, TipController.java:34-37 |
| PATCH /api/tips/{id}/status | S02·S06 | 401 UNAUTHORIZED | 200 | 403 FORBIDDEN | TipService.java:89-97 |
| GET /api/me/tips | S06 | 401 UNAUTHORIZED | 200, A의 팁만 | 200, B의 팁만(A의 팁 없음) | SecurityConfig.java:53, TipService.java:67-69 |
| POST /api/auth/signup | (화면 없음) | 201, 이미 있는 아이디면 409 USERNAME_TAKEN | 토큰을 보지 않음 | 토큰을 보지 않음 | JwtAuthenticationFilter.java:38-41, AuthService.java:43-60 |
| POST /api/auth/login | S04 | 200, 틀리면 401 LOGIN_FAILED | 토큰을 보지 않음 | 토큰을 보지 않음 | AuthService.java:63-76 |

## 표 밖의 경우

**없는 번호**
- 유효한 토큰으로 없는 팁 번호(0·음수 포함)에 PATCH하면 404 NOT_FOUND다. GET /api/tips/{id}도 404다(TipService.java:99-101).
- 토큰 없이 없는 번호에 PATCH하면 401이 먼저 나온다. 인증이 먼저이기 때문이다.
- 숫자가 아닌 번호(abc, 1.5, long 범위 밖)는 GET·PATCH 모두 400 VALIDATION_FAILED, errors.id다(ApiAdvice.java:42-46).
- 테스트: PublicReadTest.tipDetail, PermissionRegressionTest.badTipIdIs400AndMissingIdIs404.

**무효·만료·변조 토큰**
- Authorization 헤더가 있는데 토큰이 무효·만료·변조이면, 공개 GET이라도 401 UNAUTHORIZED다. 문구는 '로그인이 만료되었거나 올바르지 않습니다.'다(JwtAuthenticationFilter.java:46-55).
- 'Bearer '로 시작하지 않는 값과 빈 토큰도 401이다(JwtAuthenticationFilter.java:63-65).
- 헤더가 아예 없으면 공개 GET은 200이다.
- 가입·로그인은 Authorization 헤더를 보지 않는다. 오래된 토큰이 붙어 있어도 로그인할 수 있다(JwtAuthenticationFilter.java:38-41).
- 서명이 맞아도 토큰의 사용자가 DB에 없거나, 이름이 다르거나, 계정이 생기기 전에 발급된 토큰이면 401이다(JwtAuthenticationFilter.java:68-74).

**입력 오류**
- 400 VALIDATION_FAILED: 항목별 규칙 위반이다. errors에 항목별 문구가 들어간다.
- 400 INVALID_REQUEST_BODY: 깨진 JSON, 본문 없음, 정하지 않은 항목(ownerId·status 등), 자료형이 다른 값이다(ApiAdvice.java:29-40).
- PATCH에서 여러 실패가 겹치면 이 순서로 판단한다(docs/api-spec.yaml:46-47, TipService.java:23·89-94).
  1. 401 UNAUTHORIZED(토큰)
  2. 400 VALIDATION_FAILED(경로의 id 형식)
  3. 415(본문이 JSON이 아님)
  4. 400 INVALID_REQUEST_BODY(본문을 읽을 수 없음)
  5. 400 VALIDATION_FAILED(status 값)
  6. 404(팁 없음)
  7. 403(등록자 아님)
- 그래서 B가 A의 팁에 잘못된 status를 보내면 403이 아니라 400이다.
- 테스트: PermissionRegressionTest.missingTipIs404AndOrderIs401Then400Then404Then403.

**/api 아래 없는 경로**
- 토큰 없이 부르면 401이다(SecurityConfig.java:53의 anyRequest().authenticated()). 교재 3.2처럼 401이 나오면 경로 철자도 확인한다.
- 유효한 토큰으로 부르면 404 NOT_FOUND다(ApiAdvice.java:48-51).
- /api/auth/** 아래의 다른 요청(예: GET /api/auth/login)은 필터가 헤더를 보지 않으므로 토큰이 있어도 401이다.
- 테스트: AuthApiTest.undeclaredMethodsAndPaths.

**허용하지 않는 메서드**
- 예: DELETE /api/tips/{id}. 이 API에는 삭제가 없다.
- 토큰 없이 보내면 401이다. 공개로 열린 것은 GET뿐이기 때문이다(익명의 HEAD·OPTIONS도 401).
- 유효한 토큰으로 DELETE·PUT처럼 선언하지 않은 메서드를 보내면 405 METHOD_NOT_ALLOWED다(ApiAdvice.java:53-57).
- 예외: 유효한 토큰의 HEAD는 같은 주소의 GET처럼 판단한다. 유효한 토큰의 OPTIONS는 선언한 주소면 200과 Allow 헤더, 없는 주소면 404다(docs/api-spec.yaml:53-54).
- 테스트: AuthApiTest.undeclaredMethodsAndPaths(DELETE 401·405, 없는 경로 401·404).

**예시 팁(example=true)**
- 서버는 예시 팁도 등록자이면 상태를 바꾸게 둔다.
- 팁 7은 B(102)가 올린 예시 팁이다. 그래서 B는 200, A는 403이다.
- 앱은 예시 팁에 상태 버튼을 숨긴다. 이것은 화면 편의일 뿐이고 서버 검사를 대신하지 않는다.

## 확인할 질문

1. 등록자가 아닐 때의 403 code가 FORBIDDEN이다(ApiException.java:27-29). 보안 설정의 접근 거부도 같은 FORBIDDEN이다(SecurityConfig.java:57-58). 두 경우는 문구로만 구분된다.
   - 교재 예제는 등록자 검사를 NOT_OWNER로 따로 둔다.
   - code를 바꾸면 계약 변경이다(api-spec.yaml Error.code 목록, 앱 처리). 그래서 지금은 바꾸지 않았다.
   - 지금 규칙에는 역할 조건이 없어서, 보안 설정의 접근 거부가 실제로 나오는 경로는 없어 보인다(추정).
2. 예시 팁의 상태 변경을 서버에서도 막을지 정해야 한다(docs/data-model.md 검토 메모 2).
3. 실행 서버(Tomcat)의 응답은 MockMvc 테스트와 다를 수 있다. 8장 Postman·Newman 실행 결과(reports/verification.md 8장 절)로 위 표를 다시 확인한다.
