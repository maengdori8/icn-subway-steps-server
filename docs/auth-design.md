# 인증 설계 · ICN Subway Steps 서버

문서 정보: 2026-10-20 · 근거 docs/requirements.md, docs/api-spec.yaml · 작성 Claude Code, 검토: (본인 이름)

## 비밀번호

- 가입 때 password를 검사한다: 8자(코드 포인트) 이상, UTF-8 72바이트 이하. BCrypt는 72바이트 뒤를 잘라 버리므로 그보다 긴 값은 받지 않는다.
- 저장은 BCrypt(강도 10) 해시만. 평문·해시는 응답·로그·보고서에 쓰지 않는다.
- 로그인 비교는 `PasswordEncoder.matches`. 없는 아이디여도 미리 만든 가짜 해시와 한 번 비교해 응답 시간 차이로 아이디 존재가 드러나지 않게 한다.
- 로그인 실패는 아이디가 없든 비밀번호가 틀렸든 같은 401 LOGIN_FAILED 문구다.

## JWT

| 항목 | 값 |
| --- | --- |
| 알고리즘 | HS256 (헤더 alg가 HS256이 아니면 거절. 서명 없는 토큰 거절) |
| 발급자(iss) | skilltransfer-ch07 (검증 때 일치 요구) |
| 주체(sub) | 사용자 id (문자열) |
| 그 밖의 클레임 | username, iat, exp |
| 유효 시간 | 1800초. 응답 expiresIn=1800 |
| 라이브러리 | JJWT 0.13.0 (jjwt-api, jjwt-impl, jjwt-jackson) |

- JWT 서명은 내용 암호화가 아니다. 누구나 Base64로 페이로드를 읽을 수 있으므로 비밀값을 넣지 않는다.
  디코딩만으로 믿지 않고 서버가 서명·발급자·만료를 검증한 뒤에만 사용자로 인정한다.
- exp·iat·username 클레임이 없는 토큰은 거절한다(JJWT는 exp가 있을 때만 만료를 검사하므로 직접 요구한다).
- 서명이 맞아도 DB 사용자와 다시 대조한다: sub의 사용자가 없거나, username이 다르거나, 토큰 발급 시각(iat)이 계정 생성 시각보다 앞서면 401.
  DB만 초기화하고 키를 남기면 시퀀스가 103부터 다시 시작해 같은 id가 다른 사람에게 배정될 수 있기 때문이다(Codex 반박 반영).
- 올린 사람(owner_id)은 요청 본문이 아니라 검증한 토큰의 사용자로 정한다. 본문에 ownerId·status가 오면 400.

## 키 생성·보관·재사용

1. 환경 변수 `JWT_SECRET`(속성 app.jwt.secret)에 Base64 값이 있으면 그것을 쓴다. 32바이트보다 짧으면 시작을 멈춘다.
2. 없으면 `data/.jwt-secret` 파일을 읽는다.
3. 파일도 없으면 `SecureRandom` 32바이트를 만들어 Base64로 그 파일에 저장한다(처음 실행 한 번).

- 재시작해도 같은 파일을 읽으므로 이미 발급한 토큰이 만료 전까지 유효하다.
- data/는 .gitignore에 있어 Git·보고서에 들어가지 않는다. 코드·설정 파일에 키 상수를 두지 않는다.
- 키를 바꾸려면 서버를 끄고 파일을 지운다(모든 토큰 무효).
- 테스트는 build/test-data/.jwt-secret에 따로 만든다.

## 요청 처리 순서

```
요청 → JwtAuthenticationFilter → 접근 규칙(SecurityConfig) → 컨트롤러 → 서비스(올린 사람 비교)
```

- 필터: Authorization 헤더가 없으면 익명으로 통과. 있으면 `Bearer <토큰>`이어야 하고 검증에 실패하면 즉시 401 UNAUTHORIZED.
  이 규칙은 공개 GET에도 적용한다(무효·만료 토큰을 붙인 공개 GET은 401). /api/auth/**와 dev의 /h2-console/**는 헤더를 보지 않는다.
- 접근 규칙: 공개 — GET /, /api/health, /api/routes, /api/routes/*/steps, /api/tips, /api/tips/*, POST /api/auth/signup·login.
  나머지는 인증 필요. 익명이면 401 UNAUTHORIZED(JSON).
- 세션을 만들지 않는다(STATELESS). 쿠키를 쓰지 않으므로 CSRF 보호는 끈다. 폼 로그인·HTTP Basic도 끈다.
- dev 프로필에서만 /h2-console/**를 별도 체인으로 허용한다(프레임 sameOrigin, CSRF 제외).

## 401과 403

| 상황 | 상태 | code | message | 주는 곳 |
| --- | --- | --- | --- | --- |
| 보호된 API에 Authorization 헤더 없음 | 401 | UNAUTHORIZED | 로그인이 필요합니다. | SecurityConfig 진입점 |
| 헤더가 Bearer가 아님(Basic 등)·빈 토큰·토큰 형식 오류·서명 불일치(변조·다른 키)·만료·exp·iat·username 없음·발급자 다름·alg 다름 | 401 | UNAUTHORIZED | 로그인이 만료되었거나 올바르지 않습니다. | JwtAuthenticationFilter(공개 GET에도) |
| 토큰의 사용자가 DB에 없음·username 다름·계정 생성 전에 발급 | 401 | UNAUTHORIZED | 위와 같음 | JwtAuthenticationFilter |
| 로그인 실패(입력 검사 400을 통과한 뒤) | 401 | LOGIN_FAILED | 아이디 또는 비밀번호가 맞지 않아요. | AuthService |
| 로그인했지만 그 팁을 올린 사람이 아님 | 403 | FORBIDDEN | 이 팁을 올린 사람만 상태를 바꿀 수 있습니다. | TipService |

401은 "누구인지 모름", 403은 "누구인지는 알지만 이 팁의 주인이 아님"이다. 앱은 message가 아니라 code를 본다.
상태 변경은 서비스가 DB의 owner_id와 토큰의 사용자 id를 비교한 뒤에만 값을 바꾼다. 앱의 버튼 숨김은 이 검사를 대신하지 않는다.
상태 변경의 판단 순서는 401(토큰) → 400(경로 id 형식) → 415(JSON이 아닌 본문) → 400(본문·status) → 404(팁 없음) → 403 → 변경이다.
로그인의 LOGIN_FAILED는 이 401과 달리 400 검사 뒤에 나온다. 전체 순서는 docs/api-spec.yaml 공통 규칙.
