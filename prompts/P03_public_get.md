# P03 Entity와 공개 조회 구현

권한 모드: Manual(편집마다 확인)
사용법: '## 요청' 아래 줄을 Claude Code 입력창에 그대로 붙여 넣는다. 또는 입력창에 `@prompts/P03_public_get.md 의 요청을 수행해 줘`라고 입력한다.
경로가 다른 자기 프로젝트는 요청의 @경로를 실제 파일 경로로 바꾼 뒤 쓴다. 참고 코드를 통째로 덮어쓰지 않는다.

## 요청
@CLAUDE.md @docs/api-spec.yaml @docs/data-model.md
@docs/implementation-plan.md 를 읽고 Entity와 공개 조회
(GET 목록·상세)만 구현해 줘.
1) 진입점 StarterApplication(내 프로젝트는 실제 진입점)을 그대로 쓴다.
2) build.gradle에는 이번 단계에 필요한 것만 추가한다:
   spring-boot-starter-data-jpa, spring-boot-starter-validation,
   spring-boot-h2console, com.h2database:h2(runtimeOnly),
   spring-security-crypto(표본 비밀번호 해시용).
   추가할 줄과 이유를 먼저 보여 줘.
3) H2는 파일 DB ./data/chapter07을 쓰고, H2 콘솔은 dev 프로필에서만
   켠다(application-dev.properties에 spring.h2.console.enabled=true).
4) 표본 자료: 사용자 표가 비어 있을 때만 student01(101)·student02(102)를
   BCrypt 해시로, 물건 7·8을 requirements.md의 값으로 한 번 넣는다.
   새 번호가 물건 9, 사용자 103부터 나오게 SEQUENCE 시작값을 정한다.
5) 응답은 DTO로 만들고 password_hash는 내보내지 않는다.
6) POST·PUT·PATCH·DELETE 처리기는 아직 만들지 마.
7) 테스트: 목록 200(표본 2건), 상세 200, 없는 번호 404.
gradlew를 실행하기 전에 java -version 이 21인지 보여 줘.
.\gradlew.bat test 의 명령·종료 코드·기대/실제 결과를
reports/verification.md의 P03 항목에 적어 줘. 못 했으면 이유를 적어.

읽는 파일: CLAUDE.md, docs/api-spec.yaml, docs/data-model.md, docs/implementation-plan.md

주로 바뀔 파일(예시): build.gradle, src/main/resources/application.properties, src/main/resources/application-dev.properties, src/main/java/org/skilltransfer/lostfound/ 아래의 AppUser.java · Item.java · UserRepository.java · ItemRepository.java · ItemService.java(조회만) · ItemController.java(GET만) · 응답 DTO(예: Responses.java) · SeedData.java(표본 자료), src/test/java/org/skilltransfer/lostfound/PublicReadTest.java, reports/verification.md
목록 밖의 파일을 만들거나 바꿔야 하면 Claude Code가 먼저 이유를 말해야 한다. 파일 이름은 AI마다 다를 수 있으며, 명세·권한·실제 실행 결과가 기준이다.

실행 기록: reports/verification.md의 P03 항목. 계획의 기대값과 실제 결과를 구분한다.
