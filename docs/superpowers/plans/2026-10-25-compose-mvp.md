# Compose MVP Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Installable Android UI demo matching the approved Figma and reproducible CI APK builds.
**Architecture:** Pure Kotlin phone/session reducer, ViewModel-owned demo timing, stateless Compose screens and shared visual primitives. No real calls or network session writes.
**Tech Stack:** Kotlin 2.2.21, Compose BOM 2025.09.01, AGP 8.13.0, Gradle 8.13, JDK 17/21, SDK36/min26.
**Spec:** ../specs/2026-10-25-android-mvp-design.md

## Global Constraints

- Preserve approved labels and screen count; no removed purpose-revision/result/error screens.
- No secrets or real personal data. No actual calls in demo.
- CI read-only repository permissions; same lint/test/assemble tasks locally and in Actions.

## Review Focus

- Invalid/short/pasted phone numbers and the 010 boundary.
- Cancelling pending automatic input/timers and late callbacks after end.
- Sensitive input clearing and rejecting excess/malformed input.
- Small screens and larger system fonts keeping actions reachable.
- CI signing: optional fixed team key; fork PRs never receive signing secrets.

## Task 1: Build skeleton and tested state

Files: Gradle config, `app/src/main/java/kr/ac/suwon/arsagent/model/PhoneState.kt`, `app/src/test/.../PhoneStateTest.kt`.
Interfaces: `PhoneNumber` normalization/eligibility; `AppState.reduce(Action): AppState` pure screen/state transitions.
- [x] Write tests for 010/non-010, empty/invalid length, goal/answer automatic continuation, secure length/clearing, end reset.
- [x] Watch tests fail, implement model, run unit suite.
- [x] Generate Gradle wrapper and build clean baseline.

## Task 2: Figma Compose screens

Files: MainActivity, AppViewModel, theme, components, PhoneScreens, CallScreens, local Figma assets, Compose instrumentation tests.
Interfaces: screens consume AppState and `(Action) -> Unit`; ViewModel owns cancellation-aware timers.
- [x] Add UI tests for keypad routing, cancel, call termination and audio popup.
- [x] Implement A/B/C/D screens using shared components and exact downloaded Figma SVGs.
- [x] Add a visibly separate demo scenario selector; no backend or device permission prompts.
- [x] Run emulator tests and visually compare representative screens to Figma.

## Task 3: CI and handoff

Files: `.github/workflows/android.yml`, README, docs/status.md.
- [x] Add JDK17, Android SDK36, Gradle wrapper validation, lint/test/APK artifact workflow.
- [x] Configure optional fixed team signing only on trusted non-PR builds; debug APK fallback clearly documented.
- [x] Run full lint/test/assemble, review change set, document implemented vs simulated.
- [x] Commit on feature branch. Publish only after user authorizes shared-remote writes.
