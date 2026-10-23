# ICN Subway Steps 서버

인천공항 T1 입국장 → 서울역행 공항철도 탑승까지 단계 안내와 단계별 팁을 주는 API 서버.
교재 7장 시작 프로젝트(분실물 API)를 복사해 경로·단계·팁 도메인으로 바꿨다. Spring Boot 4.1.1 · Java 21 · H2 파일 DB · JWT(HS256).

| 문서 | 내용 |
| --- | --- |
| docs/api-spec.yaml | API 계약(OpenAPI 3.0.3). 앱 연결의 기준 |
| docs/api-reference.md | 사람이 읽는 API 명세서(yaml 풀이, API별 상태 코드·예시 링크, 검토 필요 목록) |
| docs/api-examples/ | 자동 테스트(MockMvc)로 만든 예시 JSON. 실행 서버의 실제 응답이 아니다 |
| docs/samples/ | 실행 서버의 실제 응답 표본 자리(학생이 모은다, 아직 없음) |
| docs/data-model.md | 테이블·제약·시드·초기화 방법·검토 메모(맨 위 ER 그림) |
| docs/schema.sql | 엔티티로 만든 create 스크립트(참고용. 실행 DB의 표는 ddl-auto=update가 만들어 열 순서·UNIQUE 방식이 다르다. H2 콘솔에서 비교해 볼 것) |
| docs/functional-spec.md | 기능별 입력·처리·실패·DB, 화면 → API → DB 추적표 |
| docs/auth-design.md | 비밀번호·JWT·키 보관·401/403 |
| docs/requirements.md | 기능 요구(R01~R08)와 수업 기술 조건 |
| docs/permission-matrix.md / docs/state-rules.md | 8장 권한표(로그인 없음·등록자·다른 사용자) / 팁 상태 전이 규칙 |
| docs/verification-plan.md / reports/verification.md | 검사 계획 / 실제 결과 |
| postman/ | 8장 Postman 컬렉션·환경(비밀번호 비움) |
| reports/change-review.md | 바뀐 파일·Codex 반박 판정·남은 문제·인계 |
| docs/ui-*.csv, S02·S03-screen-spec.md, ui-handoff.md, ui-validation-decisions.md | 5장 입력(C:\dev\chapter05-ai\docs에서 복사, 수정하지 않음) |

## 패키지 구조

요청은 controller → service → repository → domain 순서로 흐른다(교재 6장의 요청 경로).
진입점 `StarterApplication`은 맨 위 패키지에 두어 아래 패키지를 모두 스캔한다.

```text
src/main/java/org/skilltransfer/icnsubway
├── StarterApplication.java   진입점
├── controller/   HTTP 요청을 받는 곳: AuthController, RouteController, TipController, HelloController
├── service/      규칙과 판단: AuthService, RouteService, TipService (입력 검사, 401·400·404·403 순서, 등록자 비교)
├── repository/   DB 조회·저장(Spring Data JPA): UserRepository, RouteRepository, RouteStepRepository, TipRepository
├── domain/       DB 표와 짝인 엔티티: AppUser, Route, RouteStep, Tip
├── dto/          요청·응답 JSON 모양: Requests, Responses
├── security/     로그인·JWT·접근 규칙: SecurityConfig, JwtService, JwtAuthenticationFilter, AuthUser
├── config/       설정·처음 자료: JsonInputConfig(JSON 자료형 엄격), SeedData(시드)
└── common/       공통: ApiAdvice·ApiError·ApiException(오류 응답 {code, message, errors}), TextRules(공백·글자 수 규칙)
```

테스트(src/test/java/org/skilltransfer/icnsubway)는 맨 위 패키지에 그대로 두고 필요한 클래스를 import한다.

## 실행

PowerShell에서:

    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
    $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    java -version
    .\gradlew.bat test
    .\gradlew.bat bootRun

- http://127.0.0.1:8097/api/health 에 status UP이 보이면 실행된 것이다. 종료는 그 창의 Ctrl+C.
- H2 콘솔이 필요하면 bootRun 전에 `$env:SPRING_PROFILES_ACTIVE='dev'`. 주소 http://127.0.0.1:8097/h2-console, JDBC URL `jdbc:h2:file:./data/icn-subway`, 사용자 sa, 비밀번호 없음.
- 처음 실행하면 data/icn-subway.mv.db(DB)와 data/.jwt-secret(JWT 키)이 생긴다. 둘 다 Git에서 빠진다.
  DB를 처음 상태로 돌리려면 서버를 끄고 data/icn-subway.mv.db를 지운다(시드가 다시 들어간다).
- 표본 계정 student01·student02, 비밀번호는 docs/requirements.md의 공개 표본 값(로컬 실습 전용).
- api-reference.md는 손으로 고친다. 고친 뒤 yaml·코드와 맞는지 확인: `.\gradlew.bat test --offline --rerun --tests '*ApiContractDocTest*'`
  - docs/만 고쳤을 때는 `--rerun`을 꼭 붙인다. Gradle은 docs/를 테스트 입력으로 보지 않아서, 붙이지 않으면 테스트를 하나도 돌리지 않고 BUILD SUCCESSFUL만 나온다.
- 평소 test에서 ApiExamplesGenerator가 docs/api-examples/를 지금 코드의 응답과 비교한다. 코드나 yaml을 바꿔 응답이 달라지면 이 테스트가 실패하므로 예시를 다시 만든다. 명령과 확인 순서(종료 코드가 0일 때만 복사)는 docs/api-examples/README.md '다시 만드는 명령'에 있다.

## 앱(Flutter)에서 연결

- 기본 주소: PC에서는 http://127.0.0.1:8097, Android 에뮬레이터에서는 http://10.0.2.2:8097. 서버가 127.0.0.1에만 열려 있어 실제 휴대폰은 접속할 수 없다.
- 로그인 응답의 accessToken을 `Authorization: Bearer <accessToken>` 헤더로 보낸다. 1800초 뒤 만료되면 401 → 다시 로그인.
- 응답 필드는 camelCase다. 자세한 내용은 docs/api-spec.yaml과 reports/change-review.md의 '앱 연결' 절.
