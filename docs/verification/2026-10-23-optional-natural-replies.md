# V1.5.1 optional natural replies — 2026-10-23

Base: 9943018328f93650c9a4d2176fb9885b04ab655e (V1.5.0). User requested preserving original rules with an optional new expression layer.

## Delivered behavior
- Global 去AI味 defaults OFF for new and upgraded installs, saved in existing encrypted settings.
- Settings → 回复风格设置 and the panel menu offer the same switch; attached views observe saved state and unsubscribe on detach. Save errors restore saved UI state.
- Switch itself does not read chat, refresh candidates, or call models. Next manual generation/refresh uses the saved mode; an already-started request keeps its captured mode.
- OFF retains the V1.5.0 prompts, knowledge routing, validation and judging rules.
- ON adds contextual natural expression in the existing generation/judging requests. Six distinct candidates and target validation remain; repeated strategy labels and short replies are accepted. Explicit user style, actual facts and boundaries take priority. No invented personal details or mandatory humor, slang, filler or questions.

## Verification
- Baseline core suite: 449 pass, six existing Kotlin nullability warnings.
- Mode regression RED: two expected failures for accepting short same-strategy candidates in automatic/unified paths; GREEN: four tests covering direct/group, explicit/automatic target and fixed/custom judge permutations.
- Final Gradle core suite: 453 tests, zero failures/errors/skipped (103 XML reports).
- Settings migration RED: two expected assertions for absent new setting; GREEN:18 tests. Default false, true/false roundtrip and preserving flag through unrelated model-setting saves covered.
- OFF compatibility probe against unchanged baseline compiled classes:24 actual serialized API request bodies byte-for-byte equal for draft, fixed/custom judge, unified and advice paths over direct/group and target variations. Combined payload SHA256 bb5ef56680df4e2aa1651df59af78b5a181d41c4e9fedd190ee89bc2d4da0fbf. Fake transport only; no real chats/keys sent. Probe outputs in ignored .superpowers/sdd/2026-10-23-optional-natural-replies/.
- Fresh independent whole-change reviewer: no important findings.
- tools/build-android.ps1: BUILD SUCCESSFUL. Missing local SDK/Maven build-cache files restored from existing checksum-verified archives; inference environment retained.
- tools/test-settings.ps1, tools/package-apk.ps1, tools/verify-sources.py, tools/verify-apk-content.py and tools/verify-app-policy.py: pass.
- Pinned Humanizer-zh source bytes match source-manifest SHA256s; original46 indexed knowledge files remain unchanged. Added license/provenance only.
- APK v2 signature verifies; same signing certificate as V1.5.0 (SHA256 f7c17a0bcbcdfa96b610c13fc2e5d4ece82cb71d5ff454e80834c21ca1b0aa92). ARM64 OCR library passes ZIP/ELF16KB alignment.

Artifact: output/love-advisor-1.5.1-debug.apk (27379001 bytes).
SHA256: e7018bcd2873d6ae6d0e444364f8eba8ac219f3ebcd20fcc4606c3b4af1c7f9e

## Limits and acceptance
No connected physical phone UI test or live-model A/B quality evaluation was performed. Automated fixtures verify contracts, request budget and compatibility; they do not prove real generated replies sound more natural.

On a phone: upgrade without clearing data, verify default OFF, turn ON from 回复风格设置, verify the panel menu state, toggle OFF there and confirm settings state persists after restart. Existing candidates should stay untouched; manually refresh to apply the new setting. No extra model request should occur from switching alone.
