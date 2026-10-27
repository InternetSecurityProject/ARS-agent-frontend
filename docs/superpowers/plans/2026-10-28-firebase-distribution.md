# Firebase Team Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Distribute signed team APKs to `meokbodeul` and offer optional in-app updates without disturbing calls.
**Architecture:** Pure Kotlin update policy, debug-only Firebase SDK/UI, release no-op. CI validates public client configuration and signs with the existing team key before distributing.
**Tech Stack:** Kotlin/Compose, Firebase App Distribution SDK beta21, Python fixture tests, GitHub Actions.
**Spec:** `docs/superpowers/specs/2026-10-28-firebase-distribution-design.md`

## Global Constraints

- Only `feat/compose-mvp` push/manual runs distribute; never merge main.
- Preserve demo, gallery, design and all existing call/API behavior; do not modify backend or place calls.
- Only idle, foreground, real-mode home can show optional update UI. Never silently install or uninstall.
- Missing local Firebase config disables updates. Release excludes the SDK implementation and client config.
- CI requires all seven existing secrets, fixed signing, increasing versionCode and exact `.demo` app ID match. Never log private credentials or package them into APK/artifacts.

## Review Focus

- Late SDK callbacks across calls/background/configuration changes must not produce prompts.
- Declined sign-in/offline must leave normal use intact, with no repeated prompt per foreground visit.
- Mismatched config/credentials or absent signing must fail before publication, without secret output.
- PR/other branch runs must not access distribution secrets.
- APK signed with a different legacy debug key needs user-directed migration, never automated data deletion.

### Task 1: Update policy and optional debug integration

**Files:** main `updates/UpdateCoordinator.kt`; debug `updates/TeamUpdates.kt`; release `updates/TeamUpdates.kt`; `MainActivity.kt`; `ui/live/MainPhoneApp.kt`; Gradle; JVM/UI tests.
**Interfaces:** `UpdateCoordinator` consumes callback-based `UpdateGateway`, exposes `StateFlow<UpdateUi>` and `setEligible(Boolean)`, `signIn()`, `install()`, `dismiss()`. `TeamUpdates(eligible: Boolean)` is a debug/release matched composable. `MainPhoneApp` gains a default no-op eligible-state callback.

- [ ] Write tests for initial consent, decline, signed-in checking, no duplicate checks, missing config, offline failure, stale check after leaving home, sign-in completion after background, explicit install only and duplicate install suppression.
- [ ] Run `./gradlew testDebugUnitTest --tests '*Update*Test'`; Expected: new policy missing (RED).
- [ ] Implement policy and debug adapter using advanced SDK sign-in/check/update methods; explicit public client initialization only from ignored debug asset. No automatic SDK dialog checks.
- [ ] Add UI tests for consent/update dismissal, real-home eligibility and hidden UI outside home. Run targeted tests then JVM suite/lint/debug/release; Expected: PASS and release contains no distribution implementation.
- [ ] Commit `feat: add optional Firebase team update notices`.

### Task 2: Safe signed CI distribution

**Files:** `scripts/firebase_config.py`, `scripts/test_firebase_config.py`, `.github/workflows/android.yml`, `.gitignore`, `app/build.gradle.kts`, `docs/firebase-distribution.md`.
**Interfaces:** CI validation generates only selected public debug Firebase asset; secret files live under runner temp. `appVersionCode` Gradle property sets monotonic CI version; default local is 1.

- [ ] Write fixture tests invoking config validator for valid/missing/malformed/duplicate/wrong-package/wrong-app/project mismatch; ensure errors do not echo inputs and credential JSON cannot become client config.
- [ ] Run `python3 -m unittest discover -s scripts -p 'test_*.py'`; Expected: missing validator (RED).
- [ ] Implement validation, exact feature-branch gated workflow, fixed team signing and pinned Firebase CLI distribution. Verify credential cleanup on failure and artifact paths exclude private data.
- [ ] Run fixture suite plus `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`; Expected: PASS. Verify custom versionCode through APK metadata and debug-only SDK/config exclusion.
- [ ] Commit `ci: distribute signed feature APKs to Firebase testers`.

### Task 3: Regression, independent review and first delivery

**Interfaces:** consumes Tasks 1/2; publishes only reviewed feature commit with GitHub Secrets.

- [ ] Run full JVM/build/lint/fixture tests and Android UI regression on task-owned isolated emulator without restarting user emulator. Expected: all existing tests plus new tests PASS.
- [ ] One fresh-context whole-branch review, fix important findings with regression tests. Expected: no blocking findings.
- [ ] Push exact reviewed commit to `feat/compose-mvp`, inspect matching Actions run. Expected: signed APK artifact and successful Firebase distribution to `meokbodeul`.
- [ ] If external Firebase API/IAM configuration blocks deployment, report exact minimal user action; never change IAM/backend without approval.
- [ ] Hand off tester link and first install/sign-in instructions; distinguish uploaded release from actual tester login/install verification.
