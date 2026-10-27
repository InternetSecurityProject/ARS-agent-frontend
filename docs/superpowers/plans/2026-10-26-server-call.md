# B Server Call Implementation Plan

> Historical, unexecuted proposal: the user declined the extra launcher. Do not execute this plan; see `2026-10-27-b-data.md` for the subsequently approved preparation work.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Render B call progress from API v0.3 without changing A/demo/gallery.
**Architecture:** A pure presenter consumes the existing server controller state; a debug Activity renders the B-style call UI and reuses safe setup/request/exit controls. Keep server events separate from demo timers.
**Tech Stack:** Existing Kotlin/Compose, coroutines, OkHttp, JUnit/MockWebServer, Compose UI tests. No new dependencies.
**Spec:** `docs/superpowers/specs/2026-10-26-server-call-design.md`.

## Global Constraints

- Fixed `https://ars.mcsmtp.org/v1`; sim/idle recheck and explicit start; no real calls.
- Preserve demo/gallery/diagnostic entry points and their behavior. Keep the existing `feat/compose-mvp` checkout per the previous plan; no new worktree, push or merge.
- Memory-only credentials; FLAG_SECURE; no secrets in observable/saved state or logs.
- Server requests/options authoritative; no auto-six-digit submission, implicit mutating retries, false terminal states or fake audio support.

## Review Focus

1. Summary/handoff mistaken for ended: mapping and UI tests in tasks 1/2.
2. Disconnected or unknown server status hidden behind canned success: mapping and UI tests in tasks 1/2.
3. Final REST summary/path/duration contradicted by stale snapshot: task 1 and transport tests in task 2.
4. Pending input/failed cancellation strands user: reuse current tested controls, task 2 UI regression tests.
5. Rotation/small window/credentials: task 2 UI and release-manifest checks; existing key restoration tests retained.

### Task 1: Server call presentation

**Files:** Create debug `servercall/ServerCallPresentation.kt`; test `app/src/test/java/kr/ac/suwon/arsagent/servercall/ServerCallPresentationTest.kt`.
**Interfaces:** `ServerTestState.toCallPresentation(): ServerCallPresentation` exposes title, message, goal, caption, path labels, summary/keyInfo, terminal/server-duration and pending request. It never mutates controller state or opens network connections.

- [ ] Write tests with literal API fixture content for dialing/listening/selecting/result, clarification/secure, handoff, unknown status, disconnected and final REST precedence.
- [ ] Run `testDebugUnitTest --tests '*ServerCallPresentationTest'`; expect RED for missing presentation.
- [ ] Implement the pure presenter, preserving distinct workflow and terminal states; rerun full unit suite, expect PASS.

### Task 2: B screen, entry point and end-to-end verification

**Files:** Create debug `ServerCallActivity.kt`, `servercall/ServerCallScreen.kt`; minimally expose existing safe setup/request/exit composables in `servertest/ServerTestScreen.kt`; update debug manifest. Create Android `ServerCallUiTest.kt`; extend transport tests; update integration/status docs.
**Interfaces:** `ServerCallScreen(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit, onExit: () -> Unit)` consumes task 1 presentation and the existing controller. Activity owns the controller through a ViewModel and FLAG_SECURE.

- [ ] Write Compose tests for server content/no weather, distinct summary-vs-ended, disabled audio, pending requests, failed cancel/back, setup/key clearing and constrained height. Add a real local transport test whose events drive the presentation.
- [ ] Run new tests and observe RED before UI implementation.
- [ ] Implement B-style call frame, shared setup/request/exit controls, explicit recovery/result/reset controls, debug launcher. Do not implement audio or a new C workflow.
- [ ] Run `testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest assembleRelease`; expect no test failures/new lint errors. Verify entry point excluded from release. Update usage and verification limits.
- [ ] Perform focused final review, resolve important findings with RED→GREEN, leave work local. If a skill requires independent review, use a read-only reviewer only after verifying the final diff.
