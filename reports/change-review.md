# 변경 정리와 인계 · ICN Subway Steps 서버

문서 정보: 2026-10-20 · 작성 Claude Code · 기준 커밋 146b091(7장 시작 프로젝트 복사) 이후 전체

## 바뀐 파일과 이유

| 파일 | 이유 |
| --- | --- |
| settings.gradle, CLAUDE.md, README.md, 패키지 lostfound → icnsubway | 분실물 예제를 내 앱 프로젝트로 바꿈 |
| docs/ui-*.csv, screen-flow.csv, S02·S03-screen-spec.md, ui-handoff.md, ui-validation-decisions.md | 5장 입력 복사(원본 C:\dev\chapter05-ai\docs, 수정 안 함) |
| docs/requirements.md | 기능 요구 R01~R06·R08, 기술 조건·시드를 내 앱 기준으로 |
| docs/api-spec.yaml, docs/data-model.md | P01: API 계약, 테이블·시드·검토 메모 |
| docs/auth-design.md, docs/verification-plan.md | P02: 인증 설계, 검사 계획 |
| build.gradle | data-jpa, security, h2console, H2 2.4.240, JJWT 0.13.0 추가 |
| src/main/resources/application*.properties | H2 파일 DB, JSON 엄격 읽기·UTF-8, JWT 설정, dev만 H2 콘솔 |
| src/main/java/.../AppUser·Route·RouteStep·Tip, *Repository | Entity와 조회 |
| RouteService·RouteController | 경로·단계 공개 조회 |
| TipService·TipController | 팁 조회(mine)·등록·상태 변경(올린 사람만)·내 팁 |
| AuthService·AuthController, JwtService, JwtAuthenticationFilter, SecurityConfig, AuthUser | 가입·로그인·JWT·접근 규칙 |
| ApiError·ApiException·ApiAdvice, TextRules, Requests·Responses | 오류 형식, 입력 규칙, DTO |
| SeedData | 시안 v03 ROUTES 문구(초안)·표본 계정·팁 7·8 |
| src/test/... (6개 클래스, 40개) | 공개 조회·인증·팁 등록·권한 회귀·dev 프로필 |
| reports/verification.md | 실제 테스트·HTTP·재시작 확인 결과 |

## Codex 반박 판정

1차는 기준 146b091 이후 전체(`/codex:adversarial-review --base 146b091`), 2차는 수정 커밋만(`--base a0b0bb5`).

| # | 지적 (Codex 심각도) | 판정 | 이유 | 조치 |
| --- | --- | --- | --- | --- |
| 1 | DB만 초기화하면 시퀀스가 103부터 다시 시작해, 예전 토큰이 같은 id의 새 계정으로 인증된다 (높음) | 수용 | 필터가 id 존재만 확인하는 것을 코드로 확인. data-model.md가 키를 남긴 DB 초기화를 안내하고 있었음 | 토큰의 username과 iat(계정 생성 이후 발급)를 DB 사용자와 대조. 회귀 테스트 `oldTokenDoesNotAuthenticateReassignedUserId` 추가, 검사를 빼면 실패하는 것 확인 |
| 2 | exp 없는 토큰을 허용한다(JJWT는 exp가 있을 때만 검사) (중간) | 수용 | verify가 exp를 요구하지 않음을 확인. 서버 발급 토큰엔 항상 있으나 auth-design의 만료 계약 위반 | exp·iat·username 없는 토큰 거절, 테스트 추가 |
| 3 | (2차) 같은 초 안에 같은 id·같은 이름으로 계정이 다시 만들어지면 예전 토큰이 통과한다 (높음) | 기각 | 재현 경로가 없다: 사용자 삭제 API가 없고, DB 초기화는 서버를 끄고 파일을 지운 뒤 다시 켜야 해 토큰 발급과 같은 초에 새 계정이 생길 수 없다. 서버 한 대·로컬 전용 범위 | 코드 변경 없음. 여러 서버가 JWT_SECRET을 공유하는 배포로 바뀌면 계정마다 난수 세대 값을 저장해 토큰에 넣는 방식으로 강화(아래 '남은 문제' 5) |
| 4 | (2차) iat·username 누락 토큰 회귀 테스트가 없다 | 수용 | 테스트 목록에 없었음 | badTokensAre401에 두 경우 추가 |

Codex가 동의한 부분: 상태 변경의 입력→404→소유자 비교→변경 순서와 권한 확인 전 값 불변, 유니코드 공백 제거·코드 포인트 길이·note 빈 값 null, 공개 GET 무효 토큰 401과 /api/auth/** 헤더 무시.
팀 규칙상 Codex 리뷰는 2회까지 받았다. 그 뒤의 변경은 테스트 2건 추가뿐이다.

## 남은 문제

1. **앱 모델과 서버 JSON 이름이 다르다 → 결정(2026-10-21, 사용자): 서버 계약을 유지하고 앱이 맞춘다.** Flutter 앱(C:\dev\projects\icn_subway_app)은 ui-handoff 후보대로 snake_case(step_no, owner_id, verified_at, is_example)와 `GET /api/tips/mine`을 가정했다. 서버는 camelCase와 `GET /api/me/tips`를 그대로 두고, Flutter 세션(icn-app)이 모델의 fromJson·toJson과 내 팁 주소를 docs/api-spec.yaml에 맞춘다(아래 '앱 연결').
2. 단계 문구·표지판·층수는 현장 확인 전 초안이다. 엘리베이터는 모두 null.
3. 회원가입은 API에만 있고 앱 화면에는 없다(계정 발급 방식 미정).
4. 오류 문구는 한국어 한 가지. 앱이 errors의 키로 자기 번역 문구를 보여야 한다.
5. 토큰-계정 대조는 username·발급 시각 기준이다. 여러 서버가 키를 공유하게 되면 계정별 난수 세대 값으로 강화.
6. CORS 미설정(Flutter 웹에서 부르면 막힘), 서버는 127.0.0.1에만 열림(실제 휴대폰 불가).
7. 교재 CLAUDE.md는 "서버는 사람이 켜고 끈다"고 하지만, 이번에는 상위 세션 지시로 Claude가 bootRun을 켜고 확인 뒤 껐다.

## 앱 연결 (Flutter 세션이 알아야 할 것)

- 기본 URL: PC `http://127.0.0.1:8097`, Android 에뮬레이터 `http://10.0.2.2:8097`.
- 로그인 `POST /api/auth/login` → `{accessToken, tokenType:"Bearer", expiresIn:1800, user:{id, username}}`. 이후 `Authorization: Bearer <accessToken>`.
  무효·만료 토큰은 공개 GET에서도 401이므로, 401이면 저장한 토큰을 버리고 다시 로그인(또는 익명 재요청).
- 응답 `Content-Type: application/json;charset=UTF-8`. 오류는 `{code, message, errors}`.
- 응답 필드(camelCase):
  - 경로: `code, name{en,ko}, summary{en,ko}, available, stepCount`
  - 단계: `stepNo, role(HERE|FIND), icon(PLANE|ESCALATOR|TRAIN), photoKey|null, sign{ko,en}, title{en,ko}, instructions{en[],ko[]}, elevator{en,ko}|null`
  - 팁: `id, route, stepNo, title, location, note|null, status(VERIFIED|OUTDATED), verifiedAt, createdAt(ISO UTC), ownerId, mine, example`
  - 팁 등록 본문: `route, stepNo, title, location, note` (다른 항목이 있으면 400)
- 지금 Flutter 모델과 다른 점: snake_case 키, 경로의 origin·sign 없음, 단계의 sign이 객체·actions 대신 instructions·photo_url/photo_credit 대신 photoKey·elevator_status 대신 elevator, verifiedAt이 날짜가 아닌 ISO 시각, is_example 대신 example, 내 팁 주소.
  자세한 표: 공유 노트 폴더의 server-api-for-flutter.md.

## 8장이 읽을 경로

| 무엇 | 경로 |
| --- | --- |
| API 명세 | docs/api-spec.yaml |
| 인증 설계 | docs/auth-design.md |
| 데이터 모델·시드 | docs/data-model.md |
| 검사 계획 | docs/verification-plan.md |
| 실행 기록 | reports/verification.md |
| 실행 주소 | http://127.0.0.1:8097 (`.\gradlew.bat bootRun`, H2 콘솔은 SPRING_PROFILES_ACTIVE=dev) |
| 테스트 | src/test/java/org/skilltransfer/icnsubway/ (`.\gradlew.bat test`) |

## 8장 변경 파일과 Postman 파일 경로

2026-10-21 · 교재 8장 P05 · 작성 Claude Code. 실행(서버 켜기·Newman)도 Claude Code가 했다(사용자 요청). 커밋은 하지 않았다.

### 바뀐 파일·이유·확인 방법

| 파일 | 바뀐 것 | 이유(요청문) | 확인 방법 |
| --- | --- | --- | --- |
| docs/permission-matrix.md (새 파일) | 요청별 로그인 없음·등록자 A·다른 사용자 B의 기대 응답, 표 밖의 경우(없는 번호, 무효 토큰, 입력 오류, 없는 경로, 허용하지 않는 메서드), 확인할 질문 | P00 | 칸마다 코드 파일:줄 근거, Newman 1·2회차, Codex 반박 |
| docs/state-rules.md (새 파일) | VERIFIED·OUTDATED 전이 표, 바뀌지 않는 값, 교재 예제와 다른 점, 확인할 질문 | P00 | 테스트 PermissionRegressionTest, Newman 4묶음 |
| src/test/java/org/skilltransfer/icnsubway/PermissionRegressionTest.java | sameStatusAgainKeepsOtherValuesAndRefreshesVerifiedAt 추가 | P02 요청 2(같은 상태 재요청 행) | `.\gradlew.bat test --offline --rerun` 64개 통과 |
| postman/my-api.collection.json (새 파일) | Collection v2.1, 6묶음 42요청·107검사 | P04 요청 2 | 정적 검사(경로가 명세에 있는지, 변수, 스크립트 문법, 비밀 값), Newman 2회차 실패 0 |
| postman/my-api.environment.json (새 파일) | baseUrl·userA·userB·passwordA·passwordB(비밀번호 비움) | P04 요청 2 | 정적 검사 |
| docs/verification-plan.md | '## 8장 Postman·Newman 검사 계획' 절 | P04 요청 2 | 컬렉션과 순서·기대값 대조 |
| reports/verification.md | '## 8장 Postman·Newman 검사' 절(P02, Newman 1·2회차, Codex 판정, F01 없음, 학생 직접 실행 미실행) | P02·실행·P05 | 값은 실제 실행 출력에서 옮김 |
| reports/my-api.junit.xml (새 파일) | Newman 2회차 JUnit 보고서 | 실행 | 토큰 모양 값·비밀번호 0건 |
| .gitignore | postman/local.environment.json 추가 | 교재 3.3 | 비밀번호를 넣는 내 PC 전용 환경을 Git에서 뺌 |
| prompts/ch08/P00~P05 (새 파일) | 교재 8장 요청문 사본 | 0단계 | 배포 원본과 같음 |

- 서버 구현(src/main)은 바꾸지 않았다. 그래서 docs/api-spec.yaml과 새로 어긋난 곳은 없다.
- Codex 반박 7건: 수용 6, 일부 수용 1, 기각 0(reports/verification.md '8장 Codex 반박 판정').
- 위 '앱 연결' 절의 'Flutter 모델과 다른 점'은 2026-10-21에 앱이 서버 계약(camelCase, /api/me/tips)에 맞춰 해소됐다(커밋 311c4bb의 결정).

### Postman 파일과 보고서 경로

| 무엇 | 경로 |
| --- | --- |
| 컬렉션 | postman/my-api.collection.json |
| 환경(공유용, 비밀번호 비움) | postman/my-api.environment.json |
| 내 PC 전용 환경(비밀번호, Git 제외) | postman/local.environment.json(만들지 않음. Newman은 --env-var로 실행) |
| Newman 보고서 | reports/my-api.junit.xml |
| Newman 도구 | C:\dev\chapter08-api\tools\newman\node_modules\.bin\newman.cmd(6.2.1) |

### 9~10장이 읽을 파일

- 교재가 지정한 다섯 개(앱 프로젝트 docs/api/로 복사): docs/api-spec.yaml, docs/data-model.md, docs/auth-design.md, reports/verification.md, reports/change-review.md
- 함께 읽으면 좋은 것: docs/api-reference.md(사람이 읽는 API 명세서), docs/permission-matrix.md, docs/state-rules.md, docs/api-examples/
- 앱 프로젝트: C:\dev\projects\icn_subway_app. 복사는 교재 10장 1단계에서 한다(아직 안 함).

### 비어 있는 칸과 미실행 항목

- 학생 직접 실행: 미실행(reports/verification.md '학생 직접 실행')
- H2 콘솔로 저장값 확인: 미확인(DB 접속·SQL은 학생 몫)
- Postman 앱 화면(Import·Runner): 미실행. Newman으로만 실행했다.
- 8장 실패 F01·재검증: 해당 없음(실패 0)

## 패키지 구조 정리 (2026-10-21)

- 요청: "diff 섞여도 괜찮으니까 지금 바로 패키지 구조 정리해줘". Claude Code가 옮기고 검사했다.
- 로직은 바꾸지 않았다. 새 구조는 README.md '패키지 구조' 절, 검사 결과는 reports/verification.md '패키지 구조 정리' 절에 있다.

| 바뀐 파일 | 바꾼 것 | 이유 |
| --- | --- | --- |
| src/main/java/org/skilltransfer/icnsubway/ 아래 클래스 27개 | controller·service·repository·domain·dto·security·config·common 8개 패키지로 이동(git mv), package·import 줄 수정 | 클래스 28개가 한 폴더에 있어 요청 경로(controller → service → repository → domain)를 따라 읽기 어려웠다 |
| common/TextRules.java | 클래스와 strip·length를 public으로 | 다른 패키지(service)에서 쓴다. Codex 지적 2(service로 옮겨 package-private)는 기각(verification.md 표) |
| src/test/java/org/skilltransfer/icnsubway/SeedAndSchemaTest.java | config.SeedData import 한 줄 | 테스트는 맨 위 패키지에 그대로 둔다 |
| README.md | '패키지 구조' 절 추가 | 폴더마다 무슨 역할인지 바로 보이게 |
| docs/data-model.md, docs/permission-matrix.md, docs/state-rules.md | SeedData 경로, 소스 줄 번호 25곳 | import가 늘어난 만큼 줄 번호가 밀렸다. 옮긴 뒤 33곳 모두 해당 줄을 가리키는지 다시 확인했다 |

- 진입점 StarterApplication.java는 맨 위 패키지에 그대로 두었다(바뀐 줄 없음).
- 커밋할 때: 지금은 `git mv`로 이동만 staged이고 package·import 변경은 unstaged다. 둘을 함께 stage해야 한다(Codex 지적 1). `git add -A`를 쓰면 아직 커밋하지 않은 API 명세서·8장 파일도 함께 들어간다.
