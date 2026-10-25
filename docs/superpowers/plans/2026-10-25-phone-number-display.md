# Phone number search and display

Approved scope: fix review bug 1 only and automatically show phone-number hyphens while dialing. Keep in-call keypad input, back navigation, call-history metadata, API integration and demo/gallery behavior unchanged. No push or merge requested.

Approach: keep raw dial digits in AppState; format only their presentation through existing formatPhone. Compare phone searches without hyphens/spaces while preserving case-insensitive name search. Do not rewrite search field text or add formatting to ARS input.

Implementation plan (TDD, in this session):

- [x] Add JVM formatting cases (partial/full mobile, 070, service number, local area number, short number and * / #). Add UI regressions for both list searches, incremental dialing/deletion, and unchanged in-call input; observe expected failures.
- [x] Update the existing display formatter and contact-search predicate only. No dependencies, server calls, state-machine or API changes.
- [x] Run JVM and Android UI suites, lint and APK build. Install local APK on the running emulator and leave the dialer available for inspection.

Review focus: no empty normalized query matching every contact; nonnumeric names retain normal search; unrecognized/special dial strings preserved; leading zeroes retained; deletion operates on digits, not visual separators.

Verification: JVM 51 / Android UI 20 passing; lint 0 errors (11 existing warnings); APK build successful. Focused independent read-only review found no actionable issues. Back navigation and history metadata remain explicitly outside this change.
