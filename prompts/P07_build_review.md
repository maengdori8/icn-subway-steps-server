# P07 빌드·실패 수정·변경 정리

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P07_build_review.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@docs/api-spec.yaml @docs/implementation-plan.md
@docs/verification-plan.md @reports/verification.md 를 읽고
빌드·검사·실패 수정·변경 정리를 해 줘.
1) java -version 이 21인지 확인하고 .\gradlew.bat test bootJar 를
   실행해 명령·종료 코드·실패한 테스트 이름을
   reports/verification.md의 P07 항목에 덧붙인다.
2) 실패하면 원인과 바꿀 파일을 먼저 설명한 뒤 최소로 고치고
   같은 검사를 다시 실행한다. 테스트를 지우거나 주석 처리하지 마.
3) 서버는 내가 다른 PowerShell 창에서 bootRun으로 켠다.
   너는 서버를 켜거나 끄지 말고 HTTP 요청만 보낸다.
   8097을 다른 프로그램이 쓰고 있으면 끄지 말고 알려 줘.
   한글 JSON은 UTF-8로 보낸다.
4) 목록·로그인·등록·수정·반환·삭제와 잘못된 요청을 확인하고
   명세와 다르면 명세와 코드 중 어느 쪽을 고칠지 설명한다.
5) git diff -- src build.gradle docs 를 읽고
   reports/change-review.md에 바뀐 파일·이유·남은 문제와
   8장이 읽을 경로표(명세·인증 설계·검사 계획·실행 기록·
   실행 주소)를 적는다.
DB·키·토큰·개인정보는 결과와 Git에 넣지 마.
하지 않은 GUI·모바일 검사를 했다고 쓰지 마.

읽는 파일: docs/api-spec.yaml, docs/implementation-plan.md, docs/verification-plan.md, reports/verification.md

주로 바뀔 파일(예시): reports/verification.md, reports/change-review.md (실패를 고쳤다면 그 소스 파일)
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P07 항목과 reports/change-review.md.
