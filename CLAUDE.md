# 분실물 API 학습 프로젝트

Java 21, Spring Boot 4.1.1, Gradle(Groovy DSL), 패키지 org.skilltransfer.lostfound를 사용한다.
입력 자료는 docs/requirements.md, docs/S03-screen-spec.md, docs/ui-handoff.md다.
첫 시작에는 API 명세·데이터 모델·JWT 설계가 없다. P01·P02에서 파일로 작성하고 사람이 검토한다.
채팅 답변만으로 끝내지 말고 허용된 로컬 파일에 저장한다. 아직 없는 파일을 읽었다고 하지 않는다.

## 순서
P00 읽기 → P01 API·DB 명세 → P02 인증 설계·구현 계획·검사 계획 → P03 Entity·공개 GET·표본 자료 →
P04 회원가입·로그인·JWT 인증 → P05 보호된 CRUD → P06 권한·실패 재검사 →
P07 빌드·실패 수정·변경 정리 → P08 선택 파일 업로드.
요청문은 prompts/ 폴더에 있다. 각 요청문이 지정한 파일을 @경로로 읽고, 지정한 범위만 바꾼다.

## 실행 환경
- JDK 21 경로: C:\dev\jdk-21 (학생은 자기 PC의 실제 경로로 이 줄을 고친다).
- gradlew를 실행하기 전에 java -version 출력이 21인지 확인한다. 21이 아니면 코드를 고치지 말고 먼저 알린다.
- build.gradle의 toolchain(21)과 Spring Boot 버전(4.1.1)을 임의로 바꾸지 않는다.
- 서버(bootRun)는 사람이 별도 PowerShell 창에서 켜고 끈다. Claude Code는 HTTP 요청과 테스트만 실행한다.
- 8097 포트를 다른 프로그램이 쓰고 있으면 그 프로그램을 끄지 말고 알린다.

## Spring Boot 4 규칙
- 의존성 이름: spring-boot-starter-webmvc, spring-boot-starter-data-jpa, spring-boot-starter-validation,
  spring-boot-starter-security, spring-boot-h2console. 테스트는 spring-boot-starter-webmvc-test를 쓴다.
- MockMvc 테스트의 @AutoConfigureMockMvc는 org.springframework.boot.webmvc.test.autoconfigure 패키지다.
  Mock 객체는 @MockBean 대신 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다.
- JSON은 Jackson 3(tools.jackson 패키지)이다. com.fasterxml.jackson.databind를 import하지 않는다.
  애너테이션(com.fasterxml.jackson.annotation)은 그대로 쓴다.
- H2 콘솔은 dev 프로필에서만 켠다: spring-boot-h2console 의존성 + application-dev.properties의
  spring.h2.console.enabled=true. Spring Security를 넣은 뒤에는 dev 프로필에서 /h2-console/**만 따로 허용한다.
- Spring Boot 3 예제 코드로 컴파일 오류가 나면 버전을 내리지 말고 build.gradle을 다시 읽어 최소로 고친다.

## 지킬 경계
- 기존 StarterApplication을 유지한다. 별도 @SpringBootApplication을 추가해 진입점을 중복하지 않는다.
- 127.0.0.1:8097을 사용한다. 다른 사용자 서버와 포트를 바꾸지 않는다.
- P03은 조회만 구현한다. 인증과 서버의 등록자 확인이 준비되기 전에 쓰기 API를 노출하지 않는다.
- 표본 자료는 docs/requirements.md의 값으로, 사용자 표가 비어 있을 때만 한 번 넣는다. 비밀번호는 BCrypt 해시로 저장한다.
- JWT 키는 실행 시 생성·보관하거나 환경에서 읽는다. 공개 예시 키를 실제 서명 키로 고정하지 않는다.
- 실제 비밀번호, JWT, DB 파일, 키 파일을 답변·Git·보고서에 담지 않는다. 로컬 표본만 사용한다.
- 공개 조회는 로그인 없이, 변경은 인증된 사용자와 DB 등록자를 비교한 뒤 허용한다.
- 배포 받은 원본 수업 자료를 수정하지 않는다. 시작 프로젝트의 자기 사본만 수정한다.
- docs/S03-screen-spec.md·docs/ui-handoff.md는 5장에서 가져온 입력이다. API 문서로 바꾸어 덮어쓰지 않는다(서버 계약은 docs/api-spec.yaml).
- 테스트를 통과시키려고 권한 검사를 지우거나 실패 기대값을 바꾸지 않는다.
- 불명확한 화면 기능을 추가하지 않는다. 외부 배포·결제·실제 계정 로그인을 수행하지 않는다.
- 요청문의 '주로 바뀔 파일' 밖의 파일을 만들거나 바꿔야 하면 먼저 이유를 말한다.

## 실행과 기록
각 단계에서 읽을 파일과 쓸 파일을 먼저 밝힌다. 설계 파일은 사람이 검토한 뒤 다음 구현에 쓴다.
명령·종료 코드·기대/실제 결과·실패와 재검사는 reports/verification.md에 P번호별로 기록한다.
검사 계획은 docs/verification-plan.md에 두고, 변경 이유와 인계 경로는 reports/change-review.md에 정리한다.
실행하지 않은 결과를 성공으로 보고하지 않는다.
git diff로 바뀐 줄을 읽고 작은 변경 단위로 검토한다. 요청문 하나를 마칠 때마다 사람이 커밋한다.
API 계약은 docs/api-spec.yaml 하나를 기준으로 한다. P01의 미정 사항은 docs/data-model.md의 검토 메모에 남긴다.
이 파일의 기술·경로는 제공 시작 프로젝트의 기준이다. 기존 자기 프로젝트에서는 실제 폴더·진입점·맞는 구현을 먼저 읽고 보존한다.
