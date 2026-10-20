# P06 권한·상태·실패 뒤 저장값 재검사

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P06_permission_check.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@docs/api-spec.yaml @docs/verification-plan.md 와 현재 구현을 읽고
두 계정(student01·student02)으로 권한 회귀 검사를 보강해 줘.
1) A의 물건을 B가 PUT·PATCH·DELETE하면 403인지, 거절 전후
   DB의 여섯 필드와 행 개수가 같은지 확인한다.
2) FOUND와 RETURNED를 모두 시험하고, 반복 반환 200과
   반환 뒤 PUT에서 status가 그대로인지 확인한다.
3) 입력 오류 400과 무효·만료 토큰 401 뒤에도 저장값이
   바뀌지 않는지 확인한다.
검사가 실패하면 기대값을 느슨하게 바꾸지 말고
원인이 된 분기를 찾아 최소로 고쳐 줘.
추가한 검사 조건은 docs/verification-plan.md에,
실제 결과는 reports/verification.md의 P06 항목에 적어 줘.
토큰 값은 쓰지 마.

읽는 파일: docs/api-spec.yaml, docs/verification-plan.md, 현재 src

주로 바뀔 파일(예시): src/test/java/org/skilltransfer/lostfound/PermissionRegressionTest.java, docs/verification-plan.md, reports/verification.md (원인을 고쳤다면 그 소스 파일)
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P06 항목. 계획의 기대값과 실제 결과를 구분한다.
