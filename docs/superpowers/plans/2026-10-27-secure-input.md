# Secure Input Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Explicit completion for all C secure inputs, with offline server-aware secure_input preparation.
**Architecture:** Pure ephemeral draft plus opt-in Compose input callbacks; reuse card-free keypad and call frame. Keep demo separate and replace its six-digit auto-submit with CommitSecure.
**Tech Stack:** Existing Kotlin, Compose, JUnit, MockWebServer.
**Spec:** docs/superpowers/specs/2026-10-27-secure-input-design.md

## Global Constraints

Keep design/normal keypad/demo/gallery entrypoints and previous B/C work. No live deployment calls, keys, new icons/dependencies, Figma mutations, commits or push. Same feat/compose-mvp checkout. Server contract allows 1~20 chars [0-9*#], optional terminator is appended by server; no expected-length field.

## Review Focus

No implicit six-digit submission; no raw digits in UI/saved state/logs; expiry/replacement/background disposal; duplicate after resize/reappearing request; exact secure_input payload without appended terminator. Include tests for all five.

### Task 1: Explicit completion and secure server form

**Files:** model/PhoneState.kt, model/SecureInputDraft.kt; ui/CallScreens.kt, ui/ServerSecureKeypad.kt, ui/BServerCallScreen.kt, ui/Components.kt; debug/servertest/ServerTestScreen.kt; unit/model/PhoneStateTest.kt, SecureInputDraftTest.kt; androidTest/PhoneFlowTest.kt, ScreenGalleryTest.kt, ServerSecureInputUiTest.kt, ServerTestUiTest.kt; unit/servertest/ServerTestTransportTest.kt; docs.

**Interfaces:** Action.CommitSecure; SecureInputDraft(request: UserRequest).append(Char, Instant), delete(), clear(), submit(Instant): SessionAction.SecureInput?, count; SecureInputInput(canSend: Boolean, onSubmit: (SessionAction.SecureInput)->Unit, retryVersion: Int=0). CallScreen/BServerCallScreen optional secureInput; no live owner connection.

- [x] Update demo unit/UI test to require button and retain digits until completion; add draft tests for 1,6,20 chars, empty/expired/wrong kind, redacted representation, deletion, terminator omission. Run RED before implementation.
- [x] Implement demo CommitSecure + draft. Run unit tests GREEN.
- [x] Add server UI tests: labels, masked non-auto input, explicit callback, configured terminator, empty/disabled/expired/readiness guards, request replacement, end/background disposal, retry and resize duplicate protection, compact scrolling. Run RED.
- [x] Implement ephemeral form at stable call-screen scope, card-free server keypad, lifetime cleanup and callbacks. Add demo button, retain fixed footer and gallery. Rename diagnostic secure submit to 입력 완료; update test.
- [x] Test exact secure_input JSON through existing local MockWebServer boundary, no live deployment calls.
- [x] Run full JVM/UI tests, lintDebug and debug/release builds; inspect synthetic capture and diff; update docs with results.
- [x] Fresh read-only review; important fixes RED→GREEN and full regression; retain local work.
