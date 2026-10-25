# 백엔드 연동 준비

## 현재 상태

API v0.3 계약에 맞춘 독립 통신 코드와 로컬 모의 서버 테스트를 추가했다. **앱 화면에는 아직 연결하지 않았다.** 기존 UI 데모 타이머, 예시 문구, 시나리오, A/B/C/D 고정 미리보기는 그대로다. 앱 실행이나 API 클라이언트 생성만으로 네트워크 요청/전화 발신이 발생하지 않는다.

근거: 백엔드 `docs/api-main-v0.3.yaml`, `docs/ARS자동화-API명세서-v0.3.md`, `app.py`, `session.py` (로컬 확인 commit `3887bc4`). 이 명세는 초안이며 실서버 검증 전이다.

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

주소와 키를 받아도 **화면 연동까지 끝나는 것은 아니다.** 다음 작업에서 기존 UI 타이머 대신 서버 상태를 반영하고, 결과 조회·오류 표시·재연결·요청 만료 및 입력 동작을 연결해야 한다.

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
