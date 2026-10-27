# 백엔드 연동 준비

## 현재 상태

API v0.3 계약의 통신 코드에 **별도 debug 서버 테스트 화면**을 연결했다. 기존 UI 데모 타이머, 예시 문구, 시나리오, A/B/C/D 고정 미리보기는 그대로다. 기존 앱/미리보기 실행이나 API 클라이언트 생성만으로 네트워크 요청/전화 발신이 발생하지 않는다. 서버 테스트도 버튼을 누르기 전에는 요청하지 않는다.

### 서버 테스트 음성 대화·ARS 듣기 (2026-10-27)

사용자 승인 범위는 **Agent 질문·진행 안내 TTS, 사용자 목적·일반 답변 STT, 수신 전용 ARS 듣기**다. 상담원 음성 통화는 포함하지 않는다. 추가 debug 파일과 기존 서버 테스트의 화면/진입점/권한 연결만 변경하며 main·데모·갤러리·기존 컨트롤러/API·백엔드는 그대로 유지한다.

1. **음성 대화 켜기**에서 음성 인식 서비스가 외부 서버로 음성을 보낼 수 있다는 안내를 확인하고 마이크 권한을 허용한다. 지원되는 기기는 Android 온디바이스 인식을 우선하고, 아니면 시스템 서비스를 사용한다. 녹음 파일은 만들지 않는다. 한국어 엔진/권한/네트워크 문제가 있으면 화면에 오류를 표시하고 기존 글자 입력을 사용한다.
2. **목적 말하기**는 확정 인식 결과를 기존 목적 입력칸에 채운다. 인식만으로 세션 생성/발신하지 않으며 기존 실통화 확인창과 **실제 발신** 버튼을 유지한다.
3. 통화 중에는 Agent의 핵심 상태 전환과 일반 질문을 읽는다. 일반 질문을 읽은 뒤 단발 음성 인식을 시작하고, 현재 요청이 유효할 때 `answer`를 한 번 전송한다. **말로 답하기**로 다시 시도할 수 있다. 인식 실패·기한 만료·연결 손실은 자동 반복/재전송하지 않는다. 전체 ARS 자막을 TTS로 중복 낭독하지 않는다.
4. `critical_action`은 기존 후보 버튼만, `secure_input`은 기존 키패드와 **입력 완료**만 제공한다. 해당 구간은 안전한 일반 문장으로 안내하며 사용자 음성 입력을 시작하지 않는다.
5. **ARS 소리 켜기/끄기**는 현재 세션의 `audio_url`에 세션 token으로 연결하여 G.711 μ-law 8kHz mono 음성을 디코딩·재생한다. 앱 키나 사용자 마이크 음성은 이 소켓에 보내지 않는다. origin/경로/스킴을 검증하고 redirect와 자동 재시도는 하지 않는다. 오류가 나도 기존 통화를 취소/재발신하지 않는다.
6. Agent가 말하거나 답변을 듣는 동안 ARS 재생을 억제하고 밀린 음성은 쌓아두지 않는다. **음성 입력·안내 멈추기**는 음성 턴만 멈추며 통화를 종료하지 않는다. 음성 대화와 ARS 듣기는 각각 끌 수 있다.
7. 상담원 연결 상태에서는 ARS 재생/마이크 답변을 중단하며, 상담원과의 음성 통화를 지원한다고 표시하지 않는다. 통화 자체는 사용자가 종료한다. 백그라운드/화면 해제에서는 모든 음성 입출력을 멈추고 복귀 후 사용자가 다시 켠다.

STT/TTS 정확도·음질, 단말 오디오 출력, 배포 오디오 URL과 프록시의 WebSocket 지원은 사용자의 실기기 테스트가 필요하다. 짧은 ARS 대기 시간을 프론트가 늘리지는 못하며, 음성 인식 도중 기한이 지나면 답변을 보내지 않는다. 후보 버튼은 계속 제공한다. 검증 중에는 합성 음성 장치와 로컬 WS만 사용하며 실제 발신/녹음/실제 키/배포 API는 사용하지 않는다.

Android 근거: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

### B 화면 데이터 연결 준비 (2026-10-27)

`BCallData.from(snapshot, result)`와 `BServerCallScreen`을 추가했다. 기존 B 통화 틀의 위치·색·크기를 재사용하며, 서버 자막·선택 경로·안내 결과·최종 서버 소요 시간을 표시한다. **현재 MainActivity, UI 데모, 갤러리 및 서버 테스트 진입점에는 연결하지 않았다.** 추가 Activity/아이콘/모드 스위치가 없고 렌더링만으로 네트워크를 사용하지 않는다.

향후 세션 소유자가 기존 컨트롤러의 최신 `snapshot`과 `result`를 전달한다. 세션별 전화번호·이벤트 수명, 인증·오류/연결상태 안내, 중복 조작 제한, 취소 실패 처리는 세션 소유자 책임이다. 예를 들어 기존 서버 컨트롤러를 사용하는 호스트는 아래처럼 연결할 수 있지만, 현재 앱의 실행 경로에 추가한 코드는 아니다.

```kotlin
BServerCallScreen(
    number = selectedNumber,
    snapshot = serverState.snapshot,
    result = serverState.result,
    onEnd = controller::cancel,
    onBack = onReturnToPhone,
)
```

- 최신 snapshot에서 매번 다시 계산하므로 재연결 후 오래된 자막·경로가 합쳐지지 않는다. 최종 REST 결과의 null/빈 값도 그대로 존중한다.
- summary/agent_done/상담원 연결은 통화 종료가 아니다. ended 또는 완료된 REST 결과만 종료로 표시한다. 실시간 통화 시작 시각 계약이 없으므로 가짜 시계는 돌리지 않고 최종 결과가 제공한 소요 시간만 표시한다.
- C 후보 선택과 개인정보 입력은 아래 선택적 콜백까지 준비했다. 음성 답변 연결은 아직 없으며 서버 개인정보 입력에는 데모의 고정 6자리 규칙을 적용하지 않는다.
- 서버용 보조 버튼 3개는 위치를 유지하되 실제 키패드/오디오 연결 전에는 비활성이다. 종료/돌아가기는 호스트 콜백만 호출한다.
- 로컬 REST/WebSocket/Compose 테스트는 가상 데이터만 사용한다. 이 준비를 실제 배포 서버 인증·발신·오디오 검증 완료로 보지 않는다.

근거: 백엔드 `docs/api-main-v0.3.yaml`, `docs/ARS자동화-API명세서-v0.3.md`, `app.py`, `session.py` (로컬 확인 commit `3887bc4`). 이 명세는 초안이며 실서버 검증 전이다.

### C 후보 선택 연결 준비 (2026-10-27)

`pending_request.kind == clarification`이면 서버 질문과 `options`를 원래 순서·이름·키로 표시한다. 기존 후보 행 디자인을 재사용하고 숫자·`*`·`#`를 모두 업무 선택지로 취급한다. `critical_action`도 버튼만 제공하며 confidence 수치는 노출하지 않는다. 후보가 없으면 예시 메뉴를 대신 채우지 않는다.

`BServerCallScreen`의 `choiceInput`은 선택적이다. 제공하지 않으면 후보가 보이지만 비활성이다. 아래는 **향후 호스트 연결 예시이며 현재 실행 경로에는 추가하지 않았다.** 콜백을 넘긴 경우에도 화면이 직접 HTTP를 수행하지 않는다.

```kotlin
// 기존 BServerCallScreen 인자에 추가할 수 있는 예시
choiceInput = CandidateChoiceInput(
    canSend = serverState.canSend,
    onSelect = { action ->
        controller.respond(action.requestId, action.key, selection = true)
    },
)
```

- 콜백 결과는 `SessionAction.Select(requestId, key)`다. 기존 API 클라이언트가 `{"type":"select","request_id":"…","key":"…"}`로 직렬화한다. 일반 `key`·음성 `answer`로 변환하지 않는다.
- 최신 상태가 `awaiting_user`이고 호스트가 송신 가능하다고 제공한 경우에만 활성화한다. 현재 후보에 없는 키·중복 키·빈 ID·만료/잘못된 기한은 선택하지 못한다. 화면 표시 중에도 만료를 반영하고 클릭 시 다시 확인한다.
- 같은 요청은 한 번만 콜백을 호출한다. 서버 응답 전에는 다음 화면으로 진행하거나 성공 처리하지 않는다. 최신 요청 ID로 교체되면 새 후보로 바뀌며, 동일 ID의 후보 갱신만으로는 중복 송신 잠금을 해제하지 않는다.
- 선택 기록은 진행 카드가 아닌 통화 화면 전체에 유지하므로 화면 높이 변경이나 요청/콜백 임시 제거 후 복원으로 잠금이 풀리지 않는다.
- 명확히 미접수된 오류 후 수동 재시도가 필요한 경우만 호스트가 `retryVersion`을 올릴 수 있다. 시간 초과·연결 손실 등 처리 여부 불명인 경우 올리지 않고 스냅샷으로 먼저 확인한다. 호스트는 세션별 컴포지션 수명을 구분하고 최신 요청/상태를 송신 직전에도 검증해야 한다(기존 controller.respond가 수행).
- MainActivity·A 데모·미리보기·기존 서버 테스트 진입점은 변경하지 않았다. 실제 배포 서버 호출, 발신, 인증키 입력/저장은 이번 작업에 없다. 로컬 모의 서버에서만 전송 형식을 검사한다.

### C 개인정보 입력 연결 준비 (2026-10-27)

사용자 승인에 따라 개인정보 입력은 **모두 ‘입력 완료’** 방식으로 통일했다. 데모/미리보기에도 버튼이 보이고, 데모는 6자리만 입력해서는 진행하지 않는다. 일반 통화 중 수동 키패드의 즉시 입력 동작은 바꾸지 않았다. debug 서버 테스트 화면도 기존 제출 버튼 이름을 ‘입력 완료’로 통일했다.

서버용 화면은 기존 카드 밖 원형 키패드와 고정 종료 위치를 재사용하고, 완료 버튼을 종료 위에 고정한다. `secure_input` 요청의 `label`과 `message`(없으면 `ars_prompt`)를 사용한다. 기대 자리 수 필드가 없으므로 안내 문자열에서 길이를 추측하거나 6자리 자동 제출하지 않는다. API의 허용 범위인 1~20자만 적용한다. 서버가 지정한 단일 종료 키는 비활성화하고, 앱이 종료 기호를 붙이지 않는다.

아래는 **향후 호스트 연결 예시**이며 MainActivity/데모/미리보기 실행 경로에는 연결하지 않았다. 콜백만 전달하며 화면이 HTTP를 실행하지 않는다.

```kotlin
// BServerCallScreen에 선택적으로 전달
secureInput = SecureInputInput(
    canSend = serverState.canSend,
    onSubmit = { action ->
        controller.respond(action.requestId, action.digits)
    },
)
```

- 입력은 마스킹하고 명시적 완료 전까지 요청을 만들지 않는다. 완료 시 `SessionAction.SecureInput`을 만들어 기존 클라이언트의 `{"type":"secure_input","request_id":"…","digits":"…"}` 계약으로 연결 가능하다. 일반 key/answer로 보내지 않는다.
- CharArray 버퍼는 제출·만료·요청 교체·종료·연결 불가·백그라운드·컴포지션 해제 시 지운다. 원문은 saved state/로그/파일에 저장하지 않는다. 복원 후 재입력이 필요하다. 전달용 immutable String과 네트워크 내부 버퍼의 암호학적 완전 삭제까지 보장하지 않는다.
- 완료 후 같은 요청의 중복 클릭/화면 크기 변경/요청 임시 제거로 재전달하지 않는다. 호스트는 화면 수명을 세션별로 구분하고 현재 요청을 송신 직전 재검사해야 한다. 불확실한 실패는 자동 재시도하지 않는다. 명확한 미접수 오류에서만 호스트가 retryVersion을 증가시키며 입력값은 복원하지 않고 다시 입력받는다.
- 이 작업은 로컬 가상 데이터/모의 서버 검증만 한다. 실제 서버 인증·전화·오디오·개인정보 전송을 검증한 것은 아니다.

## 서버 테스트 모드 사용법

1. debug APK를 설치하고 앱 목록에서 **ARS 서버 테스트**를 연다. 기존 **ARS Agent 데모**, **ARS 화면 미리보기**는 독립적으로 유지된다. release APK에는 서버 테스트 화면이 없다.
2. 기본 주소는 `https://ars.mcsmtp.org/v1`로 고정된다. **연결 확인**은 키 없이 health만 읽는다. `sim`은 모의 테스트, `clawops`는 실통화 확인 흐름을 표시한다. 앱이 서버 모드를 변경하지 않는다. 다른 활성 세션이나 알 수 없는 backend에서는 시작할 수 없다. 연결 확인 성공은 키 인증 성공이 아니다.
3. **API 키** 칸에 받은 개발용 키를 직접 붙여 넣는다. 채팅·소스·GitHub·local.properties·BuildConfig에는 넣지 않는다. 비밀번호처럼 가리며, 테스트 시작 시 입력칸에서 지운다. 클라이언트가 사용하는 키와 세션 토큰은 화면 수명 동안 메모리에만 있으며 화면 종료 시 참조와 연결을 해제한다. 키 입력은 `rememberSaveable`/SavedStateHandle/파일/Preferences로 저장하지 않는다. 화면 복원 테스트로 검증했다.
4. sim에서만 합성 예시 `07000000000`을 채운다. 실통화 번호는 빈칸에서 직접 입력한다. **서버 예시 목적 선택**에서 현재 서버가 제공하는 문장을 선택하거나 원하는 업무를 글로 입력한다.
5. sim에서는 **테스트 시작**, clawops에서는 **실통화 준비 → 번호·목적·실제 발신 경고 확인 → 실제 발신** 순서다. 준비/취소/복원만으로는 생성하지 않는다. 확인은 매번 필요하며 보관하지 않는다. 생성 직전 backend와 활성 세션을 재조회하며, 확인한 모드와 달라지면 차단한다. 이때 키를 인증하고 생성 요청은 한 번만 보낸다. 공유 서버의 다른 세션을 종료하지 않는다. **health 조회와 POST는 원자적이지 않으므로 테스트 도중 서버 모드를 변경하지 않아야 한다.** 실통화는 서버의 070 전화로 발신되며 통화 시간·요금이 발생할 수 있다.
6. 실시간 서버 상태/입력 요청/안내 결과를 확인한다. **진행 기록 보기**에서 이동 경로와 최근 안내를 본다. 재질문은 서버의 실제 선택지 또는 답변으로, 중요한 선택은 선택지로만 응답한다. 민감정보는 전용 입력창에서 명시적으로 보낸다. 길이는 1~20자이며 고정 6자리나 임의 종료키를 붙이지 않는다.
7. 필요하면 **수동 조작**에서 1~4자 키를 보낸다. 민감정보 요청 중에는 일반 키 전송을 제공하지 않는다. 목적 수정은 서버 계약대로 첫 메뉴 선택 전까지만 제공한다.
8. 연결이 끊기면 **상태 다시 연결**을 누른다. 같은 세션의 새 snapshot으로 상태를 교체하며 입력/발신 요청은 자동 재전송하지 않는다. 생성 응답을 잃으면 재시작을 차단하고 서버 담당자에게 확인하도록 안내한다.
9. **테스트 종료**는 서버 Cancel을 보낸다. 최종 결과에서 서버 기준 소요 시간을 확인한다. summary/agent_done/상담원 연계 상태만으로 종료라고 표시하지 않는다. 결과가 202라면 **결과 조회**로 다시 확인한다. 종료 이벤트를 놓쳐도 완료된 REST 결과로 종료 상태를 복구한다.
10. 진행 중 **닫기/뒤로가기**는 먼저 종료를 요청하도록 확인한다. 실패해도 종료했다고 표시하지 않는다. 오류 시 경고를 읽고 **화면만 닫기**를 선택할 수 있지만 서버 세션이 남을 수 있다. 강제 종료도 서버 Cancel을 보장하지 않는다. 키가 필요하면 다시 입력한다.

보안상 서버 테스트 Activity는 화면 캡처/최근 앱 미리보기를 막는 `FLAG_SECURE`를 사용한다. Android IME/운영체제 자체의 보관 정책이나 immutable String의 즉시 메모리 완전 삭제까지 보장하는 것은 아니다. 마이크는 위의 Agent용 STT에만 사용하고 ARS는 수신 전용이며 상담원과의 음성 통화는 지원하지 않는다.

2026-10-26 health는 sim/none/MockInterpreter였으며, 2026-10-27 진단에서는 clawops/whisper/GeminiInterpreter로 확인했다. **사용자의 실제 키는 받지 않았으므로 배포 서버에서 인증·세션 생성·WSS·종료의 실증 검증은 아직 하지 않았다. 실통화 확인 기능 구현 중에도 실제 발신은 하지 않는다.** 두 모드의 REST/WebSocket 경계는 로컬 MockWebServer 테스트로 검증한다. 운영 키를 APK에 하드코딩하지 않는다.

## 준비된 인터페이스

패키지: `kr.ac.suwon.arsagent.data.api`

| 호출 | 계약 |
| --- | --- |
| `health()` | `GET /v1/health`, 인증 헤더 없음 |
| `createSession(phoneNumber, goal)` | `POST /v1/sessions`, X-App-Key. **서버가 즉시 발신한다.** |
| `sendAction(session, action)` | `POST .../actions`, 세션 Bearer 토큰 |
| `getResult(session)` | `GET .../result`, 202는 `InProgress`, 200은 `Completed` |
| `events(session)` | 세션의 events URL로 연결하는 cold Flow. 토큰은 query, 앱 키는 전송하지 않음 |
| `close()` | 소켓·진행 중 HTTP와 클라이언트 소유 리소스 해제 |

액션은 `Answer(requestId, text)`, `Select(requestId, key)`, `Key(key)`, `SecureInput(requestId, digits)`, `ReviseGoal(text)`, `Cancel`이다. `secure_input`의 종료키는 서버가 붙이므로 앱이 임의로 `#`를 추가하지 않는다.

이벤트: snapshot, status_changed, caption, path_updated, request, input_rejected, summary, ended, error. 미래 이벤트는 `SessionEvent.Unknown`으로 구분한다. 새로운 필드가 추가되어도 기존 필드 해석은 유지한다. 상태 값은 원문 문자열로 보존하여 새 상태를 임의로 종료 상태로 취급하지 않는다.

## 주소가 나오면

백엔드 담당자에게 **개발용 HTTPS 주소, 개발용 앱 키, 시뮬레이터 모드 여부, 외부 WSS 주소**를 받는다. 운영/실통화 환경에 테스트 세션을 함부로 만들지 않는다.

설정 예시(아직 앱에서 호출하지 않는 사용 예시):

```kotlin
// verifiedBaseUrl / developmentAppKey는 실제 확인한 개발 환경 설정에서 전달한다.
val api = ArsApiClient(ArsApiConfig(
    baseUrl = verifiedBaseUrl, // https://host 또는 https://host/v1[/]
    appKey = developmentAppKey,
))

// suspend 문맥에서 실행. health만으로는 전화가 걸리지 않는다.
try {
    val health = api.health()
    // health.backend == "sim" 등 환경을 먼저 확인한다.
} finally {
    api.close()
}
```

테스트/향후 화면 연결 예시:

```kotlin
// 반드시 시뮬레이터로 확인한 환경에서만 실행한다. 생성 요청 자체가 발신이다.
val session = api.createSession(phoneNumber = "131", goal = "수원 날씨 알려줘")
api.events(session).collect { event ->
    // 다음 작업에서 snapshot/자막/경로/요청을 화면 상태에 반영한다.
}
// 입력 요청의 실제 request_id와 실제 선택지를 사용한다.
api.sendAction(session, SessionAction.Select(requestId = pending.requestId, key = chosen.key))
// Cancel은 실제 통화를 끝내는 명령이다. close()는 네트워크 자원 해제이며 통화 종료 명령이 아니다.
api.sendAction(session, SessionAction.Cancel)
```

위 코드는 독립적인 호출 예시다. `collect`는 통화 중 계속 실행되므로 실제 화면 연결 시 수신 Job과 사용자 조작 Job을 별도로 관리하고, 종료 후 클라이언트를 해제해야 한다. 전화번호 `131`도 실환경에서는 실제 기관 번호이므로 실서버에 복사 실행하지 않는다.

기존 UI 데모 자체를 서버 상태로 바꾸지는 않았다. 새 debug 서버 테스트 화면에서만 결과 조회·오류 표시·재연결·요청 만료 및 입력 동작을 연결한다. 실제 전화앱의 음성/통화 흐름을 이 화면과 동일하다고 간주하지 않는다.

## 로컬 테스트 환경

- 로컬 MockWebServer 테스트는 실제 백엔드를 띄우지 않는다: `./gradlew testDebugUnitTest`.
- 추후 Mac의 시뮬레이터 백엔드를 Android 에뮬레이터에서 연결할 때는 보통 `http://10.0.2.2:8000`을 사용한다. `allowLocalHttp = true`를 명시해야 한다.
- debug APK는 `localhost`, `127.0.0.1`, `10.0.2.2`에만 cleartext를 허용한다. 원격 HTTP를 전역 허용하거나 TLS 검증을 끄지 않았다.
- release에서는 HTTPS/WSS를 사용한다. root 주소와 `/v1` 주소를 모두 받아 `/v1/`로 정규화한다. 그 외의 path prefix는 현재 계약에 없다.
- 설정의 IPv6 loopback 허용은 JVM 환경용이다. Android debug XML은 위 세 주소만 허용한다.

## 오류·재연결·중복 발신

- `ApiHttpException`: HTTP 상태와 서버 error code. 401 인증, 404 세션 없음, 409 만료/상태 충돌·민감정보 전송 방식 충돌 등을 구분할 수 있다.
- `ApiProtocolException`: 필수 필드 누락, 깨진 JSON, 1MiB 초과 응답, 잘못된 WebSocket 주소/포맷.
- `ApiConnectionException`: 네트워크 실패. **서버에서 요청이 처리되었는지는 불명확할 수 있다.**
- `ApiStreamException`: 이벤트 단절 또는 소비 지연. 수신 Flow를 다시 수집하면 서버가 새 snapshot을 전송한다. 자동 재연결은 이번에 추가하지 않았다. `ended` 이벤트 또는 `status=ended`인 snapshot을 전달한 뒤에는 Flow가 정상 완료된다.
- 생성/조작은 one-shot HTTP body를 사용해 `503 Retry-After: 0` 같은 라이브러리의 상태 기반 재시도도 막는다. 생성 응답을 잃었더라도 서버에서는 이미 전화가 걸렸을 수 있으므로 같은 요청을 즉시 다시 보내지 않는다. 백엔드에 idempotency/reconciliation 계약이 없으며 별도 합의가 필요하다.
- 재연결 시 snapshot은 이전 상태를 **대체**해야 한다. event_id가 없으므로 누락 이벤트 재생이나 단순 이벤트 이어붙이기를 가정하지 않는다. 사용자의 이전 입력도 자동 재전송하지 않는다.
- summary만으로 통화를 종료하지 않는다. `agent_done`은 업무 완료지만 통화는 유지되는 상태다. `connected_to_agent` 역시 실제 통화 종료와 다르다.
- 이벤트 소비자가 밀리면 조용히 요청/종료 이벤트를 버리지 않고 스트림 오류로 알린다. 이후 새 snapshot으로 복구하는 처리는 화면 연결 단계에 필요하다.

## 보안 경계

- 앱 키·토큰을 소스/BuildConfig/예시 설정 파일에 넣지 않았다. 토큰 URL, 요청/응답 본문을 찍는 로깅 인터셉터도 없다. 예외 메시지에 원문 본문이나 네트워크 예외의 URL을 붙이지 않는다.
- WebSocket은 설정한 HTTPS origin과 같은 host/port/scheme, 해당 세션 events path만 허용한다. 다른 origin에 토큰을 보내거나 HTTPS에서 WS로 낮추지 않는다. 프록시가 잘못된 `ws://`를 반환하면 서버의 외부 scheme 설정을 고쳐야 한다.
- HTTP redirect도 따라가지 않는다. 잘못된 host로 인증키가 전달되는 것을 막고, 발신 명령의 중복 실행 위험을 줄인다.
- 단말에 배포된 앱 키는 추출 가능하다. 개발용 제한·회전 가능한 키를 사용하고, 운영 인증 정책은 백엔드와 별도 설계해야 한다.
- 민감정보는 `SecureInput` 전용 전송 모델을 쓰고 문자열 표현은 가린다. **JVM immutable String·HTTP 내부 버퍼의 즉시 완전 삭제는 보장하지 않는다.** 저장·로깅하지 않는 것과 메모리의 암호학적 삭제는 다르다.
- WebSocket 토큰 query는 서버/프록시 접근 로그에도 남지 않도록 별도 마스킹이 필요하다.

## 다음에 확인할 계약

1. 민감정보의 정확한 입력 길이·완료 판단 필드가 없다. 기존 화면의 6자리 예시는 데모 전용이며 모든 요청에 적용하면 안 된다.
2. 재질문 선택지는 서버의 `options`를 사용해야 한다. 결제/계약 등 `critical_action`에서는 음성 답변 대신 명시적 선택을 요구한다.
3. 실제 음성 입력 STT와 안내 TTS, G.711 μ-law 오디오·권한·에코 제거·백그라운드 통화는 이번 범위 밖이다. `audio_url`은 모델로만 받는다.
4. 상담원 연결 시 AI 종료와 실제 상담원 음성 연결은 별개다. 현재 UI를 임의로 바꾸지 않았고 실제 음성 중계 지원 여부를 확인해야 한다.
5. 서버 주소/키·환경, 프록시 WSS 생성, 오류/요청 필드, 실제 API 버전이 배포 시점에 달라졌는지 확인한다.
