# API Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버 주소 없이 API v0.3 통신을 로컬 서버로 검증하고 기존 UI를 그대로 유지한다.

**Architecture:** `data/api`의 설정·직렬화 모델·REST/WS 클라이언트는 UI 및 ViewModel과 분리한다. 실제 주소/키를 전달하기 전에는 통신을 시작하지 않으며 MockWebServer만 사용한다.

**Tech Stack:** 기존 Kotlin 2.2.21, OkHttp 4.12.0(기존 Coil 전이 의존성 버전), kotlinx.serialization JSON 1.9.0, JUnit/MockWebServer 4.12.0.

**Spec:** `docs/superpowers/specs/2026-10-25-api-foundation-design.md`

## Global Constraints

- 기존 `AppViewModel`, `PhoneState`, UI 및 `src/debug` 미리보기 동작 유지.
- API v0.3: `POST sessions`, `POST sessions/{id}/actions`, `GET sessions/{id}/result`, `GET health`, WS events.
- HTTPS/WSS 기본, 명시적인 로컬 HTTP 허용만 예외. 생성/조작 자동 재시도 및 HTTP redirect 금지.
- 실제 서버 요청·발신·마이크·오디오 구현·원격 push 없음. 이전 미커밋 변경을 보존하고 작업 결과도 미커밋으로 남긴다.

## Review Focus

- `/v1` 중복, 잘못된 origin, userinfo/query 설정: URL 테스트로 거부 및 정상화 검증.
- 토큰/secure digits: toString·예외 redaction 및 실제 wire payload 검증.
- 202 결과, 409 요청 만료, 깨진 JSON: 서로 다른 결과/안전한 오류 검증.
- 이벤트 단절·unknown event·snapshot 복구: 재구독과 명시적 스트림 실패 검증, 동작 재전송 없음.
- 취소·세션 생성 응답 손실: 코루틴 취소 및 한 번만 요청함 검증.

### Task 1: 모델·코덱·설정

**Files:** `app/src/main/java/kr/ac/suwon/arsagent/data/api/ApiModels.kt`, `ApiConfig.kt`, `EventCodec.kt`; 대응 `app/src/test/.../data/api/*Test.kt`; 루트/app Gradle.

**Interfaces:** `ArsApiConfig(baseUrl, appKey, allowLocalHttp)`, `CreatedSession`, `SessionAction`, `SessionResult`, `SessionEvent`, `EventCodec.decode(text)`.

- [x] 직렬화·설정 테스트를 먼저 작성하고 RED 확인.
- [x] 모델·설정·코덱 구현: snake_case, unknown fields, request options/terminator, snapshot pending/summary, 안전한 문자열 표현.
- [x] `./gradlew testDebugUnitTest` GREEN 확인.

### Task 2: 독립 REST/이벤트 클라이언트

**Files:** `ArsApiClient.kt`, `ApiExceptions.kt`, `ArsApiClientTest.kt`.

**Interfaces:** Task 1 모델을 소비. `suspend createSession(phoneNumber, goal)`, `sendAction(session, action)`, `getResult(session)`, `health()`, `events(session): Flow<SessionEvent>` 제공.

- [x] MockWebServer로 요청/인증/결과/오류/취소/WS 테스트 작성, RED 확인.
- [x] 비동기 취소 가능한 HTTP, 수동 재구독 WS, origin 검증, 응답 크기 제한, 자동 재시도 없는 클라이언트 구현.
- [x] `./gradlew testDebugUnitTest` GREEN 확인.

### Task 3: 연결 가이드·회귀 검증

**Files:** `docs/api-integration.md`, `README.md`, `docs/status.md`.

**Interfaces:** Task 2 생성자와 메서드의 실제 사용 예시, 주소·키 전달 및 후속 UI 연결 경계를 문서화.

- [x] 설정·인증·미확정 계약·오디오 제외·snapshot 복구·중복 발신 방지를 설명.
- [x] `testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` 전체 검증.
- [x] 새 통신 코드 독립 리뷰, 기존 데모/미리보기 미변경 확인. 실제 주소 없이 연동 완료라고 표현하지 않음.
