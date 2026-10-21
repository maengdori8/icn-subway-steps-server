# ICN Subway Steps 서버

인천공항 T1 입국장 → 서울역행 공항철도 탑승까지 단계 안내와 단계별 팁을 주는 API 서버.
교재 7장 시작 프로젝트(분실물 API)를 복사해 경로·단계·팁 도메인으로 바꿨다. Spring Boot 4.1.1 · Java 21 · H2 파일 DB · JWT(HS256).

| 문서 | 내용 |
| --- | --- |
| docs/api-spec.yaml | API 계약(OpenAPI 3.0.3). 앱 연결의 기준 |
| docs/data-model.md | 테이블·제약·시드·초기화 방법·검토 메모(맨 위 ER 그림) |
| docs/schema.sql | 엔티티가 만드는 CREATE 문(참고용. 표는 JPA가 만든다. H2 콘솔에서 비교해 볼 것) |
| docs/functional-spec.md | 기능별 입력·처리·실패·DB, 화면 → API → DB 추적표 |
| docs/auth-design.md | 비밀번호·JWT·키 보관·401/403 |
| docs/requirements.md | 기능 요구(R01~R06, R08)와 수업 기술 조건 |
| docs/verification-plan.md / reports/verification.md | 검사 계획 / 실제 결과 |
| reports/change-review.md | 바뀐 파일·Codex 반박 판정·남은 문제·인계 |
| docs/ui-*.csv, S02·S03-screen-spec.md, ui-handoff.md, ui-validation-decisions.md | 5장 입력(C:\dev\chapter05-ai\docs에서 복사, 수정하지 않음) |

## 실행

PowerShell에서:

    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
    $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    java -version
    .\gradlew.bat test
    .\gradlew.bat bootRun

- http://127.0.0.1:8097/api/health 에 status UP이 보이면 실행된 것이다. 종료는 그 창의 Ctrl+C.
- H2 콘솔이 필요하면 bootRun 전에 `$env:SPRING_PROFILES_ACTIVE='dev'`. 주소 http://127.0.0.1:8097/h2-console, JDBC URL `jdbc:h2:file:./data/icn-subway`, 사용자 sa, 비밀번호 없음.
- 처음 실행하면 data/icn-subway.mv.db(DB)와 data/.jwt-secret(JWT 키)이 생긴다. 둘 다 Git에서 빠진다.
  DB를 처음 상태로 돌리려면 서버를 끄고 data/icn-subway.mv.db를 지운다(시드가 다시 들어간다).
- 표본 계정 student01·student02, 비밀번호는 docs/requirements.md의 공개 표본 값(로컬 실습 전용).

## 앱(Flutter)에서 연결

- 기본 주소: PC에서는 http://127.0.0.1:8097, Android 에뮬레이터에서는 http://10.0.2.2:8097. 서버가 127.0.0.1에만 열려 있어 실제 휴대폰은 접속할 수 없다.
- 로그인 응답의 accessToken을 `Authorization: Bearer <accessToken>` 헤더로 보낸다. 1800초 뒤 만료되면 401 → 다시 로그인.
- 응답 필드는 camelCase다. 자세한 내용은 docs/api-spec.yaml과 reports/change-review.md의 '앱 연결' 절.
