# P08 선택: 파일 업로드 명세와 구현

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P08_optional_upload.md 의 요청 1(또는 요청 2)을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청 1 · 명세만 고치기
@docs/api-spec.yaml @docs/data-model.md @docs/auth-design.md 를 읽고
물건 사진 업로드 계약을 명세에 추가해 줘. 이번에는 docs만 고친다.
- POST /api/items/{id}/files, multipart 필드 이름 file
- PNG/JPEG, 파일당 2 MiB(2,097,152바이트), 전체 요청 3 MiB 이하
- 가로·세로 각각 4096픽셀, 전체 1600만 픽셀 이하
- 업로드와 GET /api/files/{fileId} 내려받기 모두 등록자만
- 원래 파일 이름은 메타데이터로만 두고 저장 이름은 UUID
- 413·415·400·403 오류 예와 파일/DB 중간 실패 처리
기본 S03에 원래 있던 기능이라고 쓰지 마. 물건 등록 API는 바꾸지 마.

## 요청 2 · 검토한 명세로 구현하기(요청 1의 결과를 사람이 검토한 뒤)
검토한 @docs/api-spec.yaml @docs/data-model.md 대로
파일 업로드를 구현해 줘.
확장자·선언 MIME·파일 시그니처·실제 이미지 해석을 모두 확인한다.
테스트: 정상 PNG/JPEG, 위장·빈·큰 파일, ../ 같은 경로 문자,
다른 사용자의 업로드·내려받기.
실패 뒤 기존 파일과 DB가 그대로인지 확인하고
결과를 reports/verification.md의 P08 항목에 적어 줘.

읽는 파일: docs/api-spec.yaml, docs/data-model.md, docs/auth-design.md

주로 바뀔 파일(예시): 요청 1: docs/api-spec.yaml, docs/data-model.md, docs/verification-plan.md / 요청 2: src/main/resources/application.properties(multipart 제한), src/main/java/org/skilltransfer/lostfound/ 아래의 ItemFile.java · ItemFileRepository.java · FileController.java · FileService.java · 오류 처리(413·415), src/test/java/org/skilltransfer/lostfound/FileUploadTest.java, reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P08 항목. 하지 않은 검사는 미실행으로 적는다.
