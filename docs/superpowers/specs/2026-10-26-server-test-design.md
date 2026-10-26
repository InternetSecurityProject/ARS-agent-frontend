# Separate server test mode

Approved in conversation: add a server-connected test mode while preserving the UI demo and fixed screen gallery. Enter the API key in the app; keep it in memory only, never in configuration, APK constants, saved state, files or logs.

## Approach

Chosen: independent debug Activity and controller using the existing REST/WebSocket client, with an `ARS 서버 테스트` launcher entry. This isolates server events from the UI demo's automatic timers. Existing demo/gallery entry points remain unchanged. Rejected: replacing the existing demo controller (would change approved demos); build-time key injection (requires rebuilds and embeds credentials).

The address is fixed to `https://ars.mcsmtp.org/v1`. Connection check is unauthenticated health, not proof that the key is valid. Before each explicit start, recheck `backend=sim` and that no other session is active. Creation authenticates the entered key. Block real backends and never automatically retry creation or actions; health checks alone cannot guarantee that a server will not be reconfigured between requests.

Show server-provided goal examples, status/message, recent captions, path and summaries. Support clarification options/answers, secure input (explicit send, 1–20 digits/special keys, no invented six-digit requirement/terminator), manual keys, goal revision, explicit cancel, result fetch and manual stream reconnect with fresh snapshot replacement. Critical actions require explicit server-provided selections. Unknown/expired requests are not submitted. No fake audio/microphone controls.

## Lifetimes and failures

Key entry uses non-saveable Compose state and a password field; clear after start. Client/session tokens stay private in the controller. Activity uses FLAG_SECURE. Rotation may require key reentry before starting, but a running session remains in its ViewModel. Clear network resources and credential references on exit/reset; JVM immutable strings cannot guarantee immediate memory erasure.

An active session cannot be silently abandoned with Back: offer explicit server cancellation. If cancellation fails, remain with a visible error and no false success; allow an explicitly warned connection-only exit instead of trapping the user. Force-killing the app may leave a server session active; never cancel someone else's `active_session` from health. A lost creation response blocks retries and explains uncertainty. Stream/action uncertainty requires fresh snapshot reconnection rather than action replay. Summary/agent_done/handoff is not `ended`. Ended fetches result; 202 remains pending and can be refreshed. A completed REST result also confirms termination when an ended event was missed.

## Out of scope

Real calls, STT/TTS, audio streaming, production authentication, persisted keys/sessions, changes to Figma/demo/gallery, history metadata bug, GitHub push/merge. Without the user's key, only deployed unauthenticated health can be verified; authenticated flow is verified with local MockWebServer.
