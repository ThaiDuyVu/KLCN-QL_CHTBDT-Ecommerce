# Reproduce final120 without overwriting published evidence

Use the tested worktree/hotfix plus `source_hashes.json`, not Git HEAD alone. Verify workspace and Docker/Ollama/Chrome availability first. Keep all credentials in ignored private state; never echo state, cookies or DSNs. Do not run these fault harnesses against business data. Original helper ROOT paths are imported from the existing validation harness, which is unchanged.

From repo root, choose **new** ignored directories and use the Python virtualenv:

```sh
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/.local/final120-replay"
export CHATBOT_DIAGNOSTIC_PRIVATE="$PWD/.local/final120-replay-private"
export PYTHONPATH="$PWD/chatbot-service:$PWD/chatbot-service/debug/post-upgrade-validation/harness"
chatbot-service/.venv/bin/python chatbot-service/debug/final-regression-120/harness/bootstrap.py
chatbot-service/.venv/bin/python chatbot-service/debug/final-regression-120/harness/prepare.py
chatbot-service/.venv/bin/python chatbot-service/debug/final-regression-120/harness/run_automated.py
```

`prepare.py` packages the same setup/fixtures/fingerprint operations executed during this run. It is a reproduction entry point; see `test_commands.json` for the exact invocations actually used. Bootstrap refuses to overwrite an existing frozen plan; prepare refuses reused private state. Both real dependencies and Java Testcontainers must be available. Any nonzero child test command remains in `evidence/commands.json`; driver exit0 alone does not mean all suites passed.

Record the runtime under the ID expected by `post_runtime.py`:

```sh
chatbot-service/.venv/bin/python - <<'PY'
import os
from common import ROOT, run
script = ROOT / 'chatbot-service/debug/final-regression-120/harness/run_runtime.py'
code, _ = run('runtime-final-source-all-prompts', [str(ROOT/'chatbot-service/.venv/bin/python'), str(script)], env=os.environ.copy(), timeout=720)
assert code == 0
PY
chatbot-service/.venv/bin/python chatbot-service/debug/final-regression-120/harness/post_runtime.py
```

The runtime executes104chat cases +3security cases. Post-runtime executes12boundaries +T59browser/restart, then the current semantic evaluator,19UIchecks,8extraFix04controls and15separate repeats. The original120plan bytes remain unchanged. If the UI harness is interrupted, release/stop only its owned browser process tree; preserve partial observations as failed/incomplete rather than treating process exit as acceptance. Never count supplemental UI/controls/repeats as extra distinct planned cases.

Before cleanup inspect all actual results and source hashes. Run:

```sh
chatbot-service/.venv/bin/python chatbot-service/debug/final-regression-120/harness/finalize.py
```

This checks both source/business fingerprints and protected reports, removes only owned runtime PIDs/container, restores source DB running state, removes the private dump and strips credentials from state. Verify no unrelated services were stopped. Finalizer requires the fresh run's own bootstrap/source/plan/before-fingerprint artifacts. Do not point it at published evidence or a previously cleaned state.

`evaluate_current.py` generates actual verdicts without old forced FAIL/WARN assignments. `report.py` is the reviewed narrative for this **specific execution** and refuses a changed distribution to avoid publishing stale diagnoses; do not reuse its static before/after narrative as new runtime evidence. A replay with different outcomes needs independent diagnostic review and a new report. RAG algorithms/content, schema, API/authentication and storefront UI are outside this harness scope.

The checked-in dossier preserves preliminary failures and a pre-hotfix120run separately. Sanitized text outputs have trailing whitespace normalized only; raw private captures were not rewritten. No credentials or raw local logs belong in Git.
