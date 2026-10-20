# 7장 Claude Code 시작 프로젝트

이 폴더 전체를 내 서버 프로젝트 위치로 복사한다: C:\dev\chapter07-ai
8·11·12장도 같은 폴더를 이어 쓴다. 완성 예제는 강사가 화면으로 보여 주는 비교 자료다. 학생 기본 배포에는 포함하지 않는다.
Claude Desktop의 Code(Local, 이 폴더 선택) 또는 이 폴더에서 실행한 claude(CLI)로 연다.
설치와 로그인은 2장 1.4·1.7을 따른다. 이 자료가 자동으로 로그인하거나 원격 작업 공간을 만들지는 않는다.

첫 상태에는 GET / 와 GET /api/health만 있다. API·DB·JWT가 완성된 코드가 아니다.
docs에는 입력 자료 requirements.md·S03-screen-spec.md·ui-handoff.md(제공 예)만 있다.
5장에서 만든 내 S03-screen-spec.md·ui-handoff.md가 있으면 같은 이름으로 덮어 두고,
5장 요구사항표(R01~)는 docs/requirements.md의 '기능 요구' 절에 옮겨 적는다(기술 조건 절은 그대로 둔다).
P01에서 API 명세와 데이터 모델을, P02에서 인증 설계와 계획을 파일로 새로 쓴다.
API·DB·JWT 설계 완성본은 처음부터 제공하지 않는다. P01·P02로 만든 문서를 사람이 확인한 뒤 구현한다.

## 먼저 실행
PowerShell에서 JDK 21을 지정한다. 아래 경로는 자신이 설치한 경로로 맞춘다.

    $env:JAVA_HOME='C:\dev\jdk-21'
    $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    java -version
    .\gradlew.bat test bootJar
    .\gradlew.bat bootRun

java -version에 21이 보여야 한다. http://127.0.0.1:8097/api/health 에 status UP이 보이면 시작 프로젝트가 실행된 것이다.
종료는 그 서버 창의 Ctrl+C다. 같은 8097의 완성 예제 서버와 동시에 실행하지 않는다.
첫 Gradle 실행은 의존성 다운로드가 필요할 수 있다. --offline은 필요한 캐시가 준비되어 있을 때만 사용한다.

Claude Desktop의 Code에서 Claude가 gradlew를 실행하면 위 PowerShell 창의 $env:JAVA_HOME은 전달되지 않는다.
Windows 사용자 환경 변수 JAVA_HOME을 JDK 21로 등록(3장)한 뒤 Desktop을 완전히 끄고 다시 켜거나,
Code 탭에서 Local 위에 마우스를 올리면 나오는 톱니(로컬 환경 편집기)에 JAVA_HOME을 넣는다. CLI는 JAVA_HOME을 지정한 같은 창에서 claude를 실행한다.
CLAUDE.md의 'JDK 21 경로' 줄도 내 경로로 고친다.

## Git으로 시작 상태 기록
이 폴더에서 git init으로 로컬 저장소를 만들고 작성자를 git config --local로 정한 뒤(값은 강사 안내), 요청문 하나를 마칠 때마다 커밋한다(7장 배포 폴더의 003_변경기록안내.md).
강사가 Gitea 저장소를 배정했을 때만 3장 7.5 절차로 remote를 추가하고 push한다.
DB·키·실행 로그는 .gitignore로 빠진다. build.gradle과 reports의 기록 파일은 커밋한다.

## 단계마다 확인
P00→P01→P02→P03→P04→P05→P06→P07 순서다. P08은 선택이다.
P04에서 회원가입·로그인·JWT 필터까지 완성한 뒤 P05의 쓰기 API를 만든다.
인증 전에 권한 검사를 주석 처리하지 않는다. 처음에는 쓰기 처리기가 없으므로 물건을 변경할 수 없다.
실행 명령과 실패 결과는 reports에 남기고 AI에게 필요한 부분만 보여 준다.
git status --short와 git diff -- src build.gradle docs를 읽어 변경한 파일을 확인한다.
