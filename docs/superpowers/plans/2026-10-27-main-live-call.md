# Main-app live ARS connection implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Implement in this session, task by task; preserve the existing feat/compose-mvp checkout and do not push/merge without a further request.

**Goal:** Connect the basic app's Agent button to real API-backed B/C/D screens, controls and Agent voice without changing the phone demo/gallery or backend.

**Architecture:** A separate `LiveCallOwner` owns real-call entry/confirmation and delegates transport to the existing `ServerTestController`. Shared controller/voice files move from debug to main without duplicating session ownership. `MainPhoneApp` intercepts only live Agent entry; existing `ArsApp`/demo reducer remain the default pure rendering boundary for tests/gallery. Optional live bindings extend the existing call frame rather than replacing its geometry.

**Tech Stack:** Existing Kotlin/Compose, coroutines, OkHttp/MockWebServer, Android STT/TTS/AudioManager, JUnit and Compose tests; no new dependency.

**Spec:** `docs/superpowers/specs/2026-10-27-main-live-call-design.md` (user approved).

## Global constraints

- Existing `feat/compose-mvp`; no backend/Figma changes, new launcher, actual calls or production mutations by the implementer.
- Main Agent entry defaults to real ARS; normal phone remains demo, gallery frozen, legacy Agent demo available in existing top menu.
- Same gradient/header/card/60dp auxiliary controls/72dp end control. Existing key/API rules, TLS origin validation, critical/secure restrictions and no automatic mutation retry remain.
- Key is entered only in explicit real-call confirmation, never saved/logged. Rendering/goal edits/navigation never create sessions. Unknown creation/delivery blocks retry.
- Foreground-only voice/receive-only ARS; no counselor voice, SIM dialer, contacts permissions. Common mic/audio-settings permissions accompany the actual main-app feature.
- All device validation stops before real dial; use synthetic state and local transport for automated session/action checks.

## Review focus

1. Goal debounce/rotation/back/duplicate taps must not create an unconfirmed or second session: owner tests task 1, app tests task 2.
2. Sim/unknown/busy health or uncertain creation cannot silently dial/retry: owner/local-transport tests task 1, error/recovery UI tests task 2.
3. Same request across layout/reconnect/voice/button cannot be answered twice, and secure/critical input cannot become raw key/free speech: controller/owner task 1 and live UI tests task 2.
4. Audio/voice must stop on background/handoff and restore routing without stopping the remote call or claiming unsupported output succeeded: task 3 pure routing and existing voice tests.
5. Legacy A/demo/gallery behavior and secret-free restoration must survive main feature/permission migration: entry/confirmation tests task 2, complete suite and release build task 3.

### Task 1: shared runtime and explicit live-call owner

**Files:** Move `src/debug/.../servertest/{ServerTestController,ServerTestGateway,ServerTestState}.kt` and `voice/*.kt` to equivalent `src/main` paths with packages preserved. Modify main/debug manifests. Create `src/main/.../livecall/LiveCallOwner.kt`, test `src/test/.../livecall/LiveCallOwnerTest.kt`.

**Interfaces:** `LiveCallOwner(scope, factory, monotonicSeconds)` exposes `entry: StateFlow<LiveEntry?>`, `server: ServerTestController`, `begin(number)`, `editGoal(text)`, `prepare()`, `dismissConfirmation()`, `confirm(key)`, `cancelPurpose()`, `returnAfterEnd()`, `close()`. `LiveEntry` exposes number, goal, confirmation and generation; never a key/token. Confirmation captures the current number/goal and is consumed before `server.start(..., RealCall)`.

- [x] Write owner tests: no create on entry/edit/prepare/dismiss; exact confirmed target once; stale/blank input rejected; 010 blocked; failed/uncertain creation cannot accidentally restart; active/ended return guards; elapsed only after first connected status, final duration authoritative.
- [x] Run new tests and observe RED for missing behavior, then share runtime and implement owner. Preserve existing transport semantics and test fixtures.
- [x] Run full `testDebugUnitTest`; expect 0 failures. Add local MockWebServer owner→controller request/action/event evidence. Commit scoped task only after tests pass.

### Task 2: main entry and existing B/C/D frame wiring

**Files:** Create `livecall/MainCallViewModel.kt`, `ui/live/{MainPhoneApp,LivePurposeScreen,LiveSessionScreen,LiveConfirmDialog}.kt`, `ui/{ServerCallControls,ServerAnswerContent}.kt`; modify `MainActivity`, `ArsApp`, `PhoneScreens`, `BServerCallScreen`, `CallScreens`, `BServerContent`, `Components`. Test `androidTest/.../{MainLiveCallUiTest,LiveCallControlsUiTest}.kt` and extend local transport tests.

**Interfaces:** `MainPhoneApp(phoneState,onPhoneAction,owner,voice:ServerVoiceUi?,audio:LiveAudioUi?)` is injectible and has no native microphone/network side effect from rendering. `MainCallViewModel` owns one owner and `VoiceSessionRelay`. Optional `ServerCallControls` supplies live readiness/errors/recovery/voice/control callbacks and elapsed seconds to the existing frame; absent controls preserve the prepared renderer and demo/gallery behavior. Optional `ServerAnswerInput` supplies request-bound free text/STT callbacks without enabling critical/secure speech.

- [x] Write existing-main-entry RED for real/default mode and owner-injected UI RED for purpose→confirmation→B/C/D, secret clearing, cancel, no automatic create, legacy normal-call/demo/gallery, compact layouts, expiry, critical/secure, key taps, recovery and honest end handling.
- [x] Connect live Agent entry from existing keypad/contact/history actions. Reuse top scenario-menu location for real/demo selection, default real, disable mode changes while a live flow exists. Keep A AppState unchanged until explicit return.
- [x] Implement existing B01 geometry with real mic semantics, text pause opening confirmation only once, ephemeral key input and immutable target/goal consent. Bind B/C/D to controller snapshots/results and safe callbacks. Make end/recovery server-authoritative; no fake demo ticks/answers. Bind main FLAG_SECURE for live flow only.
- [x] Run full unit/UI suites with fake speech/local transport; expect no test calls to production. Commit scoped task only after verification.

### Task 3: voice/audio routing, final verification and handoff

**Files:** Add `livecall/{AudioRouting,AndroidAudioRoutes}.kt`; adapt shared `voice/{AndroidSpeechDevice,AndroidArsPlayer,ServerVoiceControls}.kt` for opt-in communication audio and pending purpose recognition. Create `ui/live/MainLiveBindings.kt`, routing/voice binding tests. Update README/integration/status docs.

**Interfaces:** `AudioRouting` uses an injected route device to expose supported/current `AudioRoute`, activate/select/restore, and errors; `AndroidAudioRoutes` implements native earpiece/speaker selection and restores previous routing/mode. `LiveAudioUi` carries native results/callbacks to task 2. Main binding owns foreground lifecycle, routing and the existing `ServerVoiceBinding` for one live entry; server-test defaults remain compatible.

- [x] Write RED tests for unsupported route rejection/restoration, purpose recognition after consent/permission, question→TTS→STT→one answer, handoff/background invalidation, live control callbacks and server-ending without local false completion.
- [x] Implement native routing with verified Android APIs; route TTS/ARS as communication speech only in main live flow. Keep ARS suppression/secure and critical guards unchanged. No microphone starts on ordinary A/gallery entry.
- [x] Run full JVM/UI suites, lint and debug/release APK; inspect manifests for only needed permissions and no secret values. Compare representative A/gallery/live layouts. Perform one fresh read-only whole-change review and fix Critical/Important findings with RED→GREEN.
- [x] Install final APK in emulator; open main entry and verify purpose/confirmation only with no real key/dial. Leave actual dialing and deployed end-to-end speech/audio to user. Document any test flake and unverified device limits. Commit local changes but do not push/merge in this request.
