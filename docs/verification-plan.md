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
