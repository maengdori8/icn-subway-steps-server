# 상태 규칙 · ICN Subway Steps 서버

문서 정보: 2026-10-21 · 교재 8장 P00 · 작성 Claude Code, 검토: (본인 이름)

근거:
- docs/api-spec.yaml(Tip, TipStatusRequest, PATCH /api/tips/{id}/status)
- docs/auth-design.md
- reports/change-review.md
- 소스: TipService.java, Tip.java, TipController.java

## 상태 값

| 값 | 뜻 | 화면 문구 |
| --- | --- | --- |
| VERIFIED | 작성자 확인 | '작성자 확인 · 날짜' |
| OUTDATED | 정보 오래됨 | '정보 오래됨 · 날짜' |

- API에서는 status, DB에서는 tip.status(H2 ENUM)다.
- verifiedAt(DB: verified_at)은 '작성자가 마지막으로 상태를 정한 시각'이다.
- 날짜가 지나도 상태가 저절로 바뀌지 않는다. 작성자가 요청해야만 바뀐다.

## 전이 표

| 이전 상태 | 요청 | 다음 상태 | 요청할 수 있는 사람 | 같은 요청을 다시 보낸 결과 | 응답 |
| --- | --- | --- | --- | --- | --- |
| (팁 없음) | POST /api/tips | VERIFIED. verifiedAt = createdAt = 지금, example = false | 로그인한 사용자(올린 사람이 등록자가 됨) | 새 팁이 하나 더 생긴다(중복 방지 없음) | 201 + Location + Tip |
| VERIFIED | PATCH {"status":"OUTDATED"} | OUTDATED, verifiedAt = 지금 | 등록자 | 200, OUTDATED 유지, verifiedAt만 다시 지금 | 200 + Tip |
| OUTDATED | PATCH {"status":"VERIFIED"} | VERIFIED, verifiedAt = 지금 | 등록자 | 200, VERIFIED 유지, verifiedAt만 다시 지금 | 200 + Tip |
| VERIFIED | PATCH {"status":"VERIFIED"} | VERIFIED 그대로, verifiedAt = 지금(작성자 재확인) | 등록자 | 위와 같음 | 200 + Tip |
| OUTDATED | PATCH {"status":"OUTDATED"} | OUTDATED 그대로, verifiedAt = 지금 | 등록자 | 위와 같음 | 200 + Tip |
| 어느 상태든 | 다른 사용자 B의 PATCH | 바뀌지 않음 | 없음 | 계속 403 | 403 FORBIDDEN |
| 어느 상태든 | 토큰 없음·무효 토큰의 PATCH | 바뀌지 않음 | 없음 | 계속 401 | 401 UNAUTHORIZED |
| 어느 상태든 | 잘못된 status(DELETED, 소문자 verified, null, status 없음 {}) | 바뀌지 않음 | 없음 | 계속 400 | 400 VALIDATION_FAILED, errors.status |
| 어느 상태든 | 본문에 다른 항목(title 등)이 섞임, 본문 없음, 깨진 JSON | 바뀌지 않음 | 없음 | 계속 400 | 400 INVALID_REQUEST_BODY |
| 어느 상태든 | Content-Type이 JSON이 아닌 PATCH | 바뀌지 않음 | 없음 | 계속 415 | 415 UNSUPPORTED_MEDIA_TYPE |
| (팁 번호가 숫자가 아님: abc, 1.5) | 유효한 토큰의 PATCH | 없음 | 없음 | 계속 400 | 400 VALIDATION_FAILED, errors.id |
| (팁 없음: 없는 번호, 0, 음수) | 유효한 토큰의 PATCH | 없음 | 없음 | 계속 404 | 404 NOT_FOUND |

근거:
- 판단 순서(401 → 400 경로 id → 415 → 400 본문 → 400 status → 404 → 403 → 변경): docs/api-spec.yaml:46-47, TipService.java:23·89-97
- 바뀌는 값은 status와 verifiedAt뿐: Tip.java:54-58
- 테스트: PermissionRegressionTest의 ownerChangesStatusAndVerifiedAtIsRefreshed, otherUserGets403AndNothingChanges, invalidStatusBodiesAre400AndNothingChanges, missingTipIs404AndOrderIs401Then400Then404Then403, unauthenticatedStatusChangeIs401AndNothingChanges

## 상태 변경 때 바뀌지 않는 값

title, location, note, ownerId(owner_id), createdAt, route, stepNo, example.

- 거절(400·401·403·404) 뒤에는 status와 verifiedAt도 바뀌지 않는다.
- 다시 조회해 비교할 때 mine은 빼고 본다. mine은 저장값이 아니라 요청한 토큰에 따라 계산되는 값이기 때문이다(TipService.java:134-138).

## 교재 2.1~2.3과 다른 점

| 교재 예제(분실물) | 이 앱(팁) |
| --- | --- |
| FOUND → RETURNED 한 방향. 되돌리는 API 없음 | VERIFIED ↔ OUTDATED 양방향. 작성자가 다시 확인할 수 있게 함(결정: '작성자 확인 + 날짜') |
| 반환 요청은 본문 없음. {}를 보내면 400 | 상태 변경은 본문 {"status": ...} 필수. {}이면 400 VALIDATION_FAILED |
| 반환 뒤 PUT(내용 수정)은 상태·등록자 유지 | 내용 수정 API 없음. 대신 상태 변경이 다른 값을 바꾸지 않는지 확인 |
| 반복 반환은 200, 최종 상태 같음(멱등) | 반복하면 status는 같지만 verifiedAt이 다시 바뀜. 엄밀히는 멱등이 아님(확인할 질문 1) |
| 삭제 204, 다시 삭제하면 404 | 삭제 API 없음 |

## 확인할 질문

1. 같은 상태를 다시 보내면 verifiedAt이 바뀐다(Tip.java:54-58). '작성자 재확인'으로 해석한 결정인데, 교재의 멱등 설명과는 다르다. 날짜를 그대로 둘지 정해야 한다(docs/data-model.md 검토 메모 6, docs/functional-spec.md 미정 6).
2. 예시 팁(example=true)의 상태 변경을 서버에서도 막을지 정해야 한다. 지금은 등록자이면 허용한다(검토 메모 2).
3. 팁 등록 POST를 재전송하면 같은 팁이 두 번 생긴다. 중복 방지(Idempotency-Key 같은 방법)는 없다(미정 3, 교재 2.3 넓혀 보기).
