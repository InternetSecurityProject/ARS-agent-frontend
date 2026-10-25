# 구현 기록

## 2026-10-25

- 작업 브랜치: `feat/compose-mvp`. 새 빈 리포이므로 별도 worktree 대신 새 checkout에서 시작.
- 계획: `docs/superpowers/plans/2026-10-25-compose-mvp.md`.
- Figma 재확인: 민감정보 전송 중 화면 `163:655`는 삭제됨. 수동 키패드는 C04로 재번호됨. 삭제된 화면은 복구하지 않음.
- 규칙 테스트 RED: 13개 중 8개 예상 실패를 확인. 순수 모델 구현 후 13개 통과.
- UI RED: 빈 루트에서 5개 실패 확인 → 구현 후 통과. 민감정보 입력 중 종료 회귀 테스트도 RED→GREEN 확인.
- 수동 입력 마지막 키 이후 10초 재개 테스트 RED→GREEN, 자동 목적 진행/입력 취소/종료 후 타이머 정지 포함 단위 테스트 17개 통과.
- API36 Android UI 테스트 7개 통과. 360×640dp·글자 130%에서 보조/종료 버튼 도달 가능, 키패드 스크롤 확인.
- `testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` 성공. 린트 오류 0; 버전 업데이트·백업 설정 권고는 후속 범위.
- 원본 Figma SVG를 앱에 포함하고 캡처로 배치 비교. Android 가변 글꼴 굵기와 큰 글씨 레이블 잘림 수정.
- 로컬 Unicode 경로에서 증분 컴파일 시 기존 최상위 함수 탐색 오류가 반복되어 증분 Kotlin 컴파일은 비활성화. 작은 앱의 전체 컴파일로 검증.
- Actions: PR/push 린트·단위 테스트·APK artifact, 선택적 수동 팀 서명 설정. 비밀키는 생성·등록하지 않음. 원격 실행 결과는 푸시 후 확인.

## 다음 구현 범위

- `InCallService`·기본 전화앱 역할·수신/실제 일반 통화.
- SpeechRecognizer와 마이크 권한, 인식 완료 기반 자동 진행(현재 텍스트 입력 정지 기준).
- REST 세션 생성/조작, WebSocket 이벤트 및 snapshot 복구.
- 민감정보 요청의 정확한 입력 길이/종료키 계약. 현재 UI 데모는 생년월일 6자리 예시.
- 오디오 수신/마이크 전달/라우팅과 상담원 연결 시 Agent 종료 계약 검증.
- 폰 연락처/통화기록 연동, 실패·권한 거절 시 안내, 고령자 사용성 검증.
