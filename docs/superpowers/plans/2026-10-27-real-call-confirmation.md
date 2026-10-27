# Real Call Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable debug real-call testing only after explicit confirmation, without performing a real call ourselves.
**Architecture:** Health-derived UI mode, ephemeral target-bound confirmation, typed mode on Start; controller rechecks backend and busy state before existing create path. No new live entrypoint or server modification.
**Tech Stack:** Existing Kotlin/Compose/coroutines/JUnit/MockWebServer.
**Spec:** docs/superpowers/specs/2026-10-27-real-call-confirmation-design.md

## Global Constraints

Preserve demo/gallery and previous uncommitted B/C preparation; work in existing feat/compose-mvp checkout. No actual network calls to deployment, real telephony, credentials, new dependencies/icons, main merge or push. Sim remains default; only clawops is an allowed real backend. Keep duplicate/uncertain-delivery safeguards.

## Review Focus

Health changes after confirmation, stale target, confirmation recreation/cancel, duplicate confirmation, lost create response. Tests pin each below. UI must not claim app audio is connected.

### Task 1: Safe real-call opt-in in existing server test

**Files:** debug/servertest/ServerTestState.kt, ServerTestController.kt, ServerTestScreen.kt; unit/servertest/ServerTestControllerTest.kt, ServerTestTransportTest.kt; androidTest/RealCallConfirmationUiTest.kt; docs/api-integration.md and status.md.

**Interfaces:** enum ServerTestMode(val backend: String) { Sim("sim"), RealCall("clawops") }; ServerTestCommand.Start(key, phone, goal, mode = Sim); ServerTestController.start(key, phone, goal, mode = Sim). Mode is NOT sent as a new API JSON field.

- [x] Unit RED: real health check does not create; default Sim cannot create on clawops; explicit RealCall passes exact phone/goal once; mode drift both directions, unknown backend, busy server, duplicate and uncertain delivery block.
- [x] Implement mode and controller checks; unit GREEN.
- [x] UI RED: real number starts empty; prepare shows formatted phone/goal/warning with no command; confirm emits one RealCall Start and clears key; cancel/new number/restore/mode drift/busy state cannot reuse confirmation. Sim flow unchanged.
- [x] Implement ephemeral dialog and accurate mode/audio copy, readiness checks; UI GREEN. No live gateway in UI tests.
- [x] Exercise existing REST/WSS MockWebServer flow with both Sim and RealCall consent modes; serialization unchanged. Whole-create-JSON assertion deferred as a minor test enhancement.
- [x] Full JVM/UI regression, lintDebug and debug/release builds; inspect synthetic dialog capture. Fresh read-only review: no important findings; keep local.
