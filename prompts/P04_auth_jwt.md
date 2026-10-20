# P04 회원가입·로그인·JWT 인증 구현

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P04_auth_jwt.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@docs/api-spec.yaml @docs/auth-design.md @docs/verification-plan.md
를 읽고 회원가입·로그인·JWT 인증을 한 단계로 구현해 줘.
물건 쓰기 API는 아직 만들지 마.
1) build.gradle에 spring-boot-starter-security와 jjwt 0.13.0
   (jjwt-api, runtimeOnly jjwt-impl·jjwt-jackson)을 추가한다.
   추가할 줄과 이유를 먼저 보여 줘.
2) 가입: username 규칙·중복 409, 비밀번호 8자 이상·UTF-8 72바이트
   이하, BCrypt 해시 저장. 응답에 비밀번호와 해시를 넣지 않는다.
3) 로그인: 성공 200(accessToken·tokenType·expiresIn·user), 실패 401.
4) JWT: HS256, 발급자 skilltransfer-ch07, 만료 1800초. 키는 상수로
   두지 말고 처음 실행 때 32바이트 난수로 만들어 data 폴더에 보관한다.
5) SecurityConfig: 가입·로그인과 GET 목록·상세만 공개하고
   나머지 /api/** 는 인증을 요구한다. dev 프로필에서는
   /h2-console/** 만 따로 허용(프레임 sameOrigin, CSRF 제외)한다.
6) 테스트: 가입 201·중복 409·로그인 200·틀린 비밀번호 401,
   토큰 없음·변조·만료 401, 공개 GET 200.
토큰과 비밀번호 해시는 로그와 보고서에 쓰지 마.
명령·종료 코드·기대/실제 결과를 reports/verification.md의
P04 항목에 적어 줘.

읽는 파일: docs/api-spec.yaml, docs/auth-design.md, docs/verification-plan.md

주로 바뀔 파일(예시): build.gradle, src/main/java/org/skilltransfer/lostfound/ 아래의 AuthController.java · JwtService.java · JwtAuthenticationFilter.java · SecurityConfig.java · 가입·로그인 DTO(예: Requests.java) · 오류 응답 처리(예: ApiError.java · ApiAdvice.java), src/test/java/org/skilltransfer/lostfound/AuthApiTest.java, reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P04 항목. 계획의 기대값과 실제 결과를 구분한다.
