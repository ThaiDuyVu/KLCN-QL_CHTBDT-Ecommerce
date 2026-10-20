# Fix01 reproduction

Use the modified source reviewed in this dossier, Docker, repository Python venv/JDK21/Node22 and installed Qwen chat/embedding models. The old harness is reused without editing its original reports. The source database must contain only documented `seed.*` customers; the harness refuses other customer data. Never put a password/DSN in a command or public output.

Choose fresh ignored private/setup directories. Do not rerun into the published `evidence/fix01` folders. The recorded runtime uses a real Spring backend and disposable pgvector clone, with process-scoped settings and no production config changes.

```sh
export PYTHONPATH="$PWD/chatbot-service:$PWD/chatbot-service/debug/post-upgrade-validation/harness"
export CHATBOT_DIAGNOSTIC_PRIVATE="$PWD/.local/fix01-runtime-private"
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/.local/fix01-runtime-output"
PY="$PWD/chatbot-service/.venv/bin/python"
H="$PWD/chatbot-service/debug/post-upgrade-validation/harness"

# Refuses existing private state; use a fresh directory for a new run.
"$PY" "$H/environment.py"

# The publication run used the following fresh output folder.
# For a reproduction select a NEW ignored output folder instead.
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/chatbot-service/debug/post-upgrade-fixes/evidence/fix01/runtime"
"$PY" chatbot-service/debug/post-upgrade-fixes/harness/fix01_runtime.py
```

`fix01_runtime.py` freezes the 35 selected prompts/expectations from the old cases.json, records app-source hashes, restarts only its owned FastAPI instance, executes the exact prompts using synthetic CUSTOMER and checks QueryPlans, all eligible variants, cards, response text and backend read evidence. REG_A=T23–T26, REG_B=T27–T29. The historical generic fixture description is narrowed to the catalog-only isolated clone; no order/payment fixtures are needed for these product cases. Raw trace exports remain private; public projections remove personal fields and secrets.

For pytest, privately load the disposable DSN from state.json into `CONVERSATION_TEST_DATABASE_URL` and `KNOWLEDGE_TEST_DATABASE_URL` inside a Python harness process. Do not echo them. Commands and outputs are recorded in `../evidence/commands.json`. Run the focused set, PostgreSQL/persistence group and full `tests -q` before cleanup. See existing `harness/suites.py` for the private environment pattern, without rerunning unrelated Java/frontend suites.

Run `fix01_cleanup.py` with CHATBOT_DIAGNOSTIC_OUTPUT set to the dossier root. It expects the source fingerprint from the setup output and report hashes from `evidence/fix01/original_reports_sha256.json`; use corresponding fresh paths/hashes when reproducing elsewhere. It checks process ownership, stops only test-owned services, removes the disposable DB and dump, restores the original source DB running state and verifies source business/report hashes. Always clean up after a blocked/failed test too; if setup is incomplete, inspect private state and remove only the recorded diagnostic resources.

`fix01_report.py` is a publication generator for this final recorded run. It asserts 35 cases with 33 PASS/1 warning/1 deferred and final source hashes, then writes the review document. Do not use its fixed outcome assertion as a generic test runner or modify expectations to satisfy it. Future runs should retain their actual verdicts, including failures. Earlier development runs are kept separately in `attempt-01`, `attempt-02`, `attempt-03`; they are not additional distinct coverage.
