# B screens driven by API v0.3

> Historical proposal, not implemented: the user declined the additional launcher approach. The approved replacement is `2026-10-27-b-data-design.md`. A separate main-app live-call design is a later draft, not completed functionality.

The user confirmed API v0.3 stays stable and requested starting B-screen integration before the detailed backend is deployed. A remains a UI-only demo. Preserve the existing demo, A/B/C/D gallery and diagnostic server test entry points.

## Approach

Add a debug-only `ARS 서버 연동` launcher that uses the existing `ServerTestController`, simulator guard and memory-only key handling. Use the same light call gradient, number header, expanding progress card and fixed bottom controls as B. Do not replace the demo reducer or copy its fake timers/weather content. An alternative is to replace the main demo with server state; reject it because preserving that demo is explicit. Reusing the diagnostic screen alone does not connect the B visual layout.

Reuse the existing setup form for explicit key/number/goal entry and start; connect it to B-style progress immediately when starting. Do not reinterpret a typing pause as semantic goal validation or implement new STT. Clarification/secure requests temporarily use the existing safe server request form inside the progress area, rather than pretending to finish C integration. D status is shown honestly without claiming working voice transport.

## Behavior and boundaries

- Construction, launcher entry, navigation and synthetic fixtures do not start a network request or telephone call. Explicit start rechecks sim/idle health; real backend remains blocked.
- Server status, messages, goal summary, captions, selected menu path, result summary and key values determine the progress content. Snapshot replacement discards stale content. Unknown states remain visible, not coerced into success.
- `agent_done`, result summary and `connected_to_agent` do not end the server session. Only an ended state or completed REST result does. Show final server duration only when provided; do not invent live call duration.
- Retry means reconnect the same session/get result; never retry a mutation automatically. Pending requests and failed cancellation remain actionable through existing controls.
- API key stays non-saveable, clears after start; credentials/tokens remain private. Use FLAG_SECURE. No screenshot capture of a real-key session.
- Sound/audio transport is not implemented. Keep the three familiar secondary controls visually present but disable unsupported audio; manual keys/requests may reuse existing safe forms, clearly not full C redesign.
- No production calls, microphone/STT/TTS, new API contracts, backend changes, Figma edits, main merge or GitHub push.

## Verification

Pure mapping tests cover status/content priority, pending input, unknown status, authoritative REST results, no false end and no hardcoded weather. Compose tests cover real server text, bottom controls, setup/key handling, error/reconnect/cancel and representative small screens. Exercise the real existing controller with a local MockWebServer session feeding B presentation. Run full JVM, Android UI, lint and debug/release builds. Without an entered key do not claim authenticated deployed-server validation.
