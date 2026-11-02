# Reproduce Fix02

Use the source commit containing this dossier and local Docker/JDK21/Python dependencies/models. Always use **fresh ignored setup/private/output paths**; never overwrite the original120-case/Fix01 evidence or this frozen run.

Recorded setup:

```sh
export PYTHONPATH="$PWD/chatbot-service:$PWD/chatbot-service/debug/post-upgrade-validation/harness"
export CHATBOT_DIAGNOSTIC_PRIVATE="$PWD/.local/fix02-runtime-private"
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/.local/fix02-setup-output"
PY="$PWD/chatbot-service/.venv/bin/python"
H="$PWD/chatbot-service/debug/post-upgrade-validation/harness"
"$PY" "$H/environment.py"
```

The existing environment harness makes a disposable clone only if source customer accounts are documented seed.*. It starts real Spring/FastAPI/Ollama/frontend on dedicated diagnostic ports, with process-scoped DB settings, and takes source read-only fingerprints. Its historical metadata workspace commit is overridden in this dossier to the actual595ccd3 baseline; it does not determine the submitted app source.

Before-fix reproduction used the unchanged baseline, the existing `fixtures.prepare()` helper, two frozen N27/N28 prompts and `runtime.Runner`. The error projections in `before-runtime/owned_error_shape.json` are fresh actual409/500 and chatbot502, not copies of old results. The new Java test `OrderReadIntegrationTest#nullWarehouseOwnerListAndDetail` was run pre-fix and failed with NPE. Reproducing the old failure requires the old production implementation; do not revert accepted code in a shared checkout just to recreate it.

After implementing the fix, run focused/full Python tests with `CONVERSATION_TEST_DATABASE_URL` and `KNOWLEDGE_TEST_DATABASE_URL` privately loaded from the disposable state.json inside the harness process. Never print those values. Exact argv/timestamps/statuses: `evidence/commands.json`. Run the Java order/auth suites with local JDK21 and Testcontainers; inspect `java-test-summary.json` for actual pass/fail/skip counts.

On a fresh isolated clone, call the existing `fixtures.prepare()` **once** before `fix02_runtime.py`. Then:

```sh
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/chatbot-service/debug/post-upgrade-fixes/evidence/fix02/runtime"
"$PY" chatbot-service/debug/post-upgrade-fixes/harness/fix02_runtime.py
```

For a new reproduction choose a fresh ignored output directory instead of the published path above. The script freezes15 cases, adds isolated foreign/multiple-payment fixtures, packages the modified backend, restarts only owned backend/default FastAPI and records actual source/JAR hashes and traces. Fixtures are not idempotent: use a new disposable clone, not a second insertion into the same database. No seed/business records in the source DB are modified.

Additional N03/N12 real-Qwen controls and fingerprinted N27/N28 repeats are separate in `extra-runtime/`. They use the same Runner plus direct read-only DB fingerprints. No mocked request is represented as a real model call.

Cleanup command for the recorded run:

```sh
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/chatbot-service/debug/post-upgrade-fixes/evidence/fix02"
"$PY" chatbot-service/debug/post-upgrade-fixes/harness/fix02_cleanup.py
```

Use corresponding fresh paths/manifests for a reproduction. Cleanup expects the setup fingerprint and protected-report manifest, verifies process ownership, removes the entire disposable DB/fixtures, restores source DB running state and checks unchanged source business/report hashes. Always clean diagnostic resources after failure too; inspect private state if setup did not finish. Private dumps/traces/SQL logs stay ignored, restricted and unpublished.
