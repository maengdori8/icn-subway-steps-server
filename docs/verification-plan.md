# 검사 계획 · ICN Subway Steps 서버

문서 정보: 2026-10-20 · 실행 전 계획이다. 실제 결과는 reports/verification.md에 적는다.
계정: A = student01(101), B = student02(102), 새 계정은 테스트마다 다른 이름으로 가입한다.
"재조회"는 실패한 요청 뒤 GET /api/tips/{id}로 저장값(제목·위치·메모·상태·verifiedAt·ownerId)과 팁 개수가 그대로인지 보는 것이다.

## 공개 조회

| # | 조건·입력 | 계정 | 기대 응답 |
| --- | --- | --- | --- |
| V01 | GET /api/routes | 없음 | 200, EXPRESS·LOCAL(available, 5단계), T2(available=false, 0단계) |
| V02 | GET /api/routes/EXPRESS/steps | 없음 | 200, 5단계, 1단계 role=HERE·photoKey=arrivals·sign ko/en, elevator=null |
| V03 | GET /api/routes/T2/steps | 없음 | 200, steps=[] |
| V04 | GET /api/routes/NOPE/steps, /api/routes/express/steps | 없음 | 404 NOT_FOUND |
| V05 | GET /api/tips?route=LOCAL&step=4 | 없음 | 200, 표본 팁 8 포함, mine=false |
| V06 | 같은 요청 | A 토큰 | 200, 팁 8 mine=true |
| V07 | route 없음·T2·소문자, step 없음·0·6·숫자 아님 | 없음 | 400 VALIDATION_FAILED, errors에 해당 항목 |
| V08 | GET /api/tips/8, /api/tips/999999 | 없음 | 200 / 404 |
| V09 | 공개 GET에 무효·만료 토큰 | — | 401 UNAUTHORIZED |

## 회원가입·로그인·토큰

| # | 조건·입력 | 기대 응답 |
| --- | --- | --- |
| V10 | 새 username 가입 | 201, {id, username}, 응답에 password·hash 없음, id ≥ 103 |
| V11 | 같은 username 다시 | 409 USERNAME_TAKEN |
| V12 | username 대문자·3자·25자·기호 / password 7자·73바이트(한글 25자) | 400, errors.username / errors.password |
| V13 | 가입 본문에 정하지 않은 항목 | 400 INVALID_REQUEST_BODY |
| V14 | student01 / 표본 비밀번호 로그인 | 200, accessToken·tokenType=Bearer·expiresIn=1800·user{id:101} |
| V15 | 틀린 비밀번호, 없는 아이디 | 401 LOGIN_FAILED(같은 문구) |
| V16 | 빈 아이디·비밀번호 | 400 VALIDATION_FAILED |
| V17 | 오래된 토큰을 붙인 로그인 | 200 (auth는 헤더를 보지 않음) |
| V18 | 보호된 API에 토큰 없음 / Bearer 아님 / 변조 / 다른 키 / 만료 / exp 없음 / 발급자 다름 / 이름 불일치 / 계정 생성 전 발급 | 401 UNAUTHORIZED |
| V19 | 가입 → 토큰 → 같은 id를 다른 계정에 재배정(DB 초기화 흉내) → 예전 토큰 | 401 |

## 팁 등록

| # | 조건·입력 | 계정 | 기대 응답 |
| --- | --- | --- | --- |
| V20 | 정상 등록(앞뒤 공백 포함 제목, 빈 메모) | A | 201, Location /api/tips/{id}, status=VERIFIED, verifiedAt 있음, mine=true, ownerId=101, 제목 공백 제거, note=null. Location 재조회 200 |
| V21 | 제목·위치 80자 / 81자 | A | 201 / 400 errors.title·location "80자 이하로 입력하세요." |
| V22 | 이모지 80개 / 81개(코드 포인트 기준) | A | 201 / 400 |
| V23 | 빈 제목·공백만 | A | 400 "1~80자로 입력하세요." |
| V24 | 메모 150자 / 151자 | A | 201 / 400 "150자 이하로 입력하세요." |
| V25 | 본문에 status·ownerId·verifiedAt·id | A | 400 INVALID_REQUEST_BODY, errors에 그 항목 |
| V26 | 깨진 JSON, 본문 없음 | A | 400 INVALID_REQUEST_BODY |
| V27 | route=T2·없는 값, stepNo 0·6 | A | 400 |
| V28 | 토큰 없음 | — | 401, 팁 개수 그대로 |

## 상태 변경·내 팁·권한

| # | 조건·입력 | 계정 | 기대 응답 |
| --- | --- | --- | --- |
| V30 | 내 팁 OUTDATED로 | A | 200, status=OUTDATED, verifiedAt 갱신, 다른 필드 그대로 |
| V31 | 같은 상태 다시 | A | 200 |
| V32 | status 없음·DELETED·정하지 않은 항목 | A | 400, 재조회 그대로 |
| V33 | A의 팁을 B가 변경 (VERIFIED·OUTDATED 각각) | B | 403 FORBIDDEN, 재조회 그대로 |
| V34 | 없는 팁 번호 | A | 404 |
| V35 | 토큰 없음·만료 | — | 401, 재조회 그대로 |
| V36 | GET /api/me/tips | A | 200, ownerId가 모두 101, mine=true, 내림차순 |
| V37 | GET /api/me/tips 토큰 없음 | — | 401 |

## 실행 서버 확인(MockMvc 밖)

| # | 조건 | 기대 |
| --- | --- | --- |
| V40 | bootRun 후 GET /api/health | 200 status UP |
| V41 | 로그인 → 팁 등록 → 상태 변경 → 내 팁 (Invoke-WebRequest, 한글 UTF-8) | 200·201·200·200 |
| V42 | 재시작 후 같은 토큰으로 내 팁 | 200 (키 재사용), 시드 중복 없음 |
| V43 | dev 프로필에서 /h2-console/ | 200. 기본 프로필에서는 열리지 않음 |

## 8장 Postman·Newman 검사 계획

교재 8장 P04로 만든 계획이다. 실제 결과는 reports/verification.md의 '8장 Postman·Newman 검사' 절에 적는다.
기준 문서는 docs/api-spec.yaml, docs/permission-matrix.md, docs/state-rules.md다.

### 파일

| 파일 | 내용 |
| --- | --- |
| postman/my-api.collection.json | Collection v2.1. 6묶음 42요청. 토큰·사용자 번호·새 팁 번호·비교값은 컬렉션 변수(tokenA, tokenB, userIdA, userIdB, tipId, snapshot, runTag, countBefore) |
| postman/my-api.environment.json | baseUrl, userA(student01), userB(student02), passwordA·passwordB(값은 비움)만 둔다 |
| postman/local.environment.json | 비밀번호를 넣는 내 PC 전용 사본. .gitignore로 Git에서 뺀다(만들지 않고 Newman의 --env-var로 넘겨도 된다) |

### 교재 예제와 다른 점

- 교재의 itemId는 이 앱에서 tipId다. '반환'은 상태 바꾸기(PATCH /api/tips/{id}/status)다.
- 이 API에는 수정(PUT)과 삭제(DELETE)가 없다. 거절 시험은 상태 바꾸기로 한다. 지금 저장값(VERIFIED)과 다른 값(OUTDATED)을 보낸다.
- 정리(삭제) 단계가 없다. 검사로 만든 팁은 남는다. 제목이 '[Postman] 8장 검사'로 시작하고, 상태는 마지막에 VERIFIED로 되돌린다.
  - 지우려면 서버를 끄고 data/icn-subway.mv.db를 지운다. 다음 실행 때 시드부터 다시 만들어지며, 그동안 넣은 다른 자료도 함께 사라진다(docs/data-model.md '보존과 초기화').
- 거절 뒤 비교에는 mine을 뺀다. mine은 토큰에 따라 계산되는 값이기 때문이다. 비교하는 값은 id, route, stepNo, title, location, note, status, verifiedAt, createdAt, ownerId, example 11개이고, 이 목록은 고정해서 쓴다(응답에서 값이 빠지면 저장 단계에서 실패).
- 삭제 시도(DELETE)는 정리 단계가 아니라 거절 시험이다. 삭제 API가 없다는 것(401·405)과 시도 뒤에도 팁이 그대로인지를 확인한다.
- 상태를 바꾸는 요청 앞에는 30ms를 기다린다. 서버가 시각을 밀리초까지 저장하므로, '다시 기록됐다'를 '이전 값보다 뒤'로 엄격하게 확인하기 위해서다.
- Postman 앱에서 실행한 뒤 컬렉션이나 환경을 다시 내보내면 저장된 토큰·비밀번호가 함께 나갈 수 있다. 공유·커밋은 이 원본 파일로 한다.

### 요청 순서와 기대

| 묶음 | 요청 | 계정 | 기대 |
| --- | --- | --- | --- |
| 1 공개 목록과 로그인 | GET /api/routes, GET /api/routes/LOCAL/steps | 없음 | 200. EXPRESS·LOCAL 안내 중, LOCAL은 2단계 이상 |
| | POST /api/auth/login (A, B) | 없음 | 200, tokenType Bearer. tokenA·tokenB·userIdA·userIdB 저장, A와 B는 다른 사용자 |
| 2 A의 새 팁 등록 | POST /api/tips | A | 201, Location /api/tips/{id}. 보낸 route·stepNo·title·location·note가 그대로, ownerId = A, VERIFIED, example false, mine true, verifiedAt = createdAt. 11개 값의 존재·자료형을 확인한 뒤 tipId·snapshot 저장 |
| | GET /api/tips/{tipId} | 없음 | 200, snapshot과 같음, mine false |
| 3 거절과 재조회 | GET /api/tips?route=LOCAL&step=2 | 없음 | 200, 개수를 countBefore에 기록 |
| | POST /api/tips 토큰 없음 / 제목 공백만 / 제목 81자 / 본문에 ownerId | 없음, A | 401 UNAUTHORIZED / 400 VALIDATION_FAILED(title) / 400 VALIDATION_FAILED(title) / 400 INVALID_REQUEST_BODY(ownerId) |
| | GET /api/tips?route=LOCAL&step=2 | 없음 | 개수가 countBefore와 같음(거절된 등록은 팁을 남기지 않음) |
| | PATCH 상태 OUTDATED: 토큰 없음 / 무효 토큰 / B | 없음, 무효, B | 401 / 401 / 403 FORBIDDEN. 거절할 때마다 GET으로 다시 읽어 11개 값이 snapshot과 같음 |
| | GET /api/routes 무효 토큰 | 무효 | 401(헤더가 있으면 공개 GET도 검사) |
| | PATCH 잘못된 status: B가 DELETED / A가 소문자 / A가 {} / A가 status+title | B, A | 400 VALIDATION_FAILED(status) ×3 / 400 INVALID_REQUEST_BODY(title). B도 403보다 400이 먼저 |
| | PATCH /api/tips/999999999/status | A, 없음 | 404 NOT_FOUND / 401(인증이 먼저) |
| | DELETE /api/tips/{tipId} | 없음, A | 401 / 405 METHOD_NOT_ALLOWED. 다시 읽어 snapshot과 같음 |
| | GET /api/tipz(없는 경로) | 없음, A | 401 / 404 NOT_FOUND |
| 4 상태 변경과 반복 | PATCH OUTDATED | A | 200, 같은 id, OUTDATED, 다른 값 그대로, verifiedAt이 이전 값보다 뒤. snapshot 갱신 |
| | PATCH OUTDATED 다시 | A | 200, OUTDATED 유지, 다른 값 그대로, verifiedAt만 이전 값보다 뒤로 다시 기록. GET으로 다시 읽어 같음 |
| | PATCH VERIFIED | A | 200, VERIFIED로 되돌림, verifiedAt이 이전 값보다 뒤 |
| 5 내 팁과 목록 | GET /api/me/tips | A, B, 없음 | A: 새 팁 있음, 모든 항목이 ownerId = A·mine true / B: 새 팁 없음, 모든 항목이 ownerId = B·mine true / 없음: 401 |
| | GET /api/tips?route=LOCAL&step=2 | A, B | 새 팁의 mine이 A면 true, B면 false |
| 6 계정 | POST /api/auth/signup (userA) | 없음 | 409 USERNAME_TAKEN(A가 이미 로그인된 뒤라 새 계정이 생기지 않음) |
| | POST /api/auth/login 틀린 비밀번호 / 빈 칸 | 없음 | 401 LOGIN_FAILED / 400 VALIDATION_FAILED |

### 실행 명령

서버를 켠다(창 1, 내 서버 프로젝트 폴더).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

Newman을 실행한다(창 2, 내 서버 프로젝트 폴더). 비밀번호는 local.environment.json에만 넣거나, 아래처럼 실행할 때만 넘긴다.

```powershell
$taskNewman = 'C:\dev\chapter08-api\tools\newman\node_modules\.bin\newman.cmd'
& $taskNewman run .\postman\my-api.collection.json `
  -e .\postman\my-api.environment.json `
  --env-var "passwordA=<표본 비밀번호>" --env-var "passwordB=<표본 비밀번호>" `
  --reporters cli,junit `
  --reporter-junit-export .\reports\my-api.junit.xml
$LASTEXITCODE
```

- 표본 비밀번호는 docs/requirements.md '표본 자료'에 있다. 이 문서와 Postman 파일에는 적지 않는다.
- $LASTEXITCODE가 0이면 모든 검사가 통과한 것이다. 1이면 출력 끝의 실패 목록에서 처음 실패한 요청부터 읽는다.
