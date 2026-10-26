# Server Test Mode Implementation Plan

> Execute in this session with superpowers:executing-plans; TDD for each task.

**Goal:** Add an isolated server-connected debug test screen with memory-only key entry.
**Architecture:** A controller owns one private API client/session; a ViewModel preserves running work on rotation; a separate debug Activity renders server state without changing demo timers or gallery.
**Tech Stack:** Kotlin, Compose, coroutines, existing OkHttp API client, JUnit/MockWebServer and Compose UI tests. No new dependencies.
**Spec:** `docs/superpowers/specs/2026-10-26-server-test-design.md` (records the user-approved conversation design).

## Global Constraints

- Fixed `https://ars.mcsmtp.org/v1`; explicit start only after fresh sim/idle health check.
- Key/token never in persisted state, files, logs, BuildConfig or display summaries.
- Existing demo/gallery remain unchanged; no live authenticated calls without the user's key.
- No implicit mutating retries; no treating summary as ended; server input IDs/options are authoritative.
- Retain the existing `feat/compose-mvp` checkout; no new worktree, push or merge.

## Review Focus

1. Double start / response loss must not create duplicate sessions: task 1 tests.
2. Expired/unknown/critical requests must not allow incorrect action types: task 1 and task 2 tests.
3. Disconnect/reconnect snapshot must replace old state and not replay input: task 1 tests.
4. Key clearing, activity exit and configuration changes must not persist credentials or falsely end server sessions: task 2 tests and manual check.
5. Health mode changes, busy server and transport errors must block unsafe start and show actionable text: task 1 tests.

### Task 1: Controller and transport boundary

**Files:** `app/src/debug/java/kr/ac/suwon/arsagent/servertest/{ServerTestState,ServerTestGateway,ServerTestController}.kt`; `app/src/test/java/kr/ac/suwon/arsagent/servertest/ServerTestControllerTest.kt`.
**Interfaces:** controller exposes StateFlow<ServerTestState>, checkConnection/start/respond/sendKeys/reviseGoal/cancel/reconnect/fetchResult/reset/close; gateway adapts existing ArsApiClient, never initiates requests on construction.

- [x] Write behavioral tests for idle/no-network, sim guard, busy/invalid key, duplicate start, uncertain creation, event state, result, inputs, expiry, reconnect and cleanup; establish failing assertions before implementation.
- [x] Implement state/gateway/controller to satisfy the tests; use MockWebServer for real transport boundaries and small event fakes for state races.
- [x] Run `testDebugUnitTest` and inspect complete pass/fail summary. Expected: full unit suite passes.

### Task 2: Debug UI and verification

**Files:** debug `ServerTestActivity.kt`, `servertest/ServerTestScreen.kt`, debug manifest; Android tests `ServerTestUiTest.kt`; `docs/api-integration.md`, `docs/status.md`.
**Interfaces:** screen consumes controller state and explicit callbacks; Activity supplies ViewModel and FLAG_SECURE. Existing entry points untouched.

- [x] Add Compose tests for initial form, missing key, health without authentication claims, server content/input forms and cancel/back dialog; establish RED.
- [x] Add debug launcher and scrollable accessible UI with non-saveable password entry, no fake voice buttons, explicit start/input/send/cancel/reconnect/result buttons.
- [x] Run `testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest assembleRelease`; expected all pass, no new lint errors. Install emulator APK; verify deployed health without entering a key or creating a session.
- [x] Request one focused read-only final review, resolve important findings with RED→GREEN, document real-server verification limits and how to enter the key. Leave changes local (no push requested).

Final evidence: `/private/tmp/ars-server-final.log` succeeded; JVM77 + Android UI33, lint 0 errors/11 pre-existing warnings, debug/release builds. Two review P2 findings were reproduced by three failing tests and fixed in one pass. The Android client displayed deployed health `sim`; no real key or authenticated session was used.
