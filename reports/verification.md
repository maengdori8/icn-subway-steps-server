# 실행 기록 · ICN Subway Steps 서버

프로젝트 경로: C:\dev\projects\icn-subway-server · 기록일 2026-10-20 · 실행 Claude Code
환경: Windows 11, JDK `openjdk version "21.0.12.1" 2026-08-18 LTS`(C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot), Gradle Wrapper 9.7.1, GRADLE_USER_HOME=C:\dev\gradle-home
토큰·비밀번호 해시·키 값은 적지 않는다. 표본 비밀번호는 교재가 정한 공개 값이다.

## P03~P06 자동 검사 (MockMvc)

명령: `.\gradlew.bat test` (JAVA_HOME = 위 JDK)

| 회차 | 결과 | 종료 코드 | 내용 |
| --- | --- | --- | --- |
| 1 | 37개 통과 | 0 | AuthApiTest 10, PermissionRegressionTest 6, PublicReadTest 11, TipWriteTest 9, StarterSmokeTest 1 |
| 변이 확인 | 1개 실패(의도) | 1 | TipService의 올린 사람 비교를 `if (false)`로 바꾸자 `otherUserGets403AndNothingChanges` 실패 → 테스트가 권한 누락을 잡음. 원래 코드로 되돌림 |
| 2 | 39개 통과 | 0 | 아래 실패 수정 뒤 DevProfileTest 1, PublicReadTest의 H2 콘솔 꺼짐 검사 1 추가 |
| 3 | 40개 통과 | 0 | Codex 반박 반영(토큰-계정 대조, exp 필수) 뒤. `oldTokenDoesNotAuthenticateReassignedUserId` 추가, badTokensAre401에 exp·iat·username 없음, 이름 불일치, 계정 생성 전 발급 추가 |
| 변이 확인 2 | 2개 실패(의도) | 1 | 필터의 username·iat 대조와 exp 필수 검사를 빼자 위 두 테스트 실패 → 원래 코드로 되돌림 |
| 4 | 40개 통과 | 0 | Codex 2차 지적으로 iat·username 없는 토큰 사례를 badTokensAre401에 추가 |

검사 계획(docs/verification-plan.md) V01~V37을 모두 테스트로 옮겼다. 테스트는 메모리 DB `jdbc:h2:mem:icn-test`와 build/test-data/.jwt-secret을 쓴다.

## P07 실제 서버 확인

### 실패와 수정

| 무엇 | 실제 결과 | 원인 | 최소 수정 | 재검사 |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE=dev` 로 bootRun | 시작 실패 `BeanDefinitionOverrideException: ... bean 'h2Console'` | SecurityConfig의 dev 보안 체인 메서드 이름이 Spring Boot H2 콘솔 자동 설정 빈 이름(h2Console)과 같음. MockMvc 테스트는 dev 프로필을 켜지 않아 못 잡음 | 메서드 이름을 h2ConsoleSecurity로 바꿈. dev 프로필 컨텍스트 테스트(DevProfileTest) 추가 | 테스트 39개 통과, dev 프로필 bootRun 성공 |

### HTTP 확인 (dev 프로필, 127.0.0.1:8097, Invoke-WebRequest·curl.exe, 한글은 UTF-8 바이트로 전송)

| 요청 | 계정 | 기대 | 실제 |
| --- | --- | --- | --- |
| GET /api/health | — | 200 UP | 200 `{"project":"icn-subway-server","status":"UP"}` |
| GET /api/routes | — | 200, 3개 | 200, EXPRESS·LOCAL 5단계, T2 준비 중. Content-Type `application/json;charset=UTF-8` |
| GET /api/routes/EXPRESS/steps | — | 200 | 200, 1단계 HERE·arrivals·표지판 한영 병기 |
| GET /api/routes/NOPE/steps | — | 404 | 404 NOT_FOUND |
| GET /api/tips?route=LOCAL&step=4 | — | 200 | 200, 표본 팁 8(OUTDATED, 2026-09-30T03:00:00Z) |
| GET /api/tips?route=LOCAL&step=9 | — | 400 | 400 errors.step "1~5 사이의 단계 번호를 보내세요." |
| GET /api/routes + 깨진 토큰 | — | 401 | 401 UNAUTHORIZED |
| POST /api/auth/login | student01 | 200 | 200, tokenType Bearer, expiresIn 1800, user 101 |
| POST /api/auth/login 틀린 비밀번호 | student01 | 401 | 401 LOGIN_FAILED |
| POST /api/tips 한글·이모지, 앞뒤 공백, 빈 메모 | student01 | 201 | 201, Location /api/tips/9, 공백 제거, note null, VERIFIED |
| POST /api/tips + status 항목 | student01 | 400 | 400 INVALID_REQUEST_BODY errors.status |
| POST /api/tips 제목 81자 | student01 | 400 | 400 errors.title "80자 이하로 입력하세요." |
| POST /api/tips 토큰 없음 | — | 401 | 401 |
| GET /api/tips/9 | — | 200 | 200, mine=false |
| PATCH /api/tips/9/status OUTDATED | student01 | 200 | 200, OUTDATED, verifiedAt 갱신 |
| PATCH /api/tips/9/status | student02 | 403 | 403 FORBIDDEN |
| PATCH /api/tips/999999/status | student01 | 404 | 404 |
| GET /api/tips/9 재조회 | — | OUTDATED | OUTDATED (student02의 403 뒤에도 그대로) |
| GET /api/me/tips | student01 | 200 | 200, id 9, 8 |
| GET /api/me/tips 토큰 없음 | — | 401 | 401 UNAUTHORIZED (본문 JSON 확인: curl) |
| POST /api/auth/signup student01 | — | 409 | 409 USERNAME_TAKEN |
| GET /h2-console/ (dev) | — | 200 | 200 |

### 재시작 확인 (기본 프로필)

| 확인 | 기대 | 실제 |
| --- | --- | --- |
| 재시작 전 발급한 토큰으로 GET /api/me/tips | 200(키 재사용) | 200 |
| 경로 수 | 3(시드 중복 없음) | 3, EXPRESS 5단계 |
| GET /h2-console/ (기본 프로필) | 열리지 않음 | 401(보안 체인에서 막힘, 콘솔 서블릿 없음) |
| 새 가입 id | 103 | 103 |
| 새 팁 id | 9보다 큼 | 41 — H2 시퀀스 캐시(32) 때문에 강제 종료 뒤 번호가 건너뜀. 오류 아님, docs/data-model.md에 적음 |

확인을 마친 뒤 서버를 끄고 실행 확인용 DB(data/icn-subway.mv.db)를 지웠다. 다음 실행 때 시드부터 다시 들어간다. 키 파일 data/.jwt-secret은 남겼다(Git 제외).

## DB 감사 반영 (2026-10-21)

감사 A(코드·문서 일치)·감사 B(디자인 → DB 충분성)의 지적을 확인해 반영했다. 실행용 data/ DB에는 접속하지 않았다(그 시점 data/에는 키 파일만 있음).

명령: `.\gradlew.bat test --offline` 또는 `--rerun` (JAVA_HOME = 위 JDK, GRADLE_USER_HOME=C:\dev\gradle-home)

| 회차 | 결과 | 종료 코드 | 내용 |
| --- | --- | --- | --- |
| 5 | 40개 통과 | 0 | 바꾸기 전 기준선 |
| 6 | 1개 실패 → 수정 | 1 | SchemaScriptTest만 실행: 스크립트 생성 설정이 있으면 Hibernate가 ddl-auto를 보지 않아 메모리 DB에 표가 없었음(시드 SELECT 실패). 테스트 속성에 `jakarta.persistence.schema-generation.database.action=drop-and-create`를 더함. build/schema/create.sql은 이때 생성됨 |
| 7 | 1개 실패 → 수정 | 1 | 엘리베이터 검사의 복구 문 `jdbc.update(sql, null, null)`이 다른 오버로드로 불려 매개변수가 비었음 → `(Object) null`로 고침(테스트 코드 결함) |
| 8 | 49개 통과 | 0 | 엘리베이터 두 언어, 공백 범위, JSON 자료형, 시드 한 번, 스키마(INFORMATION_SCHEMA), docs/schema.sql 비교 검사 추가 |
| 변이 확인 3 | 3개 실패(의도) | 1 | 엘리베이터 조건을 예전 것(둘 다 null일 때만 null)으로, 공백 판정을 isWhitespace로, JsonInputConfig의 @Configuration을 끄자 `elevatorIsGivenOnlyWhenBothLanguagesAreFilled`, `unicodeSpacesAreTrimmedLikeTheApp`, `wrongJsonTypesAre400AndSaveNothing` 실패 → 원래 코드로 되돌림 |
| 9 | 1개 실패 → 수정 | 1 | Codex 반박 반영 중 새 비교 함수 검사(`comparisonKeepsCaseOfEnumValues`)가 괄호 둘레 공백 차이로 실패 → 정규화에 괄호·쉼표 둘레 공백 제거를 더함 |
| 10 | 51개 통과 | 0 | Codex 반박 반영 뒤. AuthApiTest 12, DevProfileTest 1, PermissionRegressionTest 6, PublicReadTest 14, SchemaScriptTest 2, SeedAndSchemaTest 4, StarterSmokeTest 1, TipWriteTest 11 |

### ddl-auto=update 실험 (임시 파일 DB, 실행용 data/ 아님)

프로젝트를 스크래치 폴더에 복사하고, 그 안의 임시 파일 DB(jdbc:h2:file:<스크래치>/expdb/icn, ddl-auto=update)로 두 번 시작했다. 두 번째 시작 전에 복사본의 RouteStep만 바꿨다(원본 프로젝트는 그대로).

| 바꾼 것 | 다음 시작 뒤 DB | 결과 |
| --- | --- | --- |
| photo_key 길이 40 → 50 | CHARACTER_MAXIMUM_LENGTH 50 | update가 열 길이를 바꿈(Codex 지적이 맞음) |
| enum Icon에 GATE 추가 | icon ENUM 값 (ESCALATOR, PLANE, TRAIN) 그대로 | update가 ENUM 값을 바꾸지 않음. `icon='GATE'` UPDATE는 'Value not permitted for column' 오류 |

이 결과로 docs/data-model.md '보존과 초기화'와 docs/schema.sql 주석을 고쳤다.

### Codex 반박 판정 (codex exec --sandbox read-only)

| 지적 | 심각도 | 판정 | 처리 |
| --- | --- | --- | --- |
| 엘리베이터 공백 판정이 isBlank라 U+00A0·U+FEFF 공백뿐인 문구를 통과시킴 | 중간 | 수용 | TextRules.strip으로 판정. 검사에 U+00A0, U+FEFF+U+0085 추가 |
| 'update는 기존 열의 자료형·길이를 바꾸지 않는다'는 설명이 틀림 | 중간 | 수용 | 위 실험으로 확인. 길이는 바뀌고 ENUM 값은 안 바뀐다고 고침 |
| docs/schema.sql 비교가 전부 소문자로 바꿔 ENUM 값 대소문자 오류를 놓침 | 중간 | 수용 | 대소문자 그대로 비교. 비교 함수 검사 추가 |
| stepNo ""·" "는 막지 않음(null로 넘어감) | 낮음 | 수용 | Integer의 EmptyString도 Fail. 검사 추가(400 INVALID_REQUEST_BODY) |
| 전역 Jackson 설정 영향(로그인·가입), 무토큰 + 잘못된 자료형의 401 검사 없음 | 낮음 | 수용 | AuthApiTest에 숫자·불리언 username·password 400, TipWriteTest에 무토큰 401, PATCH code 단정 추가 |

## API 문서 감사 반영 (2026-10-21)

입력: Claude 감사 GA-01~13, Codex 감사 GC1~10(둘 다 읽기 전용), DB 작업 검증에서 남은 후속 F1~F7. 실행용 data/와 실행 서버는 쓰지 않았다.
확인 방법: 코드를 읽고, 프로젝트를 스크래치 폴더에 복사해(data/·.git·build 제외) MockMvc 탐침 테스트로 경계 동작을 돌려 봤다. 상태 코드·헤더·오류 본문만 기록했고 토큰·비밀번호는 기록하지 않았다. 탐침 파일은 프로젝트에 넣지 않았다.

### 분류 확정

| 감사 항목 | 확정 | 처리 |
| --- | --- | --- |
| GA-01·GC3 공백 제거·코드 포인트 규칙의 범위 | A | api-spec.yaml 공통 규칙·스키마 설명, functional-spec F05 표 머리 |
| GA-02·GC4 판단 순서와 401 구분 | A | yaml 공통 규칙, auth-design '401과 403', functional-spec F07 |
| GA-03 Unauthorized의 message·조건 | A | yaml Unauthorized 설명과 두 예시, auth-design 표에 message·주는 곳 열. 공개 GET의 security 표기 통일은 D2로 보고 |
| GA-04·GC8 정하지 않은 항목·자료형 규칙의 범위 | A | yaml 'JSON 본문 규칙'(본문 4곳), 쿼리는 무시, null·중복 키. 중복 키 거절 여부는 D3으로 보고 |
| GC6 '헤더가 없으면 공개 GET은 200' | A | yaml 공통 규칙(입력·대상에 따라 200·400·404) |
| GA-05·GC9 GET /api/tips의 step 형식 오류 | A(Codex는 C) | 400은 이미 선언돼 있고 설명만 빠져 A로 봄. format: int32는 D1로 보고 |
| GA-06 PATCH 경로 id 400 | A | yaml PATCH 400 설명, 테스트 추가(F3) |
| GA-07·GC7·F7 가입·로그인 400/401 | A(Codex는 C) | 같은 이유로 A. 두 400에 INVALID_REQUEST_BODY 설명·예시, 로그인 400·401 설명 |
| GA-08·GC5(415) | C | 본문 4곳에 '415' 추가(components.responses.UnsupportedMediaType). api-reference 6절 C1 |
| GA-09·GC5(404·405·500·BAD_REQUEST) | C | yaml 공통 규칙에 문장으로 추가(paths에 없는 작업에는 응답을 달 수 없음). C2 |
| GA-10·GC10 GET /, GET /api/health | C(Codex는 D) | paths에는 넣지 않고 권한 요약에 '계약 밖 점검용'으로 적음, ApiContractDocTest 예외 목록. C3, paths에 넣을지는 검토 |
| GA-11 본문 stepNo가 int 범위 밖이면 errors {} | B | 보고만(B2) |
| GA-12·GC5의 406 MVC 밖 오류의 모양 | B | 보고만(B3). 탐침으로 일부 확인 |
| GA-13·GC1·GC2 WWW-Authenticate·Allow 헤더 없음 | B | 보고만(B1) |
| 새로 찾음: yaml 흐름 표기 { } 안 설명의 쉼표 | A | Tip.note, TipCreateRequest.note, SignupRequest.password 설명이 쉼표에서 잘려 엉뚱한 스키마 키(`없으면 null`, `빈 값은 null`, `UTF-8 72바이트 이하`)가 생겨 있었다. 따옴표로 감쌌고 ApiContractDocTest가 모르는 키워드를 잡는다 |
| 새로 찾음: Accept가 JSON을 받지 않는 요청 | B | 406·빈 본문, 그때 서비스 오류는 처리하지 못한 예외(MockMvc에서 ServletException). B3에 합침 |
| 새로 찾음: 토큰이 있는 HEAD·OPTIONS | A | Spring이 200으로 답한다(OPTIONS는 Allow 헤더도). yaml·api-reference 문장에 반영 |

D(계약 변경 필요)로 보고만 한 것: D1 format: int32, D2 공개 GET security 표기, D3 중복 키 거절, D4 공백뿐인 비밀번호로 가입(지금 201, 그 값으로 로그인 200. 탐침으로 확인). 표는 docs/api-reference.md 6절.

### DB 작업 후속 F1~F7

| # | 처리 |
| --- | --- |
| F1 | docs/schema.sql 머리말을 'create 스크립트, 실행 DB의 표 정의 그대로가 아님'으로 고치고 다른 점을 적었다. 스크래치 사본의 메모리 DB를 ddl-auto=update로 띄워 Hibernate 로그로 확인: update는 기본 키 열 → 열 이름 순 → FK 열(route_code·owner_id) 마지막이고, UNIQUE는 표를 만든 뒤 `alter table ... add constraint UK... unique`로 단다. 같은 내용을 data-model.md 7행, README 표, SchemaScriptTest 설명에 맞췄다 |
| F2 | SchemaScriptTest의 static 블록이 build/schema/create.sql을 먼저 지운다. 파일이 이번 실행에서 생겼는지 단정 추가 |
| F3 | PermissionRegressionTest.badTipIdIs400AndMissingIdIs404(abc·1.5·long 범위 밖 → 400 errors.id, 본문이 틀려도 errors에는 id만, 토큰 없으면 401, 0·-1·999999 → 404, 팁 행 그대로). PublicReadTest.tipDetail에 GET 0·-1 → 404 |
| F4 | PublicReadTest 97행, TipWriteTest 18·73·150·155행의 U+00A0·U+FEFF·U+2028·U+3000을 \u 이스케이프로 바꿈(바꾼 뒤 코드 포인트가 같은지 바이트로 확인) |
| F5 | SeedAndSchemaTest: ENUM 거절의 근본 원인이 SQLException SQLSTATE 22030('Value not permitted', 'DELETED' 포함)인지까지 확인 |
| F6 | docs/schema.sql의 ENUM 조회문을 INFORMATION_SCHEMA.COLUMNS와 이어 표·열 이름이 함께 나오게 바꿈(H2 2.4.240 메모리 DB에서 실행 확인) |
| F7 | api-spec.yaml 가입·로그인 400에 INVALID_REQUEST_BODY(문자열이 아닌 값은 JsonInputConfig로 거절) 설명·예시 |

### 더한 테스트와 문서

- ApiContractDocTest(4개, 항상 실행): 코드의 /api/** ↔ yaml paths(예외 GET /api/health), api-reference.md의 '메서드 경로' 표기와 예시 링크, docs/api-examples의 operation·상태 코드 선언·필수 필드·헤더·숨김 값과 모든 작업의 성공 예시, yaml 스키마 키워드.
- ApiExamplesGenerator(API_EXAMPLES=1일 때만)와 yaml 읽기 도우미 ApiSpec. 예시 30개 → docs/api-examples/.
- 기존 클래스: TipWriteTest.nonJsonBodyIs415AfterTokenCheck, AuthApiTest.authBodiesMustBeJsonAndSignupIgnoresStaleHeader·undeclaredMethodsAndPaths. 기존 테스트에 단정 몇 줄(401 문구 두 가지, 숫자가 아닌 step 문구, 쿼리 무시, 무효 헤더가 400·404보다 먼저, 로그인의 정하지 않은 항목·공백뿐인 비밀번호·400이 401보다 먼저).
- 문서: docs/api-reference.md, docs/api-examples/README.md, docs/samples/README.md(실제 응답 표본은 미완료).

### 실행

명령: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'; $env:GRADLE_USER_HOME='C:\dev\gradle-home'; .\gradlew.bat test --offline --rerun`
생성기: `$env:API_EXAMPLES='1'; .\gradlew.bat test --offline --rerun --tests '*ApiExamplesGenerator*'; Remove-Item Env:API_EXAMPLES`

| 회차 | 결과 | 종료 코드 | 내용 |
| --- | --- | --- | --- |
| 11 | 51개 통과 | 0 | 바꾸기 전 기준선 |
| 12 | 생성기 통과, 예시 30개 저장, 문제 0건 | 0 | 응답 파일을 읽어 토큰·비밀번호 숨김, Location, 시드 팁 시각을 확인 |
| 13 | 생성기 다시 실행 | 0 | 30개 파일의 해시가 12회차와 모두 같음(매번 같은 결과). docs/api-examples/로 복사 |
| 14 | 60개 중 1개 실패 → 수정 | 1 | ApiContractDocTest가 응답의 errors.password(안내 문구)까지 비밀번호로 봄(테스트 코드 결함). 검사를 request.body.password로 좁히고, 응답 본문(errors 밖)에 password 항목이 없는지는 따로 본다 |
| 15 | 59개 통과, 1개 건너뜀(생성기) | 0 | ApiContractDocTest 4, AuthApiTest 14, DevProfileTest 1, PermissionRegressionTest 7, PublicReadTest 14, SchemaScriptTest 2, SeedAndSchemaTest 4, StarterSmokeTest 1, TipWriteTest 12 |
| 변이 확인 4 | 8개 실패(의도) | 1 | 스크래치 사본에서 일부러 깨뜨림: 코드에 GET /api/debug 추가, api-reference.md의 PATCH 표기 바꿈, yaml 설명의 따옴표 제거, 예시에 토큰 모양 값, 예시 상태 418, ENUM 대신 FK 위반, 스크립트 생성 끔(옛 파일 남김), yaml Tip.required에 없는 필드 → 각각 맞는 검사가 실패. 생성기는 문제 3건을 이름·이유만 적고 27개만 저장했고, 그 실행의 로그에는 토큰·Authorization·응답 본문이 없었다. 프로젝트 파일은 바꾸지 않음 |

### Codex 문서 사실 확인 (codex exec --sandbox read-only, 종료 코드 0)

새 문서와 yaml 변경을 코드와 대조해 달라고 했다(파일 수정·서버·테스트·data/ 접근 금지). 4건을 지적했고 모두 스크래치 탐침으로 확인해 수용했다.

| 지적 | 판정 | 처리 |
| --- | --- | --- |
| '토큰이 있으면 선언하지 않은 요청은 404·405'에 /api/auth/** 예외가 빠짐(GET /api/auth/login은 토큰이 있어도 401) | 수용 | yaml 공통 규칙, api-reference 1절·4절·6절 C2 |
| '토큰이 있는 HEAD·OPTIONS는 200'이 넓다(HEAD는 GET처럼 404·400이 날 수 있고 /api/auth/** 아래는 401) | 수용 | HEAD는 같은 주소의 GET처럼, OPTIONS는 200 + Allow로 고침 |
| '공백뿐인 비밀번호는 401'이 틀림(그 값으로 가입했으면 200) | 수용 | yaml 로그인 401·LoginRequest, api-reference A8. 공백뿐인 비밀번호 가입은 D4로 보고 |
| 'username은 그대로 정규식 검사'는 가입에만 맞다(로그인은 isBlank 뒤 그대로 찾음) | 수용 | yaml 공통 규칙·LoginRequest, api-reference 1절·A8 |

Codex가 동의한 부분: 오류 code·message, 서비스의 입력 검사 → 404 → 403 순서, '앱 처리' 열과 functional-spec의 일치. 확인 못 함으로 남긴 것: 헤더·Jackson·방화벽·Accept의 실행 결과(이쪽은 MockMvc 탐침으로 확인함), schema.sql 머리말의 DDL 순서(F1 실험으로 확인함), 예시 재생성의 동일성(13회차로 확인함).

### 마지막 실행

| 회차 | 결과 | 종료 코드 | 내용 |
| --- | --- | --- | --- |
| 16 | 59개 통과, 1개 건너뜀(생성기) | 0 | Codex 지적 반영 뒤 전체 `.\gradlew.bat test --offline --rerun` |
| 17 | 생성기 통과, 30개 저장, 문제 0건 | 0 | build/api-examples와 docs/api-examples의 30개 파일 해시가 모두 같음 |
| 18 | 60개 통과 | 0 | API_EXAMPLES=1로 전체 실행(생성기와 다른 테스트가 한 JVM에서 같이 돌아도 됨). 예시는 그대로, `eyJ` 0건 |

## API 문서 반박 검토 반영 (2026-10-21, 2차)

입력: Codex 읽기 전용 반박 검토 RC-01~11, Claude 검증 RV-01~10. 항목마다 코드·앱 저장소를 다시 읽고, 스크래치 사본(data/·.git·build 제외)에서 MockMvc 탐침으로 다시 확인한 뒤 판정했다. 실행용 data/와 실행 서버는 쓰지 않았다. 탐침과 변이는 상태 코드·code·키 이름만 기록했다(토큰·비밀번호 없음).

### 판정

| # | 판정 | 확인한 것 | 처리 |
| --- | --- | --- | --- |
| RC-01 | 수용(A) | 탐침: 익명 GET·POST·DELETE·HEAD /error → 500과 Spring Boot 기본 본문 `{timestamp, status, error}`(status 999), OPTIONS /error → 200. dev 프로필의 익명 GET·POST·DELETE /h2-console/x는 401이 아님(MockMvc는 콘솔 서블릿까지 가지 않아 404) | yaml 공통 규칙, api-reference 1절·4절 UNAUTHORIZED·6절 C2에 두 예외, 6절 B3 ③ |
| RC-02 | 수용(A) | 탐침: 메모리 DB의 app_user 표 이름을 바꾸고 유효한 토큰으로 GET /api/routes → 필터 안 DB 오류가 MockMvc perform()에서 예외로 그대로 나옴(ApiAdvice를 거치지 않음). 같은 DB 오류가 컨트롤러 안(로그인)이면 500 INTERNAL_ERROR | yaml·api-reference의 'Error 모양'과 '500' 문장을 'ApiAdvice가 바꾼 오류'로 좁힘, 6절 B3 ④, functional-spec 5절 첫 문장. 코드 수정(B)은 하지 않음 |
| RC-03 | 보류 | 앱 저장소: subway_repository.dart는 errors 키를 fieldErrors에 담지만 add_tip_screen.dart는 401만 따로 처리하고 나머지는 '올리지 못했어요'. api-reference는 functional-spec F05와 같다 | 앱을 고칠지 명세를 바꿀지는 사람이 정함. api-reference 3절 머리에 '앱 처리'는 명세 기준이라고 밝히고 6절 E1에 올림 |
| RC-04 | 수용(T) | 기존 검사는 최상위 필수 필드만 봄 | ApiSpec.check: $ref·allOf·items를 따라가며 필수 필드, 자료형, enum, nullable, date-time·int64, minimum, pattern, yaml에 없는 필드. 테스트와 생성기가 같이 씀 |
| RC-05 | 수용(T) | 상태·code·message를 대조하지 않았음 | 상태별 code 표(401은 로그인이면 LOGIN_FAILED, 그 밖은 UNAUTHORIZED). 오류 예시 파일의 (code, message)가 같은 작업·상태의 yaml 예시 중 하나와 같은지, 같은 errors 키의 문구가 같은지. yaml 오류 예시의 (code, message)가 예시 파일에 실제로 나오는지 |
| RC-06 | 수용(T) | meta.operation만 믿었음 | ApiSpec.operationFor로 request.method·url(쿼리 뺌)이 meta.operation인지 확인. 생성기는 operation을 요청에서 정함 |
| RC-07 | 수용(T) | 파일 → 링크 한 방향뿐이었음 | 링크한 파일이 있는지, 모든 파일을 링크했는지, 링크한 파일이 그 절의 작업인지 |
| RC-08 | 수용(A) | 생성기는 문제가 있어도 통과한 파일을 썼음 | 만들기 모드에서 시작할 때 build/api-examples를 비우고, 문제가 하나라도 있으면 아무것도 쓰지 않음. examples README 명령에 종료 코드·파일 수 확인, 0일 때만 복사, 마지막 --rerun 재검사. 평소 test의 비교(RV-05)와 '모든 선언 응답에 예시'(RV-03)가 빠진 파일을 잡음 |
| RC-09 | 수용(T) | 시각·errors 아래를 보지 않았음 | 예시 안의 date-time 값은 시드 팁 시각이거나 meta.replaced에 적은 고정값이어야 함. errors 값은 문자열만, 아니면 그 안까지 password·hash 항목을 봄 |
| RC-10 | 수용(A) | tip-status.json의 verifiedAt = createdAt | PATCH 응답의 verifiedAt 고정값을 `2026-10-21T00:05:00Z`로(등록은 00:00). examples README 표, api-reference A6 예시 줄 |
| RC-11 | 수용(A) | 탐침: 본문 전체 `null`은 본문 4곳 모두 400 INVALID_REQUEST_BODY(errors {}), 항목 값 null은 VALIDATION_FAILED, 공백뿐인 본문도 INVALID_REQUEST_BODY | yaml·api-reference 문장을 나눔. AuthApiTest.wholeNullBodyIsUnreadableButNullFieldsAreValidation 추가 |
| RV-01 | 수용(문서), build.gradle은 보류 | 아래 'RV-01 다시 확인' | 확인 명령을 모두 `--rerun`으로(README, api-reference 0절, examples README, samples README), ApiContractDocTest 설명에 적음. test 작업에 docs 입력을 더하는 build.gradle 수정은 요청 범위 밖 파일이라 하지 않았다(사람이 정함) |
| RV-02 | 수용(T) | 같은 표기가 두 번 나와 한 줄을 지워도 통과 | 2절 표의 행과 3절 제목을 따로 뽑아 번호·작업 비교, 3절 상태 표 ↔ yaml 응답, code 칸, 2절 '성공' 칸 |
| RV-03 | 수용(T) | 선언 32개 중 6개에 예시가 없었음 | '선언한 모든 (작업, 상태 코드)에 예시' 단정, 생성기에 예시 6개 추가(30 → 36: 401 무효 토큰 3개, 415 3개) |
| RV-04 | 수용(T) | RC-04~06과 같은 뿌리 | 위 검사 + Location = 요청 경로/응답 id |
| RV-05 | 수용(T) | 코드 문구를 바꿔도 통과했음 | 생성기를 평소 test에서 항상 돌려 docs/api-examples와 JSON으로 비교(파일 목록·값, 다른 곳은 파일 이름과 JSON 경로만 알림) |
| RV-06 | 수용(A) + B 보고 | 탐침: 경로 id `0x7`·`0X7`·`%237`·`%207`·`7%20`·`%097`·`007`·`%2B7` → 팁 7(200), `1%202` → 404(12로 읽음), `%C2%A07` → 400, 소유자의 PATCH /api/tips/0x8/status → 200. 쿼리 step은 `0x4`·`1%202`·`%C2%A04` → 400, `%2B4`·`04`·탭·줄바꿈 → 200 | yaml 공통 규칙·TipId·step 설명, api-reference 1절 '숫자 형식'·A4 표. 구현 결함은 6절 B4로 보고만 |
| RV-07 | 수용(A) | 탐침: text/plain + 빈 본문 → 415(PATCH·로그인·팁 등록), Content-Type 없음 + 빈 본문 → 400, 없음 + 본문 → 415. 토큰이 있는 OPTIONS /api/tip → 404, /api/tips/999999 → 200(Allow: GET,HEAD,OPTIONS) | yaml 공통 규칙·UnsupportedMediaType, api-reference 1절 |
| RV-08 | 수용(A) | RC-10과 같음 | RC-10 |
| RV-09 | 수용(A) | 탐침: LOCAL을 available=false로 바꿔도 GET /api/routes/LOCAL/steps → 200, 단계 5개 | api-reference A1의 available 설명 |
| RV-10 | 수용(T) | docs/samples에는 자동 검사가 없었음 | ApiContractDocTest.samplesHideSecrets: README.md를 뺀 파일의 토큰 모양 값, JSON의 accessToken·Authorization·password 값(비었거나 `<숨김`으로 시작). samples README에 적음 |

분류 A(문서 설명 오류)로 고친 것: RC-01·02·08·10·11, RV-06(문서 부분)·07·08·09. B(구현 결함, 보고만): B3 ③④ 추가, B4 새로(경로 id). C·D로 새로 올린 것은 없다. T(테스트 결함)는 모두 고쳤다.

### 변이 확인(스크래치 사본, ApiContractDocTest·ApiExamplesGenerator만 --rerun)

대조군(바꾸지 않음)은 7개 모두 통과했고, 아래 변이 36개는 모두 실패했다(의도). 앞의 검증에서 통과하던 변이(RV-02~05)도 이제 잡힌다.

| 변이 | 관련 | 잡은 검사 |
| --- | --- | --- |
| tips를 문자열로, tips[0].ownerId·mine 삭제, status를 DELETED로, title을 null로 | RC-04 | examplesMatchSpecAndHideSecrets(스키마), 생성기 비교 |
| 상태 403→404, 400→401(본문 code는 그대로), Location을 /api/tips/999로 | RC-05·RV-04 | examplesMatchSpecAndHideSecrets(yaml 예시와 code·message가 다름, Location), 생성기 비교 |
| 403 파일의 code FORBIDDEN→NOT_FOUND | RC-05 | 위 둘 + specExamplesMatchSchemasAndExampleFiles |
| yaml 로그인 401 예시의 message만 바꿈 | RC-05 | examplesMatchSpecAndHideSecrets, specExamplesMatchSchemasAndExampleFiles |
| request.method GET→DELETE, my-tips.json의 url을 /api/tips로 | RC-06 | examplesMatchSpecAndHideSecrets, 생성기 비교 |
| 없는 파일 링크, 링크 삭제, 다른 API 절에 링크 | RC-07 | apiReferenceMatchesSpec |
| 새 팁 verifiedAt을 실행 시각 모양으로, errors.password에 passwordHash 객체 | RC-09 | examplesMatchSpecAndHideSecrets, 생성기 비교 |
| 2절 A6 행 삭제, A5 제목 삭제, A6 표 403 행 삭제, A8 표 415 행 → 409, A2 표 404의 code를 FORBIDDEN으로, A9 성공 칸을 200으로 | RV-02 | apiReferenceMatchesSpec |
| yaml에서 PATCH 415, 로그인 415, 단계 401, 팁 하나 401 삭제(예시가 없어 예전엔 통과) | RV-03 | examplesMatchSpecAndHideSecrets(선언 없음), apiReferenceMatchesSpec(표 ≠ yaml), 생성기 |
| 코드(ApiAdvice)의 415 문구, 코드(AuthService)의 LOGIN_FAILED 문구만 바꿈 | RV-05 | 생성기 비교(docs/api-examples가 낡음) |
| docs/samples에 토큰 모양 값, 숨기지 않은 accessToken | RV-10 | samplesHideSecrets |
| 생성기의 기대 상태 하나를 틀리게 하고 API_EXAMPLES=1 | RC-08 | 생성기 실패, build/api-examples의 json 0개(아무것도 쓰지 않음) |
| 4xx 예시 파일과 그 링크를 함께 삭제 | RC-08·RV-03 | examplesMatchSpecAndHideSecrets(선언 응답에 예시 없음), 생성기 비교 |
| yaml Tip.properties에서 example 삭제 | RC-04 | examplesMatchSpecAndHideSecrets(yaml에 없는 필드), 생성기 |
| yaml 경로 목록 200 예시 값 바꿈, yaml 403 예시 code를 NOT_FOUND로 | RC-05 | specExamplesMatchSchemasAndExampleFiles(뒤의 것은 examplesMatchSpecAndHideSecrets도) |

RV-01 다시 확인(같은 사본, 전체 test): ① `test --offline` → 실행·통과 ② 그대로 다시 → `:test UP-TO-DATE`, 1초 ③ api-reference.md에서 예시 링크 하나만 지우고 `test --offline` → `:test UP-TO-DATE`, BUILD SUCCESSFUL, 1초(테스트 0개) ④ `--rerun` → 63개 중 1개 실패(apiReferenceMatchesSpec). 문서만 바꾸면 여전히 `--rerun`이 필요하다.

### 실행

명령: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'; $env:GRADLE_USER_HOME='C:\dev\gradle-home'; .\gradlew.bat test --offline`(--rerun은 표에 적음)

| 회차 | 결과 | 종료 코드 | 내용 |
| --- | --- | --- | --- |
| 19 | 생성기(만들기) 통과, 36개 저장, docs와 다른 곳 7건 | 0 | 새 예시 6개와 tip-status.json의 verifiedAt. 나머지 29개는 바이트가 같음. 새 파일을 읽어 본 뒤 docs/api-examples로 복사 |
| 20 | 63개 통과 | 0 | `.\gradlew.bat test --offline`(테스트 코드가 바뀌어 실제로 돎). ApiContractDocTest 6, ApiExamplesGenerator 1(이제 평소에도 돌며 비교, 건너뜀 0), AuthApiTest 15, DevProfileTest 1, PermissionRegressionTest 7, PublicReadTest 14, SchemaScriptTest 2, SeedAndSchemaTest 4, StarterSmokeTest 1, TipWriteTest 12 |
| 21 | UP-TO-DATE(테스트 0개) | 0 | 같은 명령을 한 번 더. 그래서 아래 마지막 검사는 --rerun으로 했다 |
| 22 | 63개 통과 | 0 | `.\gradlew.bat test --offline --rerun` |
| 23 | 생성기 통과, 36개, docs와 다른 곳 0건 | 0 | `$env:API_EXAMPLES='1'; .\gradlew.bat test --offline --rerun --tests '*ApiExamplesGenerator*'`. build/api-examples 36개가 docs/api-examples와 바이트까지 같음 |
| 24 | 63개 통과 | 0 | API_EXAMPLES=1로 전체 `--rerun`(만들기 모드와 다른 테스트가 한 JVM에서 같이 돎). 예시 36개와 새 문서에 토큰 모양 값·표본 비밀번호·Authorization 항목 0건 |

## 확인하지 못한 것

- Android 에뮬레이터(10.0.2.2:8097)와 실제 Flutter 앱에서의 연결. 서버는 127.0.0.1에만 열려 있어 실제 휴대폰은 접속할 수 없다.
- Flutter 웹(브라우저)에서 부를 때 필요한 CORS. 설정하지 않았다.
- 1800초 실제 만료(테스트는 과거 시각으로 만든 토큰으로 대신 확인).
- 단계 문구·표지판·층수의 사실 여부(현장 확인 전 초안).
- 실행용 data/ DB의 실제 표가 docs/schema.sql과 같은지(규칙상 학생이 H2 콘솔에서 직접 비교). docs/data-model.md의 mermaid 그림이 실제로 그려지는지.
- 방화벽이 거절한 주소(`;`, `%2F`)와 Accept가 JSON이 아닌 요청에 실행 서버(Tomcat)가 실제로 주는 본문. MockMvc로는 400·406과 빈 본문까지만 봤다.
- 보안 필터 안에서 난 예외(토큰 확인 중 DB 오류)에 실행 서버가 주는 응답(B3 ④). MockMvc로는 예외가 ApiAdvice를 거치지 않고 그대로 나오는 것까지만 봤다. Spring Boot 기본 오류 응답(500)일 것으로 보지만 추정이다.
- RV-01의 build.gradle 수정(test 작업에 docs/를 입력으로 더하기)은 하지 않았다. 그 전까지는 문서만 고쳤으면 `--rerun`을 붙여야 문서 검사가 돈다.
- docs/samples/의 실제 응답 표본(10장, 학생이 실행 서버에서 모은다).
- 5장 입력 사본 6개(docs/S02·S03-screen-spec.md, screen-flow.csv, ui-handoff.md, ui-requirements.csv, ui-validation-decisions.md)에 커밋하지 않은 변경이 있다. 2026-10-21 5장 문서 갱신 작업(Claude Code)이 원본 C:\dev\chapter05-ai\docs에 맞춰 덮어쓴 사본이다(첫 줄 '사본 · 원본 …' 표시). 사람이 검토한 뒤 커밋한다.

## 8장 Postman·Newman 검사

교재 8장 순서(P00 → P01 → P02 → P04 → 실행 → P03 → P05)로 진행했다. 이 절의 실행은 모두 **Claude Code가 학생 PC에서 실행**한 것이다(사용자 요청, 2026-10-21). 학생이 직접 실행한 결과는 아래에 따로 적는다.

### 8장 P02 권한·상태 구현

- 요청 1(점검) 결과:
  - 권한: 등록자만 상태를 바꾸고(403), 판단 순서가 401 → 400 → 404 → 403인 것이 코드·테스트와 docs/permission-matrix.md에서 같다. 고칠 곳 없음.
  - 상태: '같은 상태를 다시 보내면 다른 값은 그대로, verifiedAt만 다시 기록'(docs/state-rules.md 전이 표)을 테스트가 상태 값만 확인하고 있었다.
- 요청 2(고른 항목): docs/state-rules.md 전이 표의 '같은 상태 재요청' 행.
  - 테스트 추가: PermissionRegressionTest.sameStatusAgainKeepsOtherValuesAndRefreshesVerifiedAt
  - 구현은 바꾸지 않았다(이미 규칙대로 동작).
- 명령: `.\gradlew.bat test --offline --rerun` / 종료 코드: 0 / 64개 통과 / 실패한 테스트: 없음
- Codex 지적 5를 반영해 이 테스트를 강화한 뒤(요청 시각 범위, DB 값 = 응답 값, VERIFIED·OUTDATED 두 반복) 같은 명령을 다시 실행: 종료 코드 0, 64개 통과

### 8장 Newman 실행 1회차 (Claude Code 실행, 2026-10-21 12:56)

- 검사한 서버: http://127.0.0.1:8097
  - 내 7장 서버를 dev 프로필로 켬: `.\gradlew.bat bootRun --offline --args="--spring.profiles.active=dev"`
  - 로그: `Tomcat started on port 8097 (http)`
  - 켜기 전 data/에 DB 파일이 없어서, 시드(경로 3·단계 10·계정 2·팁 2)부터 새로 만들어졌다.
- 응답 확인: `GET /api/health` 200, `GET /api/routes` 200
- 실행 도구: Newman 6.2.1(C:\dev\chapter08-api\tools\newman, 교재 001_Newman준비.ps1로 준비). Postman 앱 화면은 쓰지 않았다.
- 사용한 파일:
  - postman/my-api.collection.json
  - postman/my-api.environment.json
  - 비밀번호는 `--env-var`로 실행할 때만 넘겼다. local.environment.json은 만들지 않았다.
- Newman 명령(비밀번호 자리는 가림):
  `newman run postman/my-api.collection.json -e postman/my-api.environment.json --env-var "passwordA=<표본 비밀번호>" --env-var "passwordB=<표본 비밀번호>" --reporters cli,junit --reporter-junit-export reports/my-api.junit.xml --color off`
- 종료 코드: 0 / 요청 42개 · 검사 98개 · 실패 0개 / 걸린 시간 4.7초
- 보고서: reports/my-api.junit.xml(토큰 모양 값·비밀번호 0건 확인)
- 검사로 만든 팁: tipId 9(LOCAL 2단계, 제목 '[Postman] 8장 검사 …'). 마지막에 VERIFIED로 되돌렸다. 삭제 API가 없어 DB에 남아 있다.
- 명령 실수 1번: 처음에 없는 옵션 `--no-color`를 넣어 Newman이 검사를 시작하기 전에 끝났다(오류: unknown option). `--color off`로 고쳐 다시 실행했다. 검사 실패는 아니다.

| 요청 이름 | 계정 | 기대 | 실제(상태·code) | 재조회 결과 | 상태 |
|---|---|---|---|---|---|
| 로그인 없이 팁 등록 | 없음 | 401 UNAUTHORIZED | 401 UNAUTHORIZED | 2단계 팁 개수 그대로 | 실행 |
| 제목 공백만 / 81자 / 본문에 ownerId | A | 400 VALIDATION_FAILED / VALIDATION_FAILED / INVALID_REQUEST_BODY | 같음 | 2단계 팁 개수 그대로 | 실행 |
| A가 새 팁 등록 | A | 201, Location, ownerId 101, VERIFIED | 201, Location /api/tips/9, ownerId 101, VERIFIED | 공개 조회 값이 등록 응답과 같음 | 실행 |
| 토큰 없이 / 무효 토큰으로 상태 변경 | 없음·무효 | 401 UNAUTHORIZED | 401 UNAUTHORIZED | 11개 값 유지 | 실행 |
| B가 A의 팁 상태 변경(OUTDATED) | B | 403 FORBIDDEN | 403 FORBIDDEN | 11개 값 유지 | 실행 |
| B가 잘못된 status(DELETED) | B | 400 VALIDATION_FAILED(403보다 먼저) | 400 VALIDATION_FAILED | 11개 값 유지 | 실행 |
| A가 소문자 status / {} / status+title | A | 400 VALIDATION_FAILED ×2 / 400 INVALID_REQUEST_BODY | 같음 | 11개 값 유지 | 실행 |
| A가 없는 팁 상태 변경 | A | 404 NOT_FOUND | 404 NOT_FOUND | 해당 없음 | 실행 |
| 팁 삭제 시도 | 없음 / A | 401 / 405 METHOD_NOT_ALLOWED | 401 / 405 METHOD_NOT_ALLOWED | 11개 값 유지 | 실행 |
| A가 OUTDATED로 바꾸고 같은 요청 반복 | A | 200 OUTDATED, 다른 값 유지, verifiedAt만 다시 기록 | 200 OUTDATED, 같은 id·ownerId | 마지막 응답과 같음 | 실행 |
| A·B의 내 팁, 단계 팁 목록의 mine | A·B | A만 새 팁 포함, mine A=true·B=false | 같음 | 해당 없음 | 실행 |
| 이미 있는 아이디로 가입 | 없음 | 409 USERNAME_TAKEN | 409 USERNAME_TAKEN | 새 계정 없음(로그인된 계정) | 실행 |

- '11개 값'은 id, route, stepNo, title, location, note, status, verifiedAt, createdAt, ownerId, example이다. 교재의 '여섯 필드'에 해당한다. mine은 토큰에 따라 달라서 뺐다.
- H2 콘솔 확인: 하지 않음. DB 접속·SQL 실행은 학생 몫(4장 규칙)이라 'H2 미확인'으로 둔다.

### 8장 Codex 반박 판정 (codex exec --sandbox read-only, 1회차 뒤)

Codex는 '반드시 실패하는 요청은 없다'고 확인했다. 대신 통과해도 규칙을 증명하지 못하는 약한 검사와 순서 문제를 짚었다.

| # | 심각도 | 지적 | 판정 | 처리 |
| --- | --- | --- | --- | --- |
| 1 | 중간 | 같은 상태 반복에서 verifiedAt을 '같거나 뒤(at.least)'로 확인해, 갱신하지 않아도 통과 | 수용 | 상태를 바꾸는 요청 앞에 30ms를 기다리고 '이전 값보다 뒤(above)'로 확인 |
| 2 | 중간 | snapshot이 응답에 있는 키만 저장하고 비교해, 빠진 필드는 비교에서도 빠짐. 등록 응답을 보낸 값과 비교하지 않음 | 수용 | 11개 값의 존재·자료형을 확인한 뒤 저장하고, 비교는 고정된 11개 목록으로 함. 등록 응답의 route·stepNo·title·location·note를 보낸 값과 비교 |
| 3 | 중간 | B의 내 팁 검사가 A의 새 팁 하나만 없는지 봄 | 수용 | userIdB를 저장하고, A·B 내 팁의 모든 항목이 자기 ownerId·mine true인지 확인 |
| 4 | 중간 | 정상 등록보다 거절 등록을 먼저 실행(교재 순서와 다름), DELETE 요청 포함 | 일부 수용 | 순서를 '로그인 → A 등록 → 거절·재조회'로 바꿈. DELETE 시도는 유지: 삭제 API가 없다는 것(401·405)과 시도 뒤 팁이 그대로인지를 확인하는 거절 시험이며 권한표 '허용하지 않는 메서드'와 같음 |
| 5 | 중간 | 새 JUnit이 '지금 시각으로 갱신'을 확인하지 않고 반복 OUTDATED 행도 빠짐 | 수용 | 요청 전후 시각 사이인지, DB verified_at이 응답 값과 같은지 확인. VERIFIED·OUTDATED 두 반복을 모두 검사 |
| 6 | 낮음 | 권한표의 `GET /api/tips?route=&step=`이 그대로 보내면 400 | 수용 | 자리표시 `route={EXPRESS·LOCAL}&step={1~단계 수}`와 예시, 400 조건을 적음 |
| 7 | 낮음 | 405 설명이 HEAD·OPTIONS 예외를 덮음 | 수용 | 유효한 토큰의 HEAD는 GET처럼, OPTIONS는 200·Allow(없는 주소면 404)라고 적음 |

- Codex가 확인하지 못했다고 한 것: Postman 앱에서 실행한 뒤 컬렉션·환경을 다시 내보내면 토큰·비밀번호가 나갈 수 있다 → docs/verification-plan.md와 컬렉션 설명에 주의 문구를 넣었다.

### 8장 Newman 실행 2회차 (Claude Code 실행, Codex 지적 반영 뒤)

- 같은 서버(1회차와 같은 실행, dev 프로필, 127.0.0.1:8097), 같은 명령
- 종료 코드: 0 / 요청 42개 · 검사 107개 · 실패 0개 / 걸린 시간 4.3초 / 기다리기 스크립트 4개 실행
- 검사로 만든 팁: tipId 10(LOCAL 2단계). 마지막에 VERIFIED로 되돌렸다.
- 보고서: reports/my-api.junit.xml(2회차 결과로 덮어씀, 토큰 모양 값·비밀번호 0건)
- 1회차 표의 결과는 2회차에서도 같다. 2회차에서 더 확인한 것:
  - 등록 응답이 보낸 값을 그대로 담음
  - 반복 요청의 verifiedAt이 이전 값보다 뒤
  - A·B 내 팁의 모든 항목이 자기 팁

### 8장 실패 F01

- 없음. 1·2회차 모두 실패한 검사가 없다.

### 학생 직접 실행

- 미실행. 학생이 같은 Newman 명령(또는 Postman Runner)을 실행하면 여기에 실행 도구·종료 코드·요청·검사·실패 수를 적는다.

## 패키지 구조 정리 (2026-10-21, Claude Code 실행)

- 한 패키지에 있던 main 클래스 27개를 역할별 패키지 8개로 옮겼다(`git mv`, 이력 유지).
  - 패키지: controller, service, repository, domain, dto, security, config, common
  - 진입점 StarterApplication은 맨 위 패키지에 그대로 둔다.
  - 구조는 README '패키지 구조' 절에 있다.
- 바꾼 것:
  - package·import 줄
  - TextRules를 public으로 열었다(클래스와 strip·length 메서드). 다른 패키지의 서비스가 쓰기 때문이다.
  - 로직은 바꾸지 않았다.
- 문서:
  - README에 '패키지 구조' 절 추가
  - docs/data-model.md의 SeedData 경로
  - docs/permission-matrix.md·docs/state-rules.md의 줄 번호 참조 25곳. import가 늘어난 만큼 옮겼다.
  - 이 절보다 앞에 적힌 기록의 줄 번호는 옮기기 전 기준이다.
- 테스트: `.\gradlew.bat test --offline --rerun` 종료 코드 0, 64개 통과
- 실행 서버 확인: 서버를 dev 프로필로 다시 켰다.
  - /api/health·/api/routes·/api/routes/EXPRESS/steps·/h2-console/ 모두 200
  - 같은 Newman 컬렉션(cli 보고서만) 실행: 요청 42 · 검사 107 · 실패 0 · 종료 코드 0
  - 이때 만든 팁 번호는 41이다. 서버를 다시 켜서 시퀀스가 32개 건너뛴 것으로, docs/data-model.md 'ID 생성'의 설명과 같다.
- Codex 반박(codex exec --sandbox read-only): 패키지 이동 때문에 생긴 동작 결함은 없다고 확인했다.

| # | 심각도 | 지적 | 판정 | 처리 |
| --- | --- | --- | --- | --- |
| 1 | 중간 | `git mv`로 이동만 staged이고 package·import 변경은 unstaged라, staged만 커밋하면 폴더만 옮겨지고 패키지는 그대로 | 수용 | 커밋 전에 `git add -A`로 전부 stage하고 `git diff --cached --stat`으로 확인한다(사람이 커밋) |
| 2 | 낮음 | TextRules를 public으로 연 것이 지금 쓰는 범위(service)보다 넓음 | 기각 | 입력 글자 규칙(공백 제거·코드 포인트 길이)은 특정 서비스가 아닌 공통 규칙이라 common에 두는 것이 역할 구분에 맞다. 동작·보안 영향 없음(Codex도 결함 근거 없음) |
