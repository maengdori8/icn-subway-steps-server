# P04 내 API의 Postman 파일 만들기

권한 모드: 요청 1은 Plan, 요청 2는 Manual
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 이 파일을 내 서버 프로젝트의 prompts\ch08\ 에 복사하고 입력창에 `@prompts/ch08/P04_postman_files.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 다른 프로젝트나 배포 원본을 덮어쓰지 않는다.

## 요청 1 (Plan 모드에서 보낸다)
@docs/api-spec.yaml @docs/permission-matrix.md
@docs/state-rules.md @reports/change-review.md 와
로그인·물건 Controller를 읽어 줘. 지금은 파일을 수정하지 마.
로그인·목록·등록·수정·반환·삭제의 메서드, 경로, 인증,
본문, 성공 응답을 표로 정리하고, 토큰과 새 물건 번호를
어느 응답 필드에서 읽는지 근거 파일과 함께 보여 줘.
명세와 구현이 다르면 추측하지 말고 차이와 질문을 알려 줘.

요청 1의 답을 읽고 고칠 항목을 고른 뒤 모드를 Manual로 바꾼다.

## 요청 2 (Manual 모드)
방금 확인한 표를 기준으로 Postman 파일을 만들어 줘.
1) postman/my-api.collection.json(Collection v2.1)과
   postman/my-api.environment.json을 쓴다.
2) 환경에는 baseUrl·userA·userB·passwordA·passwordB만
   두고 비밀번호 값은 비운다. tokenA·tokenB·itemId·
   snapshot은 컬렉션 변수로 두고
   pm.collectionVariables.set으로 저장한다.
3) 공개 목록 → A·B 로그인 → A의 새 물건 등록으로 시작하고
   401·403·404·입력 오류·반복 반환·
   거절 뒤 재조회를 검사한다.
   거절을 시험하는 본문은 현재 저장값과 다르게 쓴다.
4) 검사에서 만든 물건만 마지막에 지우고
   기존 자료는 그대로 둔다.
5) 요청 순서·기대 결과·실행 명령을
   docs/verification-plan.md의
   '## 8장 Postman·Newman 검사 계획' 절에 덧붙인다.
실제 토큰과 비밀번호는 어떤 파일에도 쓰지 마.

읽는 파일: docs/api-spec.yaml, docs/permission-matrix.md, docs/state-rules.md, reports/change-review.md, 로그인·물건 Controller

주로 바뀔 파일(예시): 요청 2: postman/my-api.collection.json, postman/my-api.environment.json, docs/verification-plan.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

확인과 기록: 두 JSON을 직접 열어 교재 4.2의 점검표로 확인한 뒤 Import한다. 실행 결과는 내가 reports/verification.md의 8장 절에 적는다.
