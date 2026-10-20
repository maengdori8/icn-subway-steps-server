# P02 JWT 인증 설계와 구현·검사 계획 작성

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P02_auth_plan.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@docs/requirements.md @docs/api-spec.yaml @docs/data-model.md 를 읽고
인증 설계와 계획 파일 세 개를 만들어 줘.
1) docs/auth-design.md: BCrypt 저장과 비교, HS256 서명·발급자·
   만료(1800초), 키 생성·보관·재사용, 401과 403의 구분
2) docs/implementation-plan.md: P03~P07에서 바꿀 파일과 순서
3) docs/verification-plan.md: 조건·입력·계정·기대 응답·
   실패 뒤 재조회 기준을 담은 검사 계획 표
지킬 규칙:
- JWT 서명은 내용 암호화가 아니며 디코딩만으로 믿지 않는다고 적는다.
- 등록자는 요청 본문의 owner_id가 아니라 검증한 토큰의 사용자로 정한다.
- 순서는 공개 GET → 회원가입·로그인·JWT → 보호된 CRUD다.
  인증을 마치기 전에는 쓰기 API를 만들지 않는다.
- 검사 계획에는 토큰 없음·변조·만료 401, 다른 등록자 403,
  없는 번호 404, 중복 username 409, 삭제 204, 실패 뒤 재조회,
  표본 계정 로그인 200, dev 프로필의 H2 콘솔 접속을 넣는다.
- 이 표는 실행 전 계획이다. 통과했다고 쓰지 마.
이번에는 src를 바꾸지 마. 세 파일의 경로와 내가 검토할 점을 알려 줘.

읽는 파일: docs/requirements.md, docs/api-spec.yaml, docs/data-model.md

주로 바뀔 파일(예시): docs/auth-design.md, docs/implementation-plan.md, docs/verification-plan.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: 실행 기록 없음. 사람이 세 파일을 열어 검토하고 커밋한다.
