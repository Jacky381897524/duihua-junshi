# Optional natural replies implementation plan

> For agentic workers: use superpowers:executing-plans inline; keep a final independent review.

Goal: add a global, persistent 去AI味 opt-in without changing V1.5.0 rules when disabled.
Architecture: keep legacy policy constants and default validator semantics. Pass a default-false flag from persisted settings into AdvisorInput; only the enabled branch adds curated natural-expression rules and relaxes strategy quotas. Both UI surfaces edit the same setting. Reuse existing generation/judging calls.
Tech: Kotlin, Android native Views, Gson, JUnit4; no new runtime dependency.
Spec: user approval of Humanizer-zh plus five supplied images, superseded by user's explicit request for a switch and preservation of original rules.

## Understanding and constraints
- Default OFF for new and upgraded installs; no change to original single/group prompts, policies, 13-question judge, style preferences or strategy validation when OFF.
- ON: short natural replies, optional repeated strategy labels, matching the user's known ME voice and actual relationship. Still exactly six distinct texts, valid targets and existing limits.
- Image1: adopt ordinary speech and rhythm; no mandatory filler, stutters, slang, emoji or awkward phrasing.
- Image2: follow actual platform and relationship, not imaginary roles or social-media broadcast tone in private chats.
- Image3: use only supplied details; never fabricate habits, weather, experiences, purchases or 'personally tested' claims.
- Image4: reduce unnecessary formal wording without removing factual precision, explicit boundaries, real terminology or user style.
- Image5: questions, humor and invitations optional; no engagement-bait quota or invented experience.
- Performance: one existing generation + one existing judgment; switch does not read, refresh or invoke a model. Existing displayed/pending batch is retained; next manual generation/refresh captures current mode.
- Persistence: shared encrypted settings; existing cache clearing preserves it. Both switches observe saved state; rollback UI on failure.
- No UI redesign, local-model changes, automatic real-chat collection or GitHub publishing.

## Decisions
- A separate opt-in was explicitly requested; unconditional default-rule changes are rejected.
- Reuse current API flow rather than a second paid 'humanizer' pass.
- Keep prompt guidance contextual rather than an inflexible banned-word filter.
- User's images are reference material, not unconditional instructions.
- Keep source attribution and MIT notice for Humanizer-zh rules, pinned to f4518a8eab97b8bfebc66a89d34320a89bef6930.

## Tasks
- [x] Add regression tests: enabled short same-strategy candidates pass in direct/group/selected/unified flows without repair; OFF retains strict legacy validation and payload; both judges get same mode/style and 13 questions.
- [x] Add mode flag and opt-in policy; keep legacy constants/defaults; run RED then GREEN and full core suite.
- [x] Add persistent default-false setting, lifecycle-aware synchronized native switches in reply-style settings and overlay menu, and settings migration regressions.
- [x] Add curated reference/notice and document switch semantics; prepare V1.5.1 local test build while public V1.5.0 stays available.
- [x] Review, build, run core/settings/source/native-lib checks, deliver APK and validation limits.

## Review focus
- OFF path must not receive new naturalness instructions, relax old strategy checks or change model call counts.
- Switching mode during a pending request does not mutate/auto-regenerate candidates; later requests use the saved value.
- Save failures reset switches and keep the actual setting; observers unregister on detach.
- Global and dedicated style plus target/speaker ownership remain intact.
- Knowledge examples and image rules cannot become fabricated personal experiences.

## Baseline
2026-10-23: isolated managed worktree created at HEAD9943018. Product diff empty. Core suite449 tests pass; existing six Kotlin nullability warnings recorded. The original local inference environment remains in the original workspace.

## Completion evidence
2026-10-23: 453 core tests and18 settings tests pass; 24 OFF request bodies byte-identical with baseline V1.5.0 across direct/group/selected targets, both judges, unified and advice. Fresh independent review: no important findings. V1.5.1 APK built and original certificate verified; source, packaged46 knowledge documents/licenses, backup policy and16KB native alignment pass. No physical-device UI or live-model quality claim. See docs/verification/2026-10-23-optional-natural-replies.md.
