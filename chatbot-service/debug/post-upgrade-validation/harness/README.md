# Reproducing this diagnostic

Run at source commit `1c62129d290023ee577e08aade2ff0ee70a52e8f` with Docker, local JDK21/Node22/Python venv, installed Qwen chat/embedding models and the repository's documented development seed database. The harness refuses copying customer data unless all source customer usernames are seed.*. It does not write source business tables.

Use fresh output/private directories to preserve this dossier. The following are harness-only settings, not production configuration:

```sh
export CHATBOT_DIAGNOSTIC_OUTPUT="$PWD/.local/post-validation-new-output"
export CHATBOT_DIAGNOSTIC_PRIVATE="$PWD/.local/post-validation-new-private"
export PYTHONPATH="$PWD/chatbot-service"
PY="$PWD/chatbot-service/.venv/bin/python"
H="$PWD/chatbot-service/debug/post-upgrade-validation/harness"

"$PY" "$H/plan.py"
"$PY" "$H/audit.py"
"$PY" "$H/recorded.py" prepare-environment "$PY" "$H/environment.py"
"$PY" "$H/recorded.py" prepare-fixtures "$PY" "$H/fixtures.py"
"$PY" "$H/suites.py"
"$PY" "$H/recorded.py" real-runtime "$PY" "$H/runtime.py"
"$PY" "$H/recorded.py" controlled-boundaries "$PY" "$H/boundaries.py"
"$PY" "$H/recorded.py" browser-restart "$PY" "$H/browser.py"
"$PY" "$H/recorded.py" real-repeats "$PY" "$H/repeats.py"
"$PY" "$H/recorded.py" semantic-evaluation "$PY" "$H/evaluate.py"
"$PY" "$H/findings.py"
"$PY" "$H/recorded.py" cleanup-verification "$PY" "$H/finish.py"
"$PY" "$H/report.py"
"$PY" "$H/publish.py"
```

The synthetic CUSTOMER password is read from the repository's existing development guide, or from `DIAGNOSTIC_CUSTOMER_PASSWORD`. Never put its value in a public command, output or artifact. Private state includes temporary DB credentials and process IDs; it stays ignored and permissions-restricted. Public evidence excludes credentials, vectors, full personal DTOs and private customer identifiers. Personal session/order/payment/cart/warranty keys are pseudonymized; public product/warehouse IDs and trace IDs remain for inspection. Exact historical test prompts use only documented synthetic order codes.

Temporary stack: frontend5174, Spring8081, chatbot8091/default warehouse,8092/empty warehouse,8093/no warehouse. Backend and chatbot are the unchanged application; temporary environment variables select the disposable DB. Source PostgreSQL is started only for read-only dump/fingerprints if needed, then restored to its original running state. The usual5173/8080/8090 application stack is not used or changed. Ollama is reused when already running; otherwise the harness starts/stops its own process.

`fixtures.py` creates5 orders/4 payments/2 warranty records/1 ticket in the disposable clone. Missing-payment and null-warehouse rows intentionally exercise schema-valid legacy/incomplete states; they are not normal checkout outputs. Mixed-stock changes are committed only inside the disposable clone and restored after each case. `finish.py` removes the entire clone/container and confirms original business/report hashes match. It also removes the private database dump and strips expired DB credentials from state.

If setup fails, do not run fake E2E or change application code to compensate. Record `BLOCKED_ENVIRONMENT` with the exact dependency and use private state to clean up only diagnostic resources. No real dependency was blocked in the recorded run. Do not use failure injection classes as proof of real model behavior.

`evaluate.py` evaluates the frozen expectations; its exit0 means the evaluation completed, not all120 cases passed. Known approved limits can produce WARN or DEFERRED. All FAIL/WARN cases must remain linked to a structured finding. Repeats are outside the120-case total and do not establish statistical reliability from three samples.

For inspection of this run, begin with `../04_FINAL_ACCEPTANCE_REPORT.md`, `../results.json` and `../03_ROOT_CAUSE_REGISTER.md`. Do not rerun application requests against a publication-sanitized case record; use frozen cases.json and a fresh output directory. Raw synthetic traces are private, not uploaded or committed.
