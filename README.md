# ARS Agent Android

인터넷보안프로젝트 · 먹보들. Kotlin + Jetpack Compose 기반 전화앱 UI.

> **일반 전화(A)·예시 연락처/통화 목록·화면 미리보기는 UI 데모**입니다. 기본 앱의 **ARS Agent 전화는 실제 서버 발신**입니다. 두 경로를 구분해 사용하세요.

기본 앱 **ARS Agent**에서 번호 → **ARS Agent 전화** → 목적 입력 → 확인창에 API 키 입력 → **실제 발신** 순서로 사용합니다. 확인 전에는 전화가 걸리지 않습니다. `https://ars.mcsmtp.org/v1`의 `clawops` 서버가 발신하므로 시간·요금이 발생할 수 있습니다. 키는 저장하지 않으며 확인창을 닫거나 앱을 재시작하면 다시 입력해야 합니다.

B/C/D에는 서버 상태·경로·자막·결과와 후보 선택/답변/개인정보 입력을 연결했습니다. Agent 질문·진행 안내 TTS, 목적·일반 답변 STT, ARS 소리 켜기/끄기와 휴대전화/스피커 선택도 연결했습니다. 상담원 음성 통화는 지원하지 않습니다. **실제 발신·배포 서버의 인증/음성 종단간 확인은 사용자 테스트가 필요**합니다. 개발 검증은 가짜 음성 장치·로컬 모의 서버·발신 없는 에뮬레이터만 사용합니다. [연동 가이드](docs/api-integration.md)를 참고하세요.

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

1. 상단 `실제 ARS` 메뉴에서 `UI 데모`를 고른 뒤 날씨·후보 선택·추가 답변·민감정보·상담원 시나리오 선택. 기본값은 실제 ARS이며, 데모를 선택하면 서버에 발신하지 않습니다.
2. 통화/연락처의 예시 항목 상세 또는 키패드에서 번호 입력.
3. `010`은 일반 전화만, 나머지 유효 번호는 일반/Agent 버튼 표시. 실제 ARS 지원 여부 판별은 아닙니다.
4. Agent 목적 입력은 입력이 멈춘 후 자동 진행. 마이크 버튼은 **예시 문장 입력**이며 녹음하지 않습니다.
5. 민감정보 화면은 데모 6자리 입력 완료 후 즉시 폐기. 수동 키패드는 키를 바로 반영합니다.
6. 오디오·음소거·보류는 UI 상태만 변경됩니다. 실제 오디오 경로 변경이 아닙니다.

## 원하는 화면 바로 보기 (debug APK)

- 앱 목록에서 **ARS 화면 미리보기**를 열고 상단 A(일반 전화) / B(Agent 진행) / C(추가 입력) / D(상담원 연결) 탭에서 화면을 선택합니다. 목록을 다시 열면 현재 화면의 그룹이 선택됩니다.
- 화면 위 **화면 선택** 또는 Android 뒤로가기로 다시 목록을 엽니다.
- 010/070 키패드·연락처/통화 상세, 일반 통화/종료, Agent 진행·입력·오디오·상담원 대기를 바로 확인할 수 있습니다.
- 자동 전환·시간 흐름·입력/통화 버튼이 멈춘 **고정 미리보기**입니다. 기본 앱의 기존 Agent 데모는 상단 메뉴에서 선택합니다. 별도 **ARS 서버 테스트** 진단 도구도 유지합니다.
- 미리보기 런처와 예시 상태는 `src/debug/`에만 포함되며 release APK에는 들어가지 않습니다.

## 구조

```
app/src/main/java/kr/ac/suwon/arsagent/
  data/api/    REST/이벤트 WS 클라이언트, 요청·응답 모델
  livecall/    실제 발신 확인·단일 세션 소유자·네이티브 오디오 라우팅
  servertest/  기본 앱과 debug 진단 도구가 공유하는 통신/음성 런타임
  model/       순수 Kotlin 상태·전환 규칙·예시 데이터
  ui/          화면, 공통 컨트롤, 테마
  AppViewModel 데모 타이밍과 취소 처리
```

- [Figma](https://www.figma.com/design/ul4FvS9IStRhfdoSzm70Ri/ARS-Agent): A/B/C/D01, 현재 C04는 수동 키패드.
- 원본 SVG는 `assets/figma/`. 네트워크로 이미지를 불러오지 않습니다.
- Noto Sans KR은 SIL OFL로 포함. SF Pro와 iOS 시스템 상태 표시줄은 재배포/모사하지 않습니다.
- `AppViewModel`은 A/Agent 데모 전용으로 유지합니다. `MainCallViewModel`/`LiveCallOwner`가 실제 ARS를 독립적으로 소유하고, 기본 앱과 debug 서버 테스트는 같은 통신·음성 구현을 재사용합니다. 기본 앱/미리보기 실행만으로 네트워크 요청·마이크 권한 요청·발신이 발생하지 않습니다.

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
