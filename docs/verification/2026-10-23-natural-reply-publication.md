# V1.5.2 natural reply publication regression — 2026-10-23

Base: a740c76 (V1.5.1). Scope: natural reply result publication and terminal UI feedback. No local inference deployment, knowledge/prompt changes or automatic API calls added.

## Confirmed cause
AutomaticAdvisor/UnifiedAdvisor accepted short, distinct candidates with repeated strategy labels under the enabled input mode. AdvisorBatch did not retain that mode; BatchCoordinator.complete revalidated with the default legacy six-strategy rule and rejected the result. It cleared the internal loading state, but AdvisorController published updates only on acceptance; rejection was logged as a stale result without notifying the panel, leaving the panel's rendered loading state unchanged.

## Reproduction and fix
Added generated-result-to-BatchCoordinator tests before production changes. Both automatic first-read publication and full consultation publication failed at the final gate (6 tests run,2 expected assertion failures). After propagating the immutable generation mode through AdvisorBatch and using it as the validator default, both passed. Model JSON cannot enable the mode: both producers set it from AdvisorInput and the codec does not trust this field.

Completion now distinguishes ACCEPTED / REJECTED / STALE. AdvisorController publishes a rejected current result, logs INVALID_RESPONSE, keeps old candidates and enables retry. Stale results cannot stop a newer request or replace another conversation. Per-request ENABLED numeric metadata records natural mode without chat text or identities.

## Verification
- tools/test-core.ps1:459 pass. Final tools/build-android.ps1 also runs all459 tests:0 failures/errors/skipped and BUILD SUCCESSFUL.
- Added coverage: enabled first-read and refresh publication for direct/group with/without selected target; full consultation; OFF→ON→OFF using one coordinator; untrusted model mode ignored for OFF; malformed results produce failure feedback and allow retry; stale malformed results preserve the newer loading request.
- tools/test-settings.ps1:18 pass; stored default and switch semantics preserved.
-24 OFF request bodies remain byte-identical against baseline V1.5.0, including fixed/custom ranking, direct/group/selected generation, unified and advice. Combined SHA256 bb5ef56680df4e2aa1651df59af78b5a181d41c4e9fedd190ee89bc2d4da0fbf. Fake transports only, no real chats/keys/model charges.
- tools/package-apk.ps1, tools/verify-apk-content.py and tools/verify-app-policy.py:pass. APK v2 signature verifies with same V1.5.1 certificate; ARM64 OCR ZIP/ELF16KB alignment passes. All46 packaged knowledge documents and notices match sources.

Artifact: output/love-advisor-1.5.2-debug.apk (27375312 bytes), versionCode44.
SHA256:16d58906472c4e47f49191753d45e7478c2c68a9e7a7e8d10f6e0f886188ce37

## Practical limits
The concrete regression is reproduced and fixed in automated code-path tests. No connected-phone interaction or live-model A/B run was performed, and the user's requested diagnostic log had not been received when building. This does not establish that unrelated network timeouts or other reading failures are solved. On the phone, verify first read with mode ON and an existing OFF read followed by switching ON and manually refreshing. Existing candidates stay until a new result succeeds.
