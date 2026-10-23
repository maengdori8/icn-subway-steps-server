# P03 실패 기록을 읽고 최소 수정

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 이 파일을 내 서버 프로젝트의 prompts\ch08\ 에 복사하고 입력창에 `@prompts/ch08/P03_fix_failure.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 다른 프로젝트나 배포 원본을 덮어쓰지 않는다.

## 요청
@docs/api-spec.yaml @docs/permission-matrix.md
@docs/state-rules.md @reports/verification.md 의
'8장 실패 F01' 항목과 관련 소스를 읽어 줘.
먼저 기대값과 실제값을 비교해 원인이 주소·변수·
요청 순서·서버 구현 중 어디인지 분류하고,
고칠 파일을 말해 줘. 토큰이나 키는 출력하지 말고,
검사를 지우거나 기대값을 바꾸지 마.
최소로 고친 뒤 .\gradlew.bat test 를 실행해 결과를 보여 줘.
서버 재시작과 Newman 재실행은 내가 하고 결과를 알려 줄게.
git diff로 바뀐 파일을 설명하고, 확인하지 못한 항목은
F01 아래 '남은 확인'에 적어 줘.

읽는 파일: docs/api-spec.yaml, docs/permission-matrix.md, docs/state-rules.md, reports/verification.md(8장 실패 F01), 관련 src

주로 바뀔 파일(예시): 원인이 된 src(또는 Postman JSON), reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

확인과 기록: 서버를 다시 켜고 같은 Newman 명령을 실행해 종료 코드와 실패 수를 F01 아래 '재검증'에 내가 적는다.
