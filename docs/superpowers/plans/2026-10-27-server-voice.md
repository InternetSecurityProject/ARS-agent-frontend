# Server Test Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement inline with TDD. Steps use checkbox syntax.

**Goal:** Agent STT/TTS and receive-only ARS playback in the existing debug server test.
**Architecture:** New debug voice coordinator + Android drivers and read-only audio transport; decorate existing gateway to observe created sessions. Only screen, activity and debug manifest receive minimal hooks.
**Tech Stack:** Kotlin/Compose, Android SpeechRecognizer/TextToSpeech/AudioTrack, existing OkHttp/coroutines.
**Spec:** docs/superpowers/specs/2026-10-27-server-voice-design.md

## Global Constraints

No backend/main/demo/gallery/controller/gateway/API edits. No real calls/deployment HTTP/credentials, new icons/dependencies, commits/push/merge. Existing call confirmation is preserved. Session audio is receive-only. Sensitive/critical input remains manual.

## Review Focus

Late TTS/STT callbacks after expiry, question/session replacement or background must not send. Mixed ARS/TTS/mic must not self-recognize. Audio redirects/external URLs must not leak tokens. Audio faults must not mutate the call lifecycle. Defaults and missing engines must preserve old tests/UI.

### Task 1: Isolated voice and ARS engines

**Create:** debug/servertest/voice/VoiceCoordinator.kt (pure orchestration), AndroidSpeechDevice.kt, ArsAudio.kt (receive-only transport + codec), AndroidArsPlayer.kt, VoiceSessionGateway.kt. Tests: test/servertest/VoiceCoordinatorTest.kt, ArsAudioTest.kt.
**Interfaces:** SpeechDevice.speak(text,onDone), listen(onResult,onError), stop(), close(); VoiceCoordinator.update(ServerTestState), enable(Boolean), listenGoal(callback), listenAnswer(), foreground(Boolean), close(); receive session via decorated create callback. VoiceStatus exposes enabled/listening/speaking/error only, not raw speech/credentials. ArsAudio.listen(session):Flow<ShortArray>.

- [x] RED: TTS→single answer; no voice for secure/critical; stale/duplicate/expired/disconnected results blocked; no passive recording; background/close clears; new-session identity invalidates. G711 fixture values and exact same-origin audio URL validation.
- [x] Run targeted JVM tests; expected unresolved new types then implement minimal engines, re-run green.
- [x] Local WS test sends only server frames, verifies token query without app key, no client audio, external origin/redirect blocked and flow cancellation closes.

### Task 2: Minimal debug integration and verification

**Modify only:** ServerTestActivity.kt, servertest/ServerTestScreen.kt, debug/AndroidManifest.xml. **Create:** voice/ServerVoiceControls.kt. Android tests: ServerVoiceUiTest.kt; existing tests unchanged unless a test selector genuinely needs scoping.
**Consumes:** Task 1 coordinator/drivers; decorate factory without altering controller/gateway. Optional UI hooks default null preserve existing composition tests.

- [x] UI RED: purpose speech populates goal without creating, ordinary answer invokes actual bound request once, sensitive/critical manual only, ARS button toggle reflects runtime and lifecycle. Existing Start confirmation unchanged.
- [x] Attach runtime on server test only; add consent/permission and unsupported-engine fallback, voice controls and goal hook; re-run UI green.
- [x] Full JVM/UI/lint/debug+release builds with synthetic devices/local server. Compare main file hashes and untouched core/backend; one fresh read-only review. Important findings RED→GREEN then full regression. Update docs, keep local. Final: 132 JVM +78 UI, lint0/11 existing warnings, both APK builds successful; no backend/main/core edits, no live calls.
