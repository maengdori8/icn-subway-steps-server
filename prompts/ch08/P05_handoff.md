# P05 8장 변경과 검사 기록 정리

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 이 파일을 내 서버 프로젝트의 prompts\ch08\ 에 복사하고 입력창에 `@prompts/ch08/P05_handoff.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 다른 프로젝트나 배포 원본을 덮어쓰지 않는다.

## 요청
@reports/verification.md @reports/change-review.md 를 읽고
git diff -- src docs postman 의 변경을 정리해 줘.
1) reports/change-review.md에
   '## 8장 변경 파일과 Postman 파일 경로' 절을 덧붙인다.
   바뀐 파일·이유·확인 방법 표, Postman 두 JSON과
   reports/my-api.junit.xml의 경로, 9장이 읽을 파일을 쓴다.
2) reports/verification.md의 8장 절에서 비어 있는 칸과
   미실행 항목을 표시한다. 값을 지어내지 마.
3) 구현을 고쳤다면 docs/api-spec.yaml과
   어긋난 곳이 있는지 알려 줘.
토큰·비밀번호·DB·키는 어떤 파일에도 쓰지 마.

읽는 파일: reports/verification.md, reports/change-review.md, git diff

주로 바뀔 파일(예시): reports/change-review.md, reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

확인과 기록: 두 파일을 읽고 비밀값이 없는지 확인한 뒤 git add·commit 한다.
