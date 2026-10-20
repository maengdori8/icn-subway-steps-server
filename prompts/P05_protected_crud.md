# P05 등록자 검사와 보호된 CRUD 구현

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P05_protected_crud.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
P04 검사를 통과한 상태에서 @docs/api-spec.yaml @docs/auth-design.md
@docs/verification-plan.md 를 읽고 보호된 CRUD를 구현해 줘.
1) 등록 POST: 201과 Location. 등록자는 검증한 토큰의 사용자,
   status는 FOUND. 본문에 owner_id·status가 있으면 400으로 거절한다.
2) 수정 PUT: 편집 필드 전체 교체. 반환한 물건도 200,
   status·owner_id는 그대로 둔다.
3) 반환 PATCH /return: 본문 없음, 반복해도 200.
4) 삭제 DELETE: 204와 빈 본문.
5) 수정·반환·삭제는 DB의 등록자와 같을 때만 실행한다.
   권한을 확인하기 전에 값을 바꾸지 않는다.
6) 입력 오류 400은 Error 스키마와 S03 문구를 따른다.
7) 테스트: 정상 등록, 빈 제목·81자 400, 토큰 없음 401,
   다른 등록자 403, 없는 번호 404.
GET을 로그인 필수로 바꾸지 마. P03·P04 테스트도 함께 실행해
결과를 reports/verification.md의 P05 항목에 적고,
Service의 핵심 변경 diff를 보여 줘.

읽는 파일: docs/api-spec.yaml, docs/auth-design.md, docs/verification-plan.md

주로 바뀔 파일(예시): src/main/java/org/skilltransfer/lostfound/ 아래의 ItemController.java · ItemService.java(쓰기 추가) · 요청 DTO(예: Requests.java) · 오류 응답 처리, src/test/java/org/skilltransfer/lostfound/ItemWriteTest.java, reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P05 항목. 계획의 기대값과 실제 결과를 구분한다.
