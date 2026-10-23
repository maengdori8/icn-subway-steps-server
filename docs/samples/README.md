# 실제 응답 표본 (교재 10장 자료표)

이 폴더는 교재 10장 자료표의 '실제 응답 표본' 자리다. 학생이 실행 서버에서 직접 모은다.
**아직 모으지 않았다(미완료).** 지금은 이 안내만 있다.

[../api-examples/](../api-examples/README.md)는 자동 테스트(MockMvc, 메모리 DB) 예시라서 실제 응답이 아니다. 이 폴더에는 실행 서버가 실제로 돌려준 응답만 둔다.

## 모으는 방법

1. **서버 실행:** 사람이 별도 PowerShell 창에서 켠다(README.md '실행'). `http://127.0.0.1:8097/api/health`에 status UP이 보이면 된다.
2. **Postman으로 요청:** 기본 주소 `http://127.0.0.1:8097`. 본문은 raw · JSON(Content-Type: application/json), Accept는 기본값(`*/*`) 그대로 둔다. 요청별 규칙은 [../api-reference.md](../api-reference.md).
3. **로그인:** `POST /api/auth/login`에 표본 계정 student01을 보낸다(비밀번호는 docs/requirements.md '표본 자료'. 이 폴더에는 적지 않는다). 응답의 accessToken을 Postman 변수(예 `token`)에 넣고 `Authorization: Bearer {{token}}`으로 보낸다.
4. **저장하기 전에 숨긴다:**
   - 응답의 accessToken 값 → `"<숨김>"`
   - 요청도 함께 적으면 password 값 → `"<숨김>"`. Authorization 헤더 값은 적지 않는다.
   - 확인: `Select-String -Path docs\samples\*.json -Pattern 'eyJ'` 결과가 비어 있어야 한다(토큰은 eyJ로 시작한다).
   - 테스트도 확인한다: `.\gradlew.bat test --offline --rerun --tests '*ApiContractDocTest*'`. ApiContractDocTest.samplesHideSecrets가 이 폴더의 파일(README.md 빼고)에서 토큰 모양 값을 찾고, JSON 파일의 accessToken·Authorization·password 값이 비어 있거나 `"<숨김"`으로 시작하는지 본다.
5. **파일 이름:** api-examples와 같은 이름으로 저장하면 비교하기 쉽다.

| 파일 | 요청 |
| --- | --- |
| routes-list.json | GET /api/routes |
| route-steps.json | GET /api/routes/EXPRESS/steps |
| tips-for-step.json | GET /api/tips?route=LOCAL&step=4 (토큰 없이) |
| tip-detail.json | GET /api/tips/7 |
| login-response.json | POST /api/auth/login (토큰 숨김) |
| tip-create.json | POST /api/tips (student01 토큰) |
| tip-status.json | PATCH /api/tips/{방금 만든 id}/status |
| my-tips.json | GET /api/me/tips |
| 실패 응답 | 예: login-401-wrong-password.json, tip-status-403-not-owner.json(student02 토큰으로 student01의 팁), tip-detail-404-not-found.json |

   응답 본문만 저장해도 되고, api-examples처럼 `{request, response, meta}`로 저장해도 된다. 어느 쪽이든 실행한 날짜와 '실행 서버 127.0.0.1:8097'을 함께 적는다(파일 안 meta.source 또는 이 README의 표).

## api-examples와 비교하기

- 같은 이름의 파일끼리 **구조**를 비교한다: 키 이름, 자료형, null 여부, 상태 코드, 201의 Location 헤더.
- **값**은 달라도 된다: id, createdAt·verifiedAt, 토큰. 실행 서버는 파일 DB라 앞서 만든 팁이 남아 있을 수 있고, 시퀀스 캐시 때문에 새 id가 건너뛸 수 있다(data-model.md 'ID 생성').
- 구조가 다르면 docs/api-spec.yaml과 비교해 서버·문서 중 어느 쪽이 맞는지 정하고 reports/verification.md에 적는다.

## 넣지 않을 것

실제 비밀번호, 토큰, DB 파일(data/icn-subway.mv.db), 키 파일(data/.jwt-secret). 표본 비밀번호 값도 적지 않는다.
