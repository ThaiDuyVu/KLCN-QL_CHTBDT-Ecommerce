# Fix04 evidence index / reproduction

Final evidence is `runtime/` and `faults-final/`. `runtime-initial/`, `runtime-pre-cap/`, `faults/` are earlier development executions; do not sum their counts as new cases. `faults-attempt-01/` preserves a failed instrumentation attempt. Original baseline reports and accepted Fix01/Fix02 evidence were not edited.

- `baseline.json`: actual branch/source before Fix04.
- `environment_sanitized.json`: isolated real runtime; model digests, schema/corpus counts. The reused setup helper contained an old literal workspace commit; this copy explicitly corrects it to independently verified baseline.
- `runtime/cases.json`, `plan_sha256.json`: 31 frozen historical cases selected before execution.
- `runtime/results.json`, `trace_index.json`, `evidence/traces/`: exact prompts, responses/cards, source checks, QueryPlans, trace IDs, timings and sanitized backend parameters.
- `runtime/source_worktree_sha256.json`: app/frontend source actually executed; checked again after final browser run.
- `runtime/verdict_summary.json`: 26 PASS, 1 PASS_WITH_WARNINGS, 4 DEFERRED. No full120 claim.
- `faults-final/results.json`: 8 controlled/real controls, each with execution class; PostgreSQL faults are injected at service/repository boundaries. B08 is real HTTP concurrency; B10 restart is an actual FastAPI process replacement.
- `evidence/commands.json`: exact commands, UTC timestamps, exit codes and output references, including unsuccessful development attempts. Framework counters apply to pytest/Vitest only. Vitest before/dev counts were taken from the Tests summary, correcting repeated regex matches in the reused recorder.
- `before-runtime/`, `before-safety.txt`, command `before-b10`: fresh pre-fix evidence.
- `business_*_safety_runtime.json`, `runtime/business_integrity.json`, `cleanup.json`: unchanged clone business tables and source data, temporary stack removed, protected reports unchanged.

## Reproduce safely

Use fresh output/private directories and synthetic repository seed customers. Docker, local Ollama models and Chrome must be available. Do not reuse this published evidence directory as output or point fault tests at a business database. All paths below are relative to repo root. Private state contains secrets and must stay outside Git.

1. Set `CHATBOT_DIAGNOSTIC_OUTPUT` to `.local/fix04-replay-setup`, `CHATBOT_DIAGNOSTIC_PRIVATE` to `.local/fix04-replay-private`, and `PYTHONPATH` to `chatbot-service:chatbot-service/debug/post-upgrade-validation/harness` (absolute paths recommended). Run `.venv/bin/python` from `chatbot-service` against `debug/post-upgrade-validation/harness/environment.py`; this creates a disposable synthetic PostgreSQL clone and test-owned Spring/FastAPI/Vite processes. The original helper has historical metadata literals; independently record current branch/HEAD rather than trusting them.
2. Read the new private `state.json` **inside Python**, never echo credentials. Supply its `dsn` to the subprocess environment as `CONVERSATION_TEST_DATABASE_URL` and `KNOWLEDGE_TEST_DATABASE_URL`. Run `python -m pytest tests -q`; run `npm test -- src/features/chatbot`, chatbot-scoped ESLint and `npm run build` in `Fontend` with the repository's supported Node version. Use the existing `common.run` recorder or an equivalent timestamp/exit-code recorder; never publish raw private logs.
3. Set output to a fresh `.../runtime` and run `chatbot-service/debug/post-upgrade-fixes/harness/fix04_runtime.py`. It freezes the focused31 cases, prepares synthetic fixtures only once, restarts only its default FastAPI process, makes27 real prompt requests and3security checks, saves backend truth/trace evidence and a browser session in private state.
4. Set output to fresh `.../faults` and run `harness/fix04_faults.py`; then restore output to the new runtime directory and run the existing `post-upgrade-validation/harness/browser.py`. Both require the same private state. B10/restart/cancellation and B08 concurrency are sequential relative to other DB tests to avoid measurement interference.
5. Review browser observation booleans; assign T59 PASS_WITH_WARNINGS only when reload/restart IDs/text match. Rating reset/trace reset and text-only card restore remain known limitations. Other verdicts are already asserted by the new runtime/fault harnesses; do not relabel multi-intent DEFERRED.
6. Compare business fingerprints before/after using stable row JSON serialization, verify final worktree hashes, sanitize output, then run `fix04_cleanup.py` with the replay output/private paths. This cleanup expects `protected_reports_sha256.json` in its output and source baseline at `.local/fix04-setup-output/evidence/source-business-before.json`; for a replay, adapt these two **test harness paths** to the fresh setup/output directories, never overwrite the original evidence. The clone/container is destroyed and source DB running state restored. Do not stop unrelated processes.

This dossier deliberately does not distribute private credentials, PostgreSQL dumps, tokens, full personal DTOs or raw runtime log files. No production configuration, business records or official policy corpus are modified.

Published command text files have trailing whitespace/extra EOF blank lines normalized only; test outcomes, error messages and counters are preserved. Private raw captures were not rewritten.
