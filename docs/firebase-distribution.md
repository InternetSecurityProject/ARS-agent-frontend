# 팀 APK와 Firebase 업데이트

## 팀원 최초 설치

1. `meokbodeul` 그룹에 추가한 Google 계정으로 Firebase 초대 메일을 수락한다.
2. 메일/테스터 링크에서 첫 APK를 설치한다. Android가 요청하면 해당 설치 경로의 '알 수 없는 앱 설치'를 허용한다.
3. 앱의 빈 키패드 대기 화면에서 '팀 앱 업데이트 안내 → 로그인'을 눌러 초대받은 Google 계정으로 로그인한다.
4. 이후 새 배포가 있으면 대기 화면에서 업데이트 여부를 묻는다. '업데이트' 후 Android 설치 확인은 사용자가 승인한다. 무인/강제 설치는 아니다.

기존 GitHub APK가 다른 debug 키로 서명되었다면 덮어쓰기 설치가 거부될 수 있다. 그 경우 사용자가 기존 앱을 삭제하고 팀 APK를 처음 설치해야 한다(앱 데이터 삭제 주의). CI/앱은 기존 앱을 자동 삭제하지 않는다.
테스터 로그인은 API 키 입력과 별개다. ARS 서버 API 키는 기존과 같이 저장하지 않는다.

## 관리자 설정

- Android 패키지: `kr.ac.suwon.arsagent.demo`.
- Firebase의 Google Cloud 프로젝트에서 **Firebase App Testers API (`firebaseapptesters.googleapis.com`)**를 활성화해야 앱 내 로그인이 동작한다. 배포용 서비스 계정 권한과는 별개다.
- 서비스 계정에는 Firebase App Distribution Admin 역할을 준다. Owner/Editor는 필요 없다.
- Secret 7개: `FIREBASE_APP_ID`(프로젝트 ID가 아니라 `1:…:android:…`), `GOOGLE_SERVICES_JSON`(원본 JSON), `FIREBASE_SERVICE_ACCOUNT_JSON`(원본 JSON), `TEAM_KEYSTORE_BASE64`, `TEAM_STORE_PASSWORD`, `TEAM_KEY_ALIAS`, `TEAM_KEY_PASSWORD`.
- 고정 서명키는 안전하게 백업한다. 이후 바꾸면 기존 설치 앱에 업데이트할 수 없다.

## 배포 조건

`feat/compose-mvp` push 또는 해당 브랜치의 수동 Android APK 실행만 Firebase 그룹 `meokbodeul`에 배포한다. main 병합은 필요하지 않다. 다른 브랜치/PR은 기존 검증·APK artifact만 만들고 Firebase Secret을 사용하지 않는다.
main merge는 코드 통합이고 배포는 별도의 Actions 동작이다. 현재 워크플로에서 main push는 검증/APK 보관만 하며 Firebase 팀 배포 조건은 아니다.

CI 버전 코드는 `run_number * 100 + run_attempt`로 증가하며, 로컬 기본값은 1이다. Firebase 배포에는 설정/팀 서명이 필수다. 설정 검증 실패 시 APK를 배포하지 않으며 임의 debug 키로 대체하지 않는다.
업로드 성공 로그의 `testing_uri`가 팀원용 링크이고 `firebase_console_uri`는 관리자용이다. signed binary URL은 만료되므로 공유용으로 사용하지 않는다.

## 앱 동작과 안전장치

공식 Firebase App Distribution Android SDK `16.0.0-beta21`의 고급 검사/로그인/업데이트 API를 사용한다. 원치 않는 늦은 팝업을 피하려고 자동 다이얼로그 방식은 사용하지 않는다.
빈 키패드/통화·연락처 목록 등 기본 대기 화면에서만 안내한다. 번호 입력, 상세, 목적 입력, 발신 확인, 통화, 데모 모드, 별도 서버 테스트/갤러리에서는 안내하지 않는다.
업데이트 검사는 10분 간격으로 제한한다. '나중에'/오류 닫기는 현재 앱 프로세스에서 다시 묻지 않는다. 재시도하려면 앱을 종료 후 다시 연다. 거절/오프라인도 기존 기능을 막지 않는다.
설치를 선택하면 SDK 다운로드 중에는 진행창을 보여 새 통화 시작과 겹치지 않게 한다. Android 최종 설치 승인은 사용자가 한다. 별도 알림 권한을 요구하지 않고 앱 내부에 진행 상태를 표시한다.

Firebase 설정이 없는 로컬 debug 빌드는 업데이트가 비활성화된다. release에는 자체 업데이트 SDK/설정 자산이 없다. Google 서비스 Gradle 플러그인을 전역 적용하지 않고 debug 전용의 선택된 공개 클라이언트 필드만 FirebaseOptions로 초기화하여 release의 다른 패키지 빌드도 유지한다.
로컬에서 공개 앱 설정만 사용하려면 `GOOGLE_SERVICES_JSON`/`FIREBASE_APP_ID`를 안전하게 환경으로 제공한 후 `python3 scripts/firebase_config.py --output app/src/debug/assets/firebase/firebase-team.json`을 실행한다. 이 경로는 git에서 무시된다. 관리자 서비스 계정은 앱 폴더에 넣지 않는다.

## 검증 경계

자동 테스트는 업데이트 상태 전이, 오래된 콜백, 거절/실패, 설치 명시 승인, 설정 검증, 기존 화면 회귀를 확인한다. 실제 테스터 Google 로그인과 Android 설치 승인은 팀원의 기기에서 별도 확인해야 한다.
공식 문서: [앱 내 알림](https://firebase.google.com/docs/app-distribution/set-up-alerts?platform=android), [CLI 배포](https://firebase.google.com/docs/app-distribution/android/distribute-cli).
