# API 예시 JSON (자동 테스트 예시)

**이 폴더의 파일은 자동 테스트 예시이며 실행 서버의 실제 응답이 아니다.**
테스트 프로필과 메모리 DB에서 MockMvc로 요청을 보내 받은 응답이다. 실행 서버의 실제 응답 표본은 [../samples/](../samples/README.md)에 학생이 모은다.
API별로 어떤 파일을 보면 되는지는 [../api-reference.md](../api-reference.md) 3절의 '예시' 줄에 있다.

## 만든 방법

- 생성기: `src/test/java/org/skilltransfer/icnsubway/ApiExamplesGenerator.java`. 환경 변수 `API_EXAMPLES=1`일 때 파일을 만든다(아래 명령).
- 다른 테스트와 나눈 메모리 DB(`jdbc:h2:mem:icn-examples`)에 시드만 넣은 상태에서, 정해진 순서로 요청 36개를 보낸다. 그래서 다시 만들어도 같은 파일이 나온다.
- 요청마다 docs/api-spec.yaml과 맞춰 본다.
  - 요청(메서드·주소)이 yaml의 어느 작업인지(meta.operation은 이것으로 정한다)
  - 기대한 상태 코드인지, 그 상태 코드가 그 작업에 선언돼 있는지
  - 본문이 스키마에 맞는지: $ref·allOf·배열 원소까지 따라가며 필수 필드, 자료형, enum, null 허용, date-time 형식, yaml에 없는 필드
  - 선언한 헤더(201의 Location)가 있고, Location이 '요청 경로/응답 id'인지
- 하나라도 맞지 않으면 아무 파일도 쓰지 않고 실패한다(출력에는 예시 이름과 이유만 나온다). 모두 맞으면 `build/api-examples/<이름>.json`에 쓰고, 이 폴더와 다른 파일 목록을 `build/api-examples-changes.txt`에 적는다.
- 사람이 그 결과를 읽어 본 뒤 이 폴더로 복사한다.

## 평소 테스트의 비교

- 평소 `.\gradlew.bat test`에서도 생성기가 같은 요청을 다시 보내 이 폴더의 파일과 비교한다(파일은 쓰지 않는다).
- 파일 목록이나 내용(JSON 값)이 다르면 실패하고, 다른 파일 이름과 처음 다른 곳(JSON 경로)을 알려 준다. 코드나 yaml을 바꿔 응답이 달라졌다는 뜻이므로 아래 명령으로 다시 만든다.
- ApiContractDocTest는 이 폴더의 파일을 yaml·api-reference.md와 맞춰 본다: 요청과 operation, 상태 코드 선언, 스키마, 오류 code·message와 yaml 예시, 선언한 모든 (작업, 상태 코드)에 예시가 있는지, 시각·토큰·비밀번호 숨김, 링크.

## 바꿔 적은 값 (각 파일의 `meta.replaced`)

| 값 | 적은 것 | 이유 |
| --- | --- | --- |
| 응답의 accessToken | `<숨김: 예시용, 실행할 수 없는 값>` | 토큰 원문을 남기지 않는다 |
| 요청의 password(빈 값이 아닐 때) | `<숨김: 표본 비밀번호>` 같은 설명 | 비밀번호 값(표본 값 포함)을 문서에 쓰지 않는다 |
| 새로 만든 팁(9번)의 createdAt, 등록 응답의 verifiedAt | `2026-10-21T00:00:00Z` | 실행할 때마다 바뀌는 시각이라 고정값으로 적었다 |
| 상태 변경 응답(tip-status.json)의 verifiedAt | `2026-10-21T00:05:00Z` | PATCH는 verifiedAt을 그 시각으로 바꾸고 createdAt은 그대로다. 등록보다 뒤라는 순서만 살린 고정값이고, 5분 간격은 실제 값이 아니다 |

- 시드 팁 7·8의 시각(`2026-10-12T03:00:00Z`, `2026-09-30T03:00:00Z`)은 고정값이라 그대로다. ApiContractDocTest는 예시 안의 시각이 이 두 값이거나, meta.replaced에 적은 고정값인지 본다.
- Authorization 헤더 값은 어디에도 적지 않는다. 요청에 어떤 토큰을 붙였는지는 `request.auth`의 라벨(없음, student01 토큰, student02 토큰, 무효 토큰)로만 적는다.

## 파일 형식

```json
{
  "request": { "method": "POST", "url": "/api/tips", "auth": "student01 토큰", "body": { "route": "LOCAL", "stepNo": 4 } },
  "response": { "status": 201, "headers": { "Location": "/api/tips/9" }, "body": { "id": 9 } },
  "meta": { "operation": "POST /api/tips", "source": "ApiExamplesGenerator · 테스트 프로필 · 메모리 DB", "note": "자동 테스트 예시이며 실행 서버의 실제 응답이 아님" }
}
```

- `response.headers`에는 yaml이 선언한 헤더(Location)만 적는다. 본문이 없는 GET 요청은 `request.body`가 null이다.
- JSON이 아닌 본문을 보낸 415 예시(`*-415-not-json.json`)에만 `request.contentType`을 더 적었다. 이 본문에는 비밀번호를 넣지 않는다(415는 본문을 읽기 전에 정해진다).
- 위 예는 줄였다. 실제 파일에는 응답 본문 전체가 들어 있다.

## 다시 만드는 명령 (PowerShell, 프로젝트 폴더)

    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
    $env:GRADLE_USER_HOME='C:\dev\gradle-home'
    # 1) 만들기. 마지막에 찍히는 숫자(종료 코드)가 0이어야 한다. 0이 아니면 여기서 멈추고 실패 이유를 읽는다
    $env:API_EXAMPLES='1'; .\gradlew.bat test --offline --rerun --tests '*ApiExamplesGenerator*'; $code = $LASTEXITCODE; Remove-Item Env:API_EXAMPLES; $code
    # 2) 읽기. 파일 수(지금 36)와 바뀐 파일 목록을 보고, 목록의 파일을 열어 본다
    (Get-ChildItem build\api-examples\*.json).Count
    Get-Content build\api-examples-changes.txt -Encoding utf8
    # 3) 복사. 1)이 0일 때만 바꾼다
    if ($code -eq 0) { Remove-Item docs\api-examples\*.json; Copy-Item build\api-examples\*.json docs\api-examples\ }
    # 4) 다시 검사. 문서만 바뀌어도 돌도록 --rerun을 붙인다
    .\gradlew.bat test --offline --rerun

- `--rerun`이 없으면 Gradle이 테스트를 '이미 했다'고 건너뛸 수 있다. Gradle은 환경 변수와 docs/ 파일을 바뀐 입력으로 보지 않는다.
- 4)에서 생성기가 이 폴더를 지금 코드의 응답과 다시 비교하고, ApiContractDocTest가 yaml·api-reference.md와 맞춰 본다. 복사가 덜 됐거나 파일이 빠졌으면 여기서 실패한다.
- 요청을 더하거나 이름을 바꾸면 api-reference.md 3절의 '예시' 링크도 고친다(ApiContractDocTest가 양쪽을 확인한다).
