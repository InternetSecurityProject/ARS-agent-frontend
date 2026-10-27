# C Candidates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare dynamic C candidate selection without live requests.
**Architecture:** Pure choice validation/action factory; opt-in Compose callback through existing B call frame. Owner controls readiness and explicit retry generation.
**Tech Stack:** Existing Kotlin, Compose, JUnit, MockWebServer.
**Spec:** docs/superpowers/specs/2026-10-27-c-candidates-design.md

## Global Constraints

Keep design, A demo, gallery, MainActivity and existing entrypoints unchanged. No live server calls, new dependencies, icons, commits or push. Continue existing feat/compose-mvp checkout with prior B changes intact.

## Review Focus

Expired/malformed timestamps, old options after replacement, duplicate clicks, critical actions with * or #, long lists on small screens. Tests cover each below.

### Task 1: Validated candidates and opt-in rendering

**Files:** model/CandidateChoice.kt, ui/ServerChoiceContent.kt; extend BServerCallScreen.kt, BServerContent.kt and CallScreens.kt; unit/model/CandidateChoiceTest.kt; androidTest/CandidateChoiceUiTest.kt; existing ServerTestTransportTest.kt; integration/status docs.

**Interfaces:** CandidateChoice(request: UserRequest).select(key: String, now: Instant): SessionAction.Select?; CandidateChoiceInput(canSend: Boolean, onSelect: (SessionAction.Select) -> Unit, retryVersion: Int = 0). BServerCallScreen accepts optional choiceInput; absent callback renders read-only choices. Default CallScreen remains demo.

- [x] Write failing unit tests: server-order options; exact request_id/key for numeric, *, #; wrong kind, unknown/duplicate key, empty list, blank ID, invalid/expired deadline return null.
- [x] Run testDebugUnitTest --tests '*CandidateChoiceTest': expect missing implementation failure.
- [x] Implement factory and rerun: expect pass.
- [x] Write UI tests: dynamic lists replace examples; disabled without readiness/handler; critical button only; double-click once; new request replaces and unlocks; retryVersion explicit reset only; live expiration; invalid/empty options; compact long-list scrolling with fixed end.
- [x] Run connectedDebugAndroidTest filtered CandidateChoiceUiTest before rendering change: expect compilation failure/missing server candidates.
- [x] Implement server-only content and optional callback plumbing; expect UI pass.
- [x] Adapt existing local MockWebServer transport test to obtain Select from factory then use existing controller dispatch; assert exact JSON and no second action. No deployment calls.
- [x] Update docs to show callback contract and pending secure/voice limitations.
- [x] Run full JVM/UI tests, lintDebug, assembleDebug and assembleRelease; expect success; record counts.
- [x] Fresh read-only review of C changes; address important findings with RED→GREEN tests; leave local.
