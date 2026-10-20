# P01 화면명세를 API 명세와 데이터 모델 파일로 작성

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P01_api_data.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@CLAUDE.md @docs/requirements.md @docs/S03-screen-spec.md
@docs/ui-handoff.md 를 읽고 서버 설계 파일 두 개를 만들어 줘.
1) docs/api-spec.yaml: OpenAPI 3.0.3 형식의 API 명세
2) docs/data-model.md: 테이블·열·자료형·필수값·키·관계 표
지킬 규칙:
- 경로는 /api/items, /api/items/{id}, /api/items/{id}/return,
  /api/auth/signup, /api/auth/login 으로 맞춘다.
- 응답 필드는 id, title, location, note, status, owner_id 이고
  목록은 {"items": [...]} 모양이다.
- 사용자가 입력하는 값(title·location·note)과
  서버가 정하는 값(id·status·owner_id)을 구분한다.
- PUT은 편집 필드 전체 교체(note 생략은 null), PATCH /return은
  본문 없음·반복해도 200, DELETE는 204와 빈 본문이다.
- 오류 응답은 Error 스키마 {code, message, errors}로 쓰고
  400·401·403·404·409 예를 함께 적는다.
- 공개 GET에 무효·만료 토큰이 붙어 오면 401이라고 적는다.
- ID 생성 방식과 시작값(물건 9, 사용자 103)을 data-model.md에 적는다.
- 정하지 못한 것은 data-model.md 끝의 검토 메모에 질문으로 남긴다.
이번에는 src를 바꾸지 마. 두 파일의 경로와 내가 검토할 점을 알려 줘.

읽는 파일: CLAUDE.md, docs/requirements.md, docs/S03-screen-spec.md, docs/ui-handoff.md

주로 바뀔 파일(예시): docs/api-spec.yaml, docs/data-model.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: 실행 기록 없음. 사람이 두 파일을 열어 검토하고 커밋한다.
