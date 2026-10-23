# P00 권한표와 상태 규칙 파일 만들기

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 이 파일을 내 서버 프로젝트의 prompts\ch08\ 에 복사하고 입력창에 `@prompts/ch08/P00_rules.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 다른 프로젝트나 배포 원본을 덮어쓰지 않는다.

## 요청
@docs/api-spec.yaml @docs/auth-design.md
@reports/change-review.md 와
실제 소스(인증 필터·보안 설정·물건 Service)를 읽고
두 파일을 새로 만들어 줘.
1) docs/permission-matrix.md: 요청(메서드·경로)마다
   로그인 없음·등록자 A·다른 사용자 B의 기대 응답을
   표로 쓴다. 없는 번호·무효 토큰·입력 오류의 응답은
   표 아래에 쓴다.
2) docs/state-rules.md: 이전 상태·요청·다음 상태·
   요청할 수 있는 사람·같은 요청을 다시 보낸 결과·
   응답을 표로 쓴다.
명세와 소스가 다른 곳은 고치지 말고 각 파일 끝
'확인할 질문' 절에 근거 파일과 줄을 적어 줘.
이 두 파일 말고는 만들거나 고치지 마.

읽는 파일: docs/api-spec.yaml, docs/auth-design.md, reports/change-review.md, 인증·물건 관련 src

주로 바뀔 파일(예시): docs/permission-matrix.md, docs/state-rules.md (새 파일 두 개)
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

확인과 기록: 두 파일을 직접 열어 교재 1.2 표·2.1~2.3과 대조한다. 틀린 칸은 내가 고친 뒤 커밋한다.
