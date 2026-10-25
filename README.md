# ARS Agent Android

인터넷보안프로젝트 · 먹보들. Kotlin + Jetpack Compose 기반 전화앱 UI.

> 현재는 **UI 데모**입니다. 실제 전화 발신, 음성 인식, 오디오 재생, 연락처/통화기록 조회, 백엔드 통신은 하지 않습니다. 모든 번호·기록·날씨는 예시입니다. 실제 개인정보를 입력하지 마세요.

## 실행

- Android Studio, Android SDK 36 / Build Tools 35, JDK 17 또는 21.
- 프로젝트를 열어 SDK 동기화 후 `app` 실행. Android 8.0(API26) 이상.
- `local.properties`의 `sdk.dir`는 본인 SDK 경로로 설정하며 Git에 포함하지 않습니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # 실행 중인 에뮬레이터/연결 기기 필요
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
앱 ID `kr.ac.suwon.arsagent.demo`는 임시 개발용입니다. 정식 배포 전 팀 소유 도메인으로 확정합니다.

## 데모 흐름

1. 상단 `UI 데모`에서 날씨·후보 선택·추가 답변·민감정보·상담원 시나리오 선택.
2. 통화/연락처의 예시 항목 상세 또는 키패드에서 번호 입력.
3. `010`은 일반 전화만, 나머지 유효 번호는 일반/Agent 버튼 표시. 실제 ARS 지원 여부 판별은 아닙니다.
4. Agent 목적 입력은 입력이 멈춘 후 자동 진행. 마이크 버튼은 **예시 문장 입력**이며 녹음하지 않습니다.
5. 민감정보 화면은 데모 6자리 입력 완료 후 즉시 폐기. 수동 키패드는 키를 바로 반영합니다.
6. 오디오·음소거·보류는 UI 상태만 변경됩니다. 실제 오디오 경로 변경이 아닙니다.

## 구조

```
app/src/main/java/kr/ac/suwon/arsagent/
  model/       순수 Kotlin 상태·전환 규칙·예시 데이터
  ui/          화면, 공통 컨트롤, 테마
  AppViewModel 데모 타이밍과 취소 처리
```

- [Figma](https://www.figma.com/design/ul4FvS9IStRhfdoSzm70Ri/ARS-Agent): A/B/C/D01, 현재 C04는 수동 키패드.
- 원본 SVG는 `assets/figma/`. 네트워크로 이미지를 불러오지 않습니다.
- Noto Sans KR은 SIL OFL로 포함. SF Pro와 iOS 시스템 상태 표시줄은 재배포/모사하지 않습니다.
- 백엔드 연동 시 `model`의 이벤트/상태 갱신을 REST·WebSocket 어댑터에서 수행하도록 분리했습니다.

## GitHub Actions / APK 배포

PR와 `main`, `feat/**`, `fix/**` push 시 린트·단위 테스트·APK 빌드 후 Actions artifact로 업로드합니다.
코드 실행 권한은 저장소 읽기뿐이며, 자동 Play Store 배포는 하지 않습니다.

기본 빌드는 **임시 debug 서명**입니다. CI 실행마다 키가 달라질 수 있어 기존 설치 위 업데이트가 실패하면 기존 데모 앱을 삭제해야 합니다(앱 데이터도 삭제됨).
팀 배포가 잦아지면 한 번 생성한 별도 팀 키를 아래 Secrets로 등록한 후 수동 `team_signed` 빌드를 사용합니다.

- `TEAM_KEYSTORE_BASE64`
- `TEAM_STORE_PASSWORD`
- `TEAM_KEY_ALIAS`
- `TEAM_KEY_PASSWORD`

키·비밀번호는 Git에 저장하지 않습니다. 키 생성/Secrets 등록은 별도 승인 후 진행하며 현재 자동 생성하지 않았습니다.
서명 정보는 수동 팀 빌드에만 제공하고 PR 빌드에 주입하지 않습니다. 팀용 debug APK는 Play 배포용이 아닙니다.

설치 파일만 배포하면 되며 프론트 호스팅 서버는 필요 없습니다. 백엔드는 별도로 접근 가능한 서버가 필요합니다.
