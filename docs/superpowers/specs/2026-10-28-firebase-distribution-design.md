# Firebase 팀 배포와 앱 내 업데이트 안내

## 의도와 승인 범위

팀원이 매번 GitHub APK를 찾지 않아도 앱에서 새 버전을 확인하고 설치한다.
사용자가 Firebase App Distribution을 선택했고, 그룹 별칭은 `meokbodeul`이다.
작업 브랜치는 `feat/compose-mvp`이며 `main` 병합, 백엔드 변경, 실제 발신은 하지 않는다.

## 접근

- 선택한 방식: Firebase 공식 App Distribution Android SDK와 기존 GitHub Actions를 연결한다.
- 직접 APK 다운로드/설치 관리 기능을 새로 만드는 방식은 사용하지 않는다.
- Google Play 내부 테스트는 이번 범위가 아니다.
- 통화 중 비동기 검사 결과가 도착해도 안내를 띄우지 않도록 SDK의 검사와 안내 표시 시점을 분리한다.

## 앱 동작

- Firebase 설정이 포함된 팀 테스트용 debug APK에서만 업데이트 기능을 활성화한다.
- 기존 기본 화면 디자인, A 데모, B/C/D, 음성 기능, 별도 서버 테스트, 정지 미리보기는 유지한다.
- 기본 앱의 대기 화면에서만 테스터 로그인/업데이트 안내를 제공한다. 목적 입력·발신 확인·통화·미리보기 중에는 띄우지 않는다.
- 최초 1회 테스터가 Google 계정으로 업데이트 안내 사용에 동의한다. 거절하거나 오프라인이어도 앱 사용을 막지 않는다.
- 새 버전은 사용자에게 설치 여부를 묻는다. 자동 삭제·무인 설치·강제 업데이트는 하지 않는다.
- 백그라운드/통화 진입 후 도착하는 오래된 응답은 화면을 방해하지 않는다. 업데이트 실패도 기존 앱 흐름과 분리한다.
- 일반 로컬 빌드에서 Firebase 설정이 없으면 기능을 비활성화해 기존 데모·미리보기를 실행할 수 있다.
- release에는 자가 업데이트 SDK 구현을 포함하지 않는다. Firebase 앱 설정은 관리자 서비스 계정 JSON과 엄격히 구분한다.

## Actions 배포

- 기존 Android APK 워크플로를 확장한다. `feat/compose-mvp` push 또는 해당 브랜치의 명시적 수동 실행에서만 Firebase 배포한다.
- PR/다른 브랜치에서는 Firebase 배포 자격 증명을 사용하지 않고 기존 검증을 유지한다.
- 고정 팀 서명키와 증가하는 CI 버전 코드를 사용한다. Firebase 배포 시 서명/설정이 빠지면 임의 debug 키로 대체하지 않고 실패시킨다.
- `GOOGLE_SERVICES_JSON`의 패키지와 앱 ID를 `kr.ac.suwon.arsagent.demo`, `FIREBASE_APP_ID`와 대조한다. 비밀값을 오류나 로그에 출력하지 않는다.
- 검증 → 팀 서명 APK 빌드 → APK artifact 보관 → Firebase 업로드 및 `meokbodeul` 배포 순서다.
- 서비스 계정/서명키는 CI 임시 경로에서만 사용하고 정리한다. 소스, APK, 보고서 artifact에 관리자 키·비밀번호를 포함하지 않는다.

## 이미 준비된 입력

등록된 Secret 이름 7개를 읽기 전용으로 확인했다. 값은 읽지 않았다:
`FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT_JSON`, `GOOGLE_SERVICES_JSON`,
`TEAM_KEYSTORE_BASE64`, `TEAM_STORE_PASSWORD`, `TEAM_KEY_ALIAS`, `TEAM_KEY_PASSWORD`.
로컬 Downloads 접근이 제한되어 있으므로 사용자 파일을 우회해서 읽지 않는다. CI의 앱 설정 Secret을 사용한다.

## 검증과 인계

순수 상태 테스트로 중복 검사, 거절/오프라인, 통화/백그라운드 전환, 늦은 응답을 확인한다.
배포 설정과 임시 파일 처리는 실제 값을 쓰지 않는 fixture로 검증한다.
전체 기존 테스트, lint, debug/release 빌드를 실행하고 최종 변경을 별도로 검토한다.
기능 브랜치에 푸시한 정확한 커밋의 Actions와 첫 Firebase 업로드 결과를 확인한다.
실제 Google 로그인·사용자 설치 승인은 팀원과 함께 별도로 확인하며 성공을 추정하지 않는다.

기존 APK와 팀 서명이 다르면 첫 전환 시 사용자가 재설치해야 할 수 있다.
사용 중 에뮬레이터를 삭제/초기화하지 않고, 팀용 첫 APK 설치 경로와 사용자 수행 단계를 안내한다.
Firebase App Testers API가 비활성화되어 있거나 IAM 권한이 부족하면 정확한 오류를 보고하고 사용자에게 필요한 설정만 요청한다.
