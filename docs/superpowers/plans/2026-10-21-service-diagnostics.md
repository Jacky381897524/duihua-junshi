# Service failure diagnostics implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task with test-first checks and one final independent review.

**Goal:** Make recovered accessibility failures diagnosable and include actual installed version/build in exported reports, without claiming an unobserved OPPO crash fixed.

**Architecture:** Read this app's last five Android process-exit records in the diagnostics screen's existing background worker. Project only timestamp, PID, numeric reason/status into a small core formatter. Refresh the permission screen's status text while visible; use neutral wording when enabled but not ready.

**Tech stack:** Kotlin, Android API30–35, existing JUnit/core test runner.

**Spec:** User's 2026-10-21 OPPO PHY110 / ColorOS16 / Android16 API36 report: installed V1.4.5, service failed after being enabled for some time, toggle restored it; request installed app version in shared diagnostics. Supplied logs contain successful recovery only. V1.4.7+ already exports versionName. Root cause of prior failure remains unknown.

## Global constraints

- No new permission, automatic service restart, model call, screenshot, chat read, or data deletion.
- Do not read process-exit descriptions or trace streams; they may contain arbitrary/private content.
- Preserve historical releases and unrelated untracked files. Work in the current authorized codex checkout; no new worktree is required for this focused continuation.
- Version V1.4.10 / code40 is a diagnostic test build. Previous exits may be from older versions, and must be labeled accordingly.
- Existing installer-launch and OPPO screenshot restrictions remain unresolved.

## Review focus

- Manual force-stop or package update must not be labeled a service crash.
- No exit records, unavailable OS queries, or unknown reason values must remain explicitly inconclusive.
- Filter to the own package; no descriptions, trace streams, chats, keys, other app data.
- Do not query exit history on main thread; cancellation on Activity destruction remains effective.
- Live status refresh must stop when permission screen leaves foreground; no chat/model calls.

### Task 1: Recovered failure report and permission status

**Files:** Create core ProcessExitSummary.kt / ProcessExitSummaryTest.kt; create Android diagnostics/ProcessExitHistory.kt; modify ui/DiagnosticsActivity.kt, AccessibilityConnectionStatus.kt, PermissionsActivity.kt; version/release history and verification docs.

**Interfaces:** `ProcessExitRecord(timestampMillis:Long,pid:Int,reason:Int,status:Int)` and `ProcessExitSummary.describe(records:List<ProcessExitRecord>,formatTime:(Long)->String):String`.

- [x] Write formatter tests distinguishing CRASH/ANR from USER_REQUESTED/PACKAGE_UPDATED, numeric unknowns, newest-five ordering, invalid record rejection, and empty records with no invented crash inference.
- [x] Run focused test with missing API (RED), then minimal implementation and focused PASS.
- [x] Integrate read-only own-package history with Activity worker; visible/exported header uses BuildConfig.VERSION_NAME + VERSION_CODE; if a query is still pending, export explicitly says so and remains usable.
- [x] Correct enabled/not-ready wording and refresh only status text once per second while PermissionsActivity is resumed.
- [x] Run full core tests, settings tests, assembleDebug/assembleDebugAndroidTest/lintDebug. Review all modified code independently.
- [x] Package V1.4.10, verify signature/version/assets/16KB integrity, preserve old APK hashes, and document actual validation limits. No device acceptance or actual crash diagnosis claim without device evidence.

Ruling: execute directly under the user's ongoing instruction to fix the app. A new plan-approval/worktree-choice round would repeat routine permission already supplied. No device reproduction is requested or performed.

Ruling: an OEM Binder query might stall, so the optional history read must not disable all diagnostic sharing. Pending/unavailable history is explicit in both screen and export; the existing background Activity task owns cancellation. No new diagnostic read occurs outside this screen.
