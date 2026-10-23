# 기능 명세서 · ICN Subway Steps

문서 정보: 2026-10-21 · 작성 Claude Code · 검토: (본인 이름)

근거 문서:
- 이 폴더: requirements.md(기능 요구 R01~R08), api-spec.yaml(API 계약), data-model.md(표·열), auth-design.md
- 디자인: 5장 시안 v03(C:\dev\chapter05-ai\docs\screens\ICN-subway-steps-v03.src.html)
- 앱: 9장 Flutter 앱의 docs/handoff.md(C:\dev\projects\icn_subway_app)

requirements.md가 '무엇을 확인할지' 한 줄씩 적는다면, 이 문서는 기능마다 다음을 이어서 적는다.
- 누가 쓰는지
- 무엇을 넣고 받는지
- 서버가 무엇을 하는지
- 실패하면 화면이 어떻게 되는지
- DB의 어느 표·열을 쓰는지

API의 필드 이름과 상태 코드는 api-spec.yaml이 기준이다. 둘이 다르면 api-spec.yaml을 따르고 이 문서를 고친다.

## 1. 기능 목록

| ID | 기능 | 요구 | 화면 | 사용자 | 서버 API | 쓰는 표 |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | 경로 목록 보기 | R01 | S01 | 누구나 | GET /api/routes | route, route_step(개수) |
| F02 | 단계 안내 보기 | R02 | S02 | 누구나 | GET /api/routes/{route}/steps | route, route_step |
| F03 | 진행 이어 보기 | R02 | S01·S02 | 누구나 | 없음(앱 기기 저장) | 없음 |
| F04 | 단계 팁 보기 | R06 | S02 | 누구나(로그인하면 '내 팁' 표시) | GET /api/tips?route=&step= | tip, app_user(토큰) |
| F05 | 팁 올리기 | R04 | S03 | 로그인한 사용자 | POST /api/tips | tip, route, route_step |
| F06 | 로그인 | R05 | S04 | 계정이 있는 사람 | POST /api/auth/login | app_user |
| F07 | 팁 상태 바꾸기 | R06 | S02·S06 | 그 팁을 올린 사람 | PATCH /api/tips/{id}/status | tip |
| F08 | 내 팁 보기 | R08 | S06 | 로그인한 사용자 | GET /api/me/tips | tip |
| F09 | 설정(테마·언어·계정·사진 출처) | R03·R07 | S05 | 누구나 | 없음(로그아웃은 앱이 토큰을 버림) | 없음 |
| F10 | 회원가입 | (화면 없음) | 없음 | — | POST /api/auth/signup | app_user |

- F10은 API만 있고 앱 화면은 없다. 여행객이 계정을 어떻게 얻을지는 아직 정하지 않았다(9절 미정 1).
- 보조 API: GET /api/tips/{id}(팁 하나). POST의 Location이 가리키는 주소이며 앱 화면은 쓰지 않는다.
- 앱 아래쪽 메뉴(경로·내 팁·설정)는 S01·S06·S05에만 있다. S02~S04에는 없다.

## 2. 기능별 상세

### F01 경로 목록 보기 (S01)

- **시작:** 앱을 열면 첫 화면이다. 로그인은 필요 없다.
- **요청:** GET /api/routes. 본문과 토큰이 없다.
- **서버 처리:** route 표를 sort_order 순서로 읽는다. 경로마다 route_step 개수를 세어 stepCount로 준다.
- **응답:** {routes:[{code, name{en,ko}, summary{en,ko}, available, stepCount}]}
- **화면:**
  - 카드 하나에 경로 이름, 보조 문구, 단계 수를 보인다.
  - available=false(T2)인 경로는 '준비 중' 카드다. 누르면 안내 문구만 보이고 S02로 가지 않는다.
  - 출발 터미널과 표지판 병기 줄은 서버가 주지 않는다. 앱이 경로 코드별 상수(routeDisplays)로 정한다.
- **실패:**

  | 경우 | 화면 |
  | --- | --- |
  | 연결 실패 | '인터넷에 연결되지 않았습니다' 안내와 다시 시도 |
  | 그 밖의 실패 | '불러오지 못했어요'와 다시 시도 |

  앱은 경로 목록을 요청할 때 토큰을 붙이지 않으므로 401이 생기지 않는다. 서버의 '무효 토큰을 붙인 공개 GET은 401' 규칙은 그대로다.

- **DB:** route(code, name_en, name_ko, summary_en, summary_ko, available, sort_order), route_step(route_code 개수)

### F02 단계 안내 보기 (S02)

- **시작:** S01에서 안내 중인 경로(EXPRESS·LOCAL)를 누른다.
- **요청:** GET /api/routes/{route}/steps. 경로 코드는 대소문자를 구분한다.
- **서버 처리:**
  - 그 경로의 route_step을 step_no 순서로 준다.
  - 준비 중 경로는 200과 빈 steps를 준다. 서버가 available을 보고 막는 것이 아니라 T2에 route_step 행이 없어서다(T2를 열 때 행을 넣고 available을 true로 바꾼다). 없는 코드는 404다.
  - instructions는 DB에 줄바꿈으로 이어 저장한 값을 배열로 나눠서 준다.
  - elevator는 elevator_en·elevator_ko가 둘 다 있을 때만 {en, ko}로 준다. 한쪽만 채운 행은 null로 준다. 한쪽이 null인 객체를 주면 앱이 응답을 읽지 못해 그 경로의 S02 전체가 '불러오지 못했어요'가 되기 때문이다.
- **응답:** {route, steps:[{stepNo, role, icon, photoKey, sign{ko,en}, title{en,ko}, instructions{en[],ko[]}, elevator}]}
- **화면:**
  - 'n/N' 진행 표시, 4:3 사진, 역할 라벨이 붙은 표지판 칩(HERE=현재 위치, FIND=찾을 표지판), 제목, 행동 안내 줄을 보인다.
  - 이전·다음 버튼은 하단에 고정된다. 글자가 2배 이상이면 본문 끝으로 옮긴다.
  - 마지막 단계의 버튼은 '승강장에 왔어요'다. 누르면 완료 화면이 나온다.
  - 다른 이동 방법도 있다. 사진을 좌우로 밀면 이전·다음 단계로 간다. 전체 단계 목록을 펼쳐 원하는 단계로 바로 갈 수도 있다.
  - 완료 화면에는 승차권 보관 안내 한 줄, 'N단계 다시 보기', '경로 목록으로'가 있다.
  - 사진:
    - photoKey가 null이면 '사진 준비 중'을 보인다.
    - 사진 파일과 출처는 앱 안 표(photoKey → 파일·출처)에서 찾는다. 서버는 사진 파일을 주지 않는다.
    - 사진 키는 arrivals, transportCenter, expressConcourse, expressGate, expressPlatform, localConcourse, localGate, localPlatform 여덟 개다.
  - 엘리베이터: elevator가 null이면 '엘리베이터: 현장 확인 중'을 보인다(두 언어 중 한쪽만 채운 단계도 같다).
  - 안내 문구 속 `{ko|공항철도}`는 다른 언어로 읽을 부분이다. 앱은 '|' 뒤 글자만 보이고, 그 조각에 언어 표시를 단다.
- **실패:** 화면 전체에 안내와 다시 시도를 보인다. 404(없는 경로 코드)도 지금은 같은 안내다. 앱은 S01 목록에 있는 코드만 보내므로 정상 사용에서는 생기지 않는다.
- **DB:** route_step(route_code, step_no, role, icon, photo_key, sign_ko, sign_en, title_en, title_ko, instructions_en, instructions_ko, elevator_en, elevator_ko)

### F03 진행 이어 보기 (S01·S02, 앱만)

- **저장:** 경로별로 마지막에 본 화면 위치를 기기에 저장한다(shared_preferences 키 `progress.<경로 코드>`). 서버에는 보내지 않는다.
  - 값은 0부터 센다. 0은 첫 단계, N−1은 마지막 단계, N(단계 수)은 완료 화면이다.
  - API의 stepNo(1부터)와 한 칸 다르다. 저장값 i는 stepNo i+1이다.
- **열기:** 저장된 진행이 없으면 S02는 1단계부터, 있으면 저장된 위치부터 연다.
- **S01 화면:** 진행 중인 경로 카드에 'n/N'과 '이어 보기'를 보이고, 처음부터 다시 볼 수 있다. 다 본 경로는 완료로 표시한다.
- **DB:** 없음.
- **주의:** 서버의 단계 수가 바뀌면 저장한 번호가 범위를 넘을 수 있다. 앱은 읽을 때 0~단계 수 안으로 줄인다.

### F04 단계 팁 보기 (S02)

- **시작:** S02에서 단계를 보면 그 단계의 팁 목록을 읽는다.
- **요청:** GET /api/tips?route={EXPRESS|LOCAL}&step={1~단계 수}. 로그인했으면 Bearer 토큰을 붙인다.
- **서버 처리:**
  - route가 안내 중인 경로인지, step이 그 경로의 범위 안인지 확인한다. 아니면 400이다.
  - tip을 id 내림차순으로 준다.
  - 토큰이 있으면 owner_id와 토큰의 사용자를 비교해 mine을 계산한다. 토큰이 없으면 mine은 모두 false다.
- **응답:** {tips:[Tip]}. Tip은 id, route, stepNo, title, location, note, status, verifiedAt, createdAt, ownerId, mine, example이다.
- **화면:**
  - 배지:
    - VERIFIED는 '작성자 확인 · 날짜'로 보인다. 날짜는 verifiedAt을 서울 날짜로 바꾼 것이다.
    - OUTDATED는 '정보 오래됨 · 날짜'다.
    - example=true면 'Example' 배지가 상태 배지를 대신하고, 작성자를 '샘플'로 보인다.
  - 작성자:
    - mine=true면 '내가 올림' 표시와 상태 바꾸기 버튼을 보인다. 예시 팁에는 버튼을 숨긴다.
    - 다른 사람의 팁은 이름 대신 '여행객'으로 보인다. 그래서 API에 작성자 이름 필드가 없어도 된다.
  - 순서와 묶음:
    - VERIFIED 묶음을 먼저 보이고, OUTDATED 묶음은 그 뒤에 접어 둔다.
    - 묶음 안에서는 서버 순서(id 내림차순, 새 팁 먼저)를 그대로 쓴다.
    - '오래됨'은 작성자가 상태를 바꿔야만 된다. 날짜가 지나도 저절로 바뀌지 않는다.
  - 팁 영역 상태 여섯 가지: 불러오는 중, 없음, 못 불러옴, 바꾸는 중, 못 바꿈, 권한 없음.
- **DB:** tip(route_code, step_no 인덱스, 나머지 열 전부), mine은 열이 아니라 응답 때 계산한다.

### F05 팁 올리기 (S03)

- **시작:** S02의 한 단계에서 '+ 팁'을 누른다. 로그인 전이면 S04로 갔다가 로그인한 뒤 S03으로 돌아온다(F06).
- **입력:**

  | 칸 | 필수 | 규칙(title·location·note는 앞뒤 공백 제거 뒤 코드 포인트로 셈. route는 그대로 비교) | 정하는 쪽 |
  | --- | --- | --- | --- |
  | route | 예 | EXPRESS·LOCAL | 앱(S02에서 넘어옴, 사용자가 고치지 않음) |
  | stepNo | 예 | 1~그 경로의 단계 수 | 앱(S02에서 넘어옴) |
  | title | 예 | 1~80자 | 사용자 |
  | location | 예 | 1~80자 | 사용자 |
  | note | 아니요 | 0~150자, 빈 값은 null | 사용자 |

  - 다른 항목(status, ownerId, verifiedAt, id 등)을 보내면 서버는 400 INVALID_REQUEST_BODY로 거절한다.
  - 자료형도 계약대로만 받는다. stepNo는 JSON 정수, title·location·note는 문자열(note는 null도 됨)이다. stepNo 3.7이나 "3", 숫자·불리언 title을 보내면 바꿔 저장하지 않고 400 INVALID_REQUEST_BODY(errors.{항목} '형식이 맞지 않습니다.')다.
  - '앞뒤 공백'은 유니코드 White_Space 문자와 U+FEFF다(앱의 Dart trim()과 같은 범위, 자세한 목록은 data-model.md tip 절).
- **서버 처리:**
  1. 토큰을 확인한다.
  2. 입력 규칙을 검사한다.
  3. 저장할 때 서버가 정하는 값: owner_id = 토큰의 사용자, status = VERIFIED, verified_at = created_at = 지금, example = false.
  4. 201과 Location: /api/tips/{id}를 준다.
- **화면:**
  - 올리는 중에는 입력칸을 읽기 전용으로 두고 버튼을 막는다.
  - 성공하면 그 단계로 돌아가 새 팁으로 스크롤하고 알림을 보인다.
  - 쓰다가 나가려 하면 '버리기 / 계속 쓰기' 확인창이 뜬다. 초점과 Esc는 '계속 쓰기'로 간다.
  - 글자 수 표시는 서버와 같은 코드 포인트 기준이다.
- **실패:**

  | 응답 | 화면 |
  | --- | --- |
  | (보내기 전) 앱 검사 실패 | 서버와 같은 규칙으로 앱이 먼저 칸별 검사를 한다. 칸 아래 오류를 보이고 첫 오류 칸으로 초점을 옮긴다 |
  | 400 VALIDATION_FAILED | errors의 키(title·location·note)에 맞는 칸 아래 오류를 보이고 첫 오류 칸으로 초점. 문구는 앱 번역 문구 |
  | 401 | '로그인이 끝났어요' 안내. 입력값은 그대로 둔다 |
  | 연결·그 밖 | '올리지 못했어요'와 다시 시도. 입력값은 그대로 둔다 |

- **DB:** tip에 행을 추가한다. route·route_step은 범위 검사에만 쓴다. tip_seq(시작 9)로 id를 만든다.

### F06 로그인 (S04)

- **시작:** 로그인 없이 '+ 팁'을 누르거나, S05 설정이나 S06 내 팁의 로그인 안내를 누른다. 어디서 왔는지에 따라 제목과 안내가 다르다.
- **입력:** username, password. 비밀번호 칸 안 오른쪽 끝에 보이기·숨기기 눈 아이콘이 있다.
- **요청:** POST /api/auth/login {username, password}. Authorization 헤더는 보지 않는다.
- **서버 처리:** app_user.username으로 찾고 BCrypt로 비교한다. 성공하면 JWT(HS256, 1800초)를 발급한다.
- **응답:** {accessToken, tokenType:"Bearer", expiresIn, user{id, username}}
- **화면:**
  - 성공하면 온 곳으로 돌아간다(S03, S05 또는 S06).
  - 토큰은 앱 메모리에만 두고 기기에 저장하지 않는다. 앱을 끄면 로그아웃된다. 기기에 남길지는 10장에서 정한다.
  - 사용자 이름은 S05에 '…(으)로 로그인됨'으로 보인다.
- **실패:**

  | 경우 | 화면 |
  | --- | --- |
  | (보내기 전) 빈 칸 | 앱이 칸별로 검사해 그 칸 아래 오류를 보인다 |
  | 401 LOGIN_FAILED | 아이디가 맞지 않는다는 공통 안내 한 줄. 아이디는 남기고 비밀번호는 비운다 |
  | 서버 400·연결·그 밖 | 로그인 오류 안내(공통) |

- **DB:** app_user(username UNIQUE, password_hash), 읽기만 한다.

### F07 팁 상태 바꾸기 (S02·S06)

- **시작:** 내 팁(mine=true, 예시 팁 아님)의 상태 버튼을 누른다.
- **요청:** PATCH /api/tips/{id}/status {status: VERIFIED|OUTDATED} + Bearer 토큰
- **서버 처리:**
  - 판단 순서는 401(토큰) → 400(경로 id 형식) → 415(JSON이 아닌 본문) → 400(본문·status) → 404(없는 팁) → 403(올린 사람 아님)이다. 권한을 확인하기 전에는 아무것도 바꾸지 않는다(전체 규칙은 api-spec.yaml 공통 규칙).
  - status를 바꾸고 verified_at을 지금으로 바꾼다. 같은 상태를 다시 보내도 200이고 verified_at만 바뀐다(작성자 재확인).
  - title·location·note·owner_id·created_at은 바뀌지 않는다.
- **화면:**
  - 바꾸는 중에는 그 팁 카드에 진행 표시를 보인다.
  - 성공하면 배지와 날짜가 바뀐다. '오래됨'으로 바꾸면 지난 팁 묶음을 펼쳐서 그 카드가 계속 보인다.
- **실패:**

  | 응답 | 화면 |
  | --- | --- |
  | 401 | 로그인이 끝난 것으로 보고 로그아웃한다. S02는 팁을 익명으로 다시 읽고, S06은 로그인 안내로 바뀐다. 팁 버튼은 막지 않는다 |
  | 403 | 그 팁 카드 안에 '지금은 바꿀 수 없어요. 다시 로그인한 뒤 시도하세요'와 [다시 로그인]. 그 팁의 버튼은 막는다 |
  | 그 밖 | '상태를 바꾸지 못했어요'와 다시 시도 |

  - '그 밖' 알림은 단계를 옮기면 지운다.
  - 403으로 막은 팁은 세션이 바뀔 때까지 막혀 있다. 단계를 옮겼다가 돌아와도 안내가 다시 보인다.
- **DB:** tip(status, verified_at)을 고친다. owner_id로 권한을 확인한다.

### F08 내 팁 보기 (S06)

- **시작:** 하단 메뉴 '내 팁'을 누른다. 로그인 전이면 로그인 안내와 [로그인] 버튼을 보인다.
- **요청:** GET /api/me/tips + Bearer 토큰
- **서버 처리:** owner_id가 토큰의 사용자인 tip을 id 내림차순으로 준다. 예시 팁도 내 것이면 포함한다.
- **화면:**
  - 팁마다 경로·단계, 배지, 상태 바꾸기 버튼을 보인다(F07과 같은 규칙).
  - '이 단계 보기'를 누르면 그 경로·단계의 S02로 간다. 오래된 팁이면 지난 팁 묶음을 펼친 채로 연다.
  - 앱은 예시 팁을 목록에서 뺀다.
  - 팁이 없으면 빈 상태 안내를 보인다.
- **실패:**

  | 응답 | 화면 |
  | --- | --- |
  | 401 | 로그아웃한 뒤 로그인 안내로 바뀐다 |
  | 그 밖 | 안내와 다시 시도 |

- **DB:** tip(owner_id 인덱스)

### F09 설정 (S05, 앱만)

- **테마:** 시스템·라이트·다크 중 하나를 고른다. 기기에 저장한다.
- **언어:** English·한국어 중 하나를 고르고 기기에 저장한다.
  - 언어는 S05에서만 바꾼다. 다른 화면에는 언어 버튼(지구본)이 없다.
  - 바꿔도 S03·S04에 쓰던 값은 그대로 남는다.
  - 표지판 칩은 언어와 상관없이 한국어·영어를 함께 보인다.
- **계정:**
  - 로그인했으면 '…(으)로 로그인됨'과 [로그아웃]을 보인다.
  - 로그아웃하면 앱이 토큰을 버린다. 서버 요청은 없다.
  - 로그인 전이면 [로그인]을 보인다.
- **사진 출처:** 사진마다 출처, 라이선스(CC BY-SA 4.0 등), 원본 주소를 선택·복사할 수 있는 글자로 보인다.
- **DB:** 없음. 서버의 사용자 이름은 로그인 응답의 user.username을 쓴다.

### F10 회원가입 (API만)

- **요청:** POST /api/auth/signup {username, password}
- **규칙:**
  - username: 소문자 영문·숫자·밑줄 4~24자, 유일해야 한다. 이미 있으면 409 USERNAME_TAKEN이다.
  - password: 8자 이상, UTF-8 72바이트 이하.
- **서버 처리:** BCrypt(강도 10)로 해시해 저장한다. 비밀번호와 해시는 응답하지 않는다. 201 {id, username}을 준다.
- **화면:** 없음(범위 밖).
- **DB:** app_user(app_user_seq 시작 103)

## 3. 화면 → 기능 → API → DB 추적표

| 화면 | 화면 요소 | 기능 | API 필드 | DB 열 |
| --- | --- | --- | --- | --- |
| S01 | 경로 카드 순서 | F01 | routes 배열 순서 | route.sort_order |
| S01 | 경로 카드 이름·보조 문구 | F01 | name.en/ko, summary.en/ko | route.name_*, summary_* (T2 카드 설명은 앱 문구, 6절) |
| S01 | 단계 수, 준비 중 | F01 | stepCount, available | route_step 개수, route.available |
| S01 | 이어 보기 'n/N' | F03 | — | 기기 저장 |
| S02 | 진행 'n/N' | F02 | stepNo, 목록 길이 | route_step.step_no, route_step 개수 |
| S02 | 사진 | F02 | photoKey | route_step.photo_key |
| S02 | 역할 라벨 + 표지판 칩 | F02 | role, sign.ko/en | route_step.role, sign_ko, sign_en |
| S02 | 표지판 픽토그램 | F02 | icon | route_step.icon |
| S02 | 제목·행동 안내 | F02 | title, instructions | route_step.title_*, instructions_* |
| S02 | 전체 단계 목록(펼치기) | F02 | steps[].stepNo, title | route_step.step_no, title_* |
| S02 | 엘리베이터 줄 | F02 | elevator | route_step.elevator_en·elevator_ko(둘 다 있을 때만) |
| S02 | 팁 카드 제목·위치·메모 | F04 | title, location, note | tip.title, location, note |
| S02 | 배지·날짜 | F04 | status, verifiedAt, example | tip.status, verified_at, example |
| S02 | 작성자 표시 '샘플'·'내가 올림'·'여행객' | F04 | example, mine | tip.example, tip.owner_id(토큰과 비교) |
| S02 | '내가 올림'·상태 버튼 | F04·F07 | mine, id | tip.owner_id(토큰과 비교) |
| S02 | '지난 팁 n개' 접힌 묶음 | F04 | status | tip.status(OUTDATED 개수) |
| S03 | 경로·단계 요약 줄('직통열차 · 5단계 중 3단계 — 단계 제목') | F05 | 경로 name(GET /api/routes), stepNo, 단계 목록 길이, 단계 title | route.name_*, route_step 개수, route_step.title_* (보낼 때는 tip.route_code, step_no) |
| S03 | 제목·위치·메모 칸 | F05 | title, location, note | tip.title, location, note |
| S04 | 아이디·비밀번호 | F06 | username, password | app_user.username, password_hash |
| S05 | 로그인됨 이름 | F06·F09 | user.username | app_user.username |
| S05 | 테마·언어·사진 출처 | F09 | — | 기기 저장 / 앱 안 표 |
| S06 | 내 팁 목록('직통열차 · 5단계 중 3단계', 배지, 상태 버튼) | F08 | Tip 전체 + GET /api/routes의 name·stepCount | tip(owner_id), route.name_*, route_step 개수 |

## 4. 상태와 권한 규칙

| 규칙 | 내용 | 확인하는 곳 |
| --- | --- | --- |
| 팁 상태 | VERIFIED(작성자 확인) ↔ OUTDATED(정보 오래됨) 두 값만. 등록 때는 VERIFIED | 서버 TipService |
| 상태 날짜 | 등록과 상태 변경마다 verified_at = 지금 | 서버 |
| 바꿀 수 있는 사람 | owner_id = 토큰의 사용자만. 그 밖은 403 | 서버(앱의 버튼 숨김은 편의일 뿐) |
| 예시 팁 | 서버는 올린 사람이면 허용한다. 앱은 버튼을 숨기고 S06에서 뺀다 | 앱 |
| 공개 조회 | GET 경로·단계·팁은 토큰 없이 가능. 무효 토큰을 붙이면 401 | 서버 JwtAuthenticationFilter |
| 로그인 끝남 | 401을 받으면 요청 당시 세션이 지금 세션일 때만 로그아웃 | 앱 AppController |
| 수정·삭제 | 팁 내용 수정·삭제는 없다(범위 밖) | — |

## 5. 오류 코드와 화면

오류 응답은 {code, message, errors}다(이 모양이 아닌 예외: /error 직접 호출, 보안 필터 안의 예상하지 못한 예외 등은 api-reference.md 6절 B3).
- message는 한국어 한 가지다.
- 앱은 code와 errors의 키를 보고 자기 번역 문구를 보인다.
- 오류 응답에는 스택 추적, SQL, 토큰을 넣지 않는다.

| code | 상태 | 생기는 기능 | 앱 처리 |
| --- | --- | --- | --- |
| VALIDATION_FAILED | 400 | F04·F05·F06·F07·F10, 숫자가 아닌 step·경로 변수(예 step=abc, /api/tips/abc, /api/tips/1.5, long 범위를 넘는 id는 errors.id) | F05는 errors 키별 칸 오류, 나머지는 일반 실패 안내 |
| INVALID_REQUEST_BODY | 400 | F05·F06·F07·F10(읽을 수 없는 JSON, 정하지 않은 항목, 자료형이 다른 값: stepNo 3.7·"3", 숫자 title, status 1 등) | 앱 결함. 일반 실패 안내 |
| BAD_REQUEST | 원래 4xx 상태 그대로 | 따로 처리하지 않은 그 밖의 Spring 4xx | 일반 실패 안내 |
| UNAUTHORIZED | 401 | 토큰 없음·무효·만료 | 로그아웃하고 로그인 안내 |
| LOGIN_FAILED | 401 | F06 | 아이디 유지, 비밀번호 비움 |
| FORBIDDEN | 403 | F07 | 권한 없음 알림 + 다시 로그인, 버튼 막음 |
| NOT_FOUND | 404 | F02·F07, GET /api/tips/{id} | 안내 |
| USERNAME_TAKEN | 409 | F10 | (화면 없음) |
| METHOD_NOT_ALLOWED · UNSUPPORTED_MEDIA_TYPE | 405 · 415 | 잘못된 요청 | 앱 결함 |
| INTERNAL_ERROR | 500 | 어디서나 | 다시 시도 |

## 6. 앱에서만 정하는 것

서버가 주지 않고, DB에도 없는 것이다.

| 항목 | 위치(앱) |
| --- | --- |
| 출발 터미널, S01 표지판 병기 줄 | lib/models/route_info.dart routeDisplays |
| 준비 중 T2 카드 설명과 안내 | 앱 문구 |
| 사진 파일과 출처·라이선스 | lib/models/step_photos.dart (photoKey → 파일) |
| 화면 문구 번역(오류 문구 포함) | lib/l10n/app_strings.dart |
| 테마, 언어, 진행 단계 | shared_preferences |
| 로그인 토큰 | 앱 메모리(기기에 저장하지 않음) |

반대로 서버가 주지만 지금 화면은 쓰지 않는 값이다. 지우지 않고 둔다.

| 값 | DB | 지금 화면 | 메모 |
| --- | --- | --- | --- |
| Tip.createdAt | tip.created_at | 쓰지 않음(배지·정렬은 verifiedAt과 id) | 기록용. 나중에 '올린 날짜'에 쓸 수 있다 |
| Tip.ownerId | tip.owner_id | 쓰지 않음(실제 화면은 mine만 씀) | 공개 GET에서 뺄지는 9절 미정 11 |
| T2의 summary | route.summary_* | 쓰지 않음(앱 문구 t2msg로 바꿔 보임) | T2 문구를 서버와 앱 중 어디서 정할지 하나로 정하면 정리된다. 지금은 DB 값이 참고용 |
| T2의 stepCount | route_step 개수(0) | 준비 중 카드에는 단계 수를 보이지 않음 | |

## 7. requirements.md와 맞춘 점

- R03 언어 바꾸기: '어느 화면에서나'를 'S05 설정에서만'으로 고쳤다(지구본 버튼 제거 결정).
- R07 설정: 서버 요구가 없다는 이유로 표에서 빠져 있었다. 앱 기능으로 표에 넣었다.

## 8. 확인 방법

| 기능 | 서버 검사 | 앱 검사 |
| --- | --- | --- |
| F01·F02 | 공개 조회 MockMvc 테스트(reports/verification.md P03·P06) | test/api_contract_test.dart(명세 예시 읽기), 화면 위젯 검사 |
| F04~F08 | 인증·등록·권한 회귀 테스트(P04~P06) | 저장소 가짜 응답 검사, 화면 위젯 검사 |
| DB 표·시드 | SeedAndSchemaTest(메모리 DB의 INFORMATION_SCHEMA: 시퀀스·열·UNIQUE·인덱스·ENUM, 시드 한 번만), SchemaScriptTest(docs/schema.sql = 엔티티가 만드는 CREATE 문) | — |
| 앱 ↔ 서버 실제 연결 | — | 10장에서 확인(지금은 미확인) |

## 9. 미정 (data-model.md 검토 메모와 같음)

1. 여행객이 팁용 계정을 얻는 방법(회원가입 화면 추가? 현장 발급?). 강사 확인이 필요하다.
2. 예시 팁의 상태 변경을 서버에서도 막을지.
3. 같은 단계의 같은 내용 팁을 막을지.
4. 팁이 많아질 때 개수 제한·페이지 나누기.
5. 서버 오류 문구의 영어 지원(지금은 앱이 번역).
6. 같은 상태를 다시 보낼 때 날짜를 갱신할지(지금은 갱신).
7. 현장 확인 뒤 단계 문구·사진 고치는 방법(지금은 route_step을 직접 고침, 관리 API 없음).
8. 로그인 토큰을 기기에 남길지(10장).
9. 폭 없는 공백(U+200B)처럼 눈에 안 보이는 글자만 있는 제목을 막을지(지금은 1자로 받음).
10. 현장 확인으로 단계를 고칠 때 단계 번호 규칙 또는 단계 버전(팁·진행 기록이 다른 단계를 가리키지 않게).
11. 공개 GET 응답의 ownerId를 뺄지(계약 변경, 10장 연결 전).
12. 나중에 열이 필요할 수 있는 것: 단계 현장 확인 날짜, 다시 시도 중복 막기 키, 팁 언어, 표지판 방향·색 등(data-model.md 검토 메모 12).
