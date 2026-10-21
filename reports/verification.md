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

## 확인하지 못한 것

- Android 에뮬레이터(10.0.2.2:8097)와 실제 Flutter 앱에서의 연결. 서버는 127.0.0.1에만 열려 있어 실제 휴대폰은 접속할 수 없다.
- Flutter 웹(브라우저)에서 부를 때 필요한 CORS. 설정하지 않았다.
- 1800초 실제 만료(테스트는 과거 시각으로 만든 토큰으로 대신 확인).
- 단계 문구·표지판·층수의 사실 여부(현장 확인 전 초안).
- 실행용 data/ DB의 실제 표가 docs/schema.sql과 같은지(규칙상 학생이 H2 콘솔에서 직접 비교). docs/data-model.md의 mermaid 그림이 실제로 그려지는지.
