# P00 입력과 기존 코드 읽기

권한 모드: Plan(계획 모드) · 파일을 바꾸지 않는다
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P00_readonly.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@CLAUDE.md @docs/requirements.md @docs/S03-screen-spec.md
@docs/ui-handoff.md @build.gradle 을 읽어 줘.
이번 요청은 읽기만 한다. 파일을 바꾸거나 패키지를 설치하지 마.
1) @SpringBootApplication 진입점 파일의 실제 경로와 클래스 이름
2) 지금 있는 API와 포트, 아직 없는 기능(DB·CRUD·로그인·JWT)
3) 앞으로 만들 설계 파일과 Java 파일 목록(제안)
4) 화면 요구에서 아직 정하지 않은 것(질문 목록)
이 네 가지를 짧게 정리해 줘.

읽는 파일: CLAUDE.md, docs/requirements.md, docs/S03-screen-spec.md, docs/ui-handoff.md, build.gradle, 진입점 파일(제공 시작 프로젝트: src/main/java/org/skilltransfer/lostfound/StarterApplication.java)

주로 바뀔 파일(예시): 없음. 읽기만 한다.
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: 기록 없음. 답변에 나온 질문 목록은 사람이 docs/data-model.md의 검토 메모(P01 뒤)에 옮긴다.
