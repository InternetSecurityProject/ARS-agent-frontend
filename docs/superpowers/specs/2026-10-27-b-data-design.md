# B server-data boundary

Approved scope: prepare B screen data integration; preserve the existing design, A UI demo, and screen gallery; no additional launcher icon. The rejected 2026-10-26 server-call proposal is NOT this task's design.

Chosen approach: an opt-in, pure `BCallData.from(snapshot, result)` presentation boundary and a `BServerCallScreen` composable that uses the existing call layout. Existing callers pass no server data and keep their exact demo behavior. Do not replace the demo state machine or add another Activity/mode/launcher. Do not connect to the deployed server in this task.

Server data supplies status/message, goal, most recent ARS caption, selected path, summary, key information and final duration. Final REST results override stale snapshot summary/path even when empty. Only `ended` or completed REST result is terminal; `summary`, `agent_done` and handoff are not. Unknown states remain distinguishable. Request metadata is retained for a later C integration, not converted to hardcoded demo choices.

Keep existing number/progress/footer positions and colors. Unsupported server-side audio/keypad controls remain visually present but disabled in the opt-in renderer. End/back invoke caller callbacks; rendering itself does not mutate state or create network requests. Do not invent live call duration, elapsed-time transitions, six-digit input rules, or a microphone implementation.

Test with synthetic API events through the existing local test transport/controller, pure mapping tests and Compose UI tests. Existing demo/gallery regressions must remain green. No secrets, real calls, backend/Figma changes, push or merge. Keep the existing feat/compose-mvp checkout as in the preceding approved work.
