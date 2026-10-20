# 실제 실행 기록

이 폴더에는 처음부터 학생의 실행 결과가 들어 있지 않다.
P03부터 verification.md를 만들고 P번호별로 실행한 프로젝트 경로·명령·종료 코드·입력과 계정 조건·기대값·실제값·재조회 결과를 적는다.
형식은 7장 배포 폴더의 004_기록양식/001_검증기록_작성양식.md를 참고하되, 값은 실제로 실행한 결과로만 채운다.
실패한 내용은 지우지 않고 원인·최소 수정·재검사 결과를 이어 붙인다. 아직 실행하지 않은 항목은 미실행으로 표시한다.
P07의 change-review.md에는 바뀐 파일과 이유, 실제 명세·소스·검사 파일 경로, 남은 확인, 8장이 읽을 경로표를 적는다(형식: change-review-형식예.md).
docs/verification-plan.md는 앞으로 검사할 계획이므로 통과 결과와 구분한다.
8장은 이 폴더의 verification.md에 '## 8장 Postman·Newman 검사' 절을, change-review.md에 '## 8장 변경 파일과 Postman 파일 경로' 절을 덧붙인다.
verification.md와 change-review.md는 비밀값을 지운 뒤 Git에 커밋한다. API 토큰·실제 비밀번호·DB·키는 보고서에 저장하지 않는다.
명령 출력 원문을 따로 보관하려면 reports/raw/에 둔다(.gitignore로 커밋에서 빠진다).
