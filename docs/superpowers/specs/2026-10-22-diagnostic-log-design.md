# V1.4.11 diagnostic logs design

Baseline: latest V1.4.10 / 3c0254ac42b5c8e2b1b2cdc140292d29e0717557. Reports from V1.4.5 describe evidence only; never branch the fix from that old version. User requests rename 连接诊断 → 诊断日志 and persist observable errors plus operation steps so shared logs locate failures.

## Behavior

- One local chronological journal, with installed version/build, process/session, time, generated operation ID, step/outcome, fixed reason code, duration/count/boolean metrics, and bounded exception type/code frames.
- Every application-owned operation records start, intermediate steps, completion, failure, rejection or cancellation. Read follows native → capture → vision → acceptance → generation. Fill records its existing guarded stages without altering checks or writing behavior. Settings/tasks/judgment/advice/UI actions and service lifecycle also report.
- Recoverable exceptions retain sanitized type and code locations, never exception messages or API response bodies. Fatal Java errors are recorded best effort and delegated to the original Android uncaught handler; never swallowed or auto-restarted. Existing OS exit records complement the journal for kills/no callbacks.
- UI and exports distinguish incomplete/pending evidence, log persistence failures, unknown causes and expired records. A log can locate observed failures; missing callbacks cannot prove a unique cause. No promise that every OS failure is observable.
- Journal stored under internal filesDir, outside cache and configuration, not cleared by clear-cache. Default rolling bounds: 30 days, 10,000 entries or 4 MiB, with eviction/rejected-history counters visible. No passive node/chat/screenshot/model capture for logging.
- Do not store chat, nickname, account, draft/reply text, field input, tokens, endpoints, screenshots, intents, raw feedback or raw Throwable messages. Use enums/fixed mappings and numeric metadata. Default handler and sensitive fill/capture safety rules remain intact.
- Diagnostics screen shows a readable recent timeline and summary; export entire retained journal plus current version/device/status/startup/OS exit metadata as a temporary text attachment via narrowly scoped FileProvider. Large logs must not use Binder EXTRA_TEXT. Export/reading on a worker, background journal writes batched.
- Current user-facing entry/page/error prompts renamed to 诊断日志; historical release descriptions may keep their historical wording. V1.4.11/code41 is the next latest build; preserve all old releases.

## Validation

Test persistence across processes, operation correlation/cancellation/incomplete history, malformed/oversized history, bounded retention, async loading race, no exception-message/secret leakage, unknown input classification, file failure fallback, fatal handler delegation, and metadata export. Full core/settings/build/lint, exact APK signature/assets integrity and independent review. Device crash diagnosis and original OPPO restriction remain unverified without evidence.

Ruling: user has explicitly requested implementation and a latest-version update; proceed with this concrete design under existing session authorization without another planning-permission loop. No USB or deliberate reproduction required.
