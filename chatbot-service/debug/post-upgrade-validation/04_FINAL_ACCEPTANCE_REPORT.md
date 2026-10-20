# 04 — Final Acceptance Report

**C2C EXECUTED — diagnostic deliverable, awaiting ChatGPT/user acceptance.** Source `1c62129d290023ee577e08aade2ff0ee70a52e8f`, workspace QL_CHTBDT_Ecommerce, branch dev/thai-duy-vu. Execution20/10/2026. No diagnostic commit/push or recommended application fix.

## Coverage and verdicts

| Scope | PASS | WARN | FAIL | DEFERRED | BLOCKED |
|---|---:|---:|---:|---:|---:|
| Historical60 | 57 | 2 | 0 | 1 | 0 |
| New48 | 32 | 4 | 9 | 3 | 0 |
| Controlled/boundary12 | 10 | 2 | 0 | 0 | 0 |
| Total120 | 99 | 8 | 9 | 4 | 0 |

Original baseline:13 PASS /24 warnings /21 FAIL /2 blocked. New historical60:57 PASS /2 warnings /0 FAIL /1 deferred. The broader new probes expose gaps that the60 do not cover.15 repeated high-risk requests are separate and all meet their narrow expected safety/identity checks;3 samples are not a reliability estimate.

## A. Verified working capabilities

- Common canonical phone/laptop/brand and price/RAM/storage filters; exact product/SKU/variant resolution, prices/colors/warranty, identity comparison without unrelated products in historical cases.
- Real selected-warehouse stock; sold-out variants retained for detail; positive-stock discovery excludes zero; no warehouse remains unknown. Same-session snapshot is revalidated under a changed server warehouse (N13).
- Owned cart/order/payment/warranty/ticket evidence, exact code rather than latest substitution, PAID/PENDING/FAILED states, missing/nonexistent owned code safe lookup.
- Durable owned text/references through actual Chrome reload and FastAPI restart; ambiguity/no-reference/closed-session behavior; ordinary concurrent POST serializes with409 and releases lease.
- DEMO return/payment RAG with correct document/chunk/source mapping in core prompts, no official30-day fabrication, deterministic product facts agree with cards.
- Actual delegated order/payment/cancel/address/cart mutations refused with no business mutation/retrieval; unauthenticated401, foreign session403, foreign trace404/list empty; tested injections did not fabricate stock/price or reveal credentials.

## B. Confirmed defects / contract gaps

| Priority | Finding | Demonstrated result |
|---|---|---|
| P1 | F03 normalization erases model-correct per-target variants | N12 returns5 variants rather than2 |
| P1 | F01/F08 missing brand and unsupported-RAM validation | N03 returns other-brand laptops; N46 includes RAM16 despite upper-bound question |
| P1 | F05 nullable warehouse read mapping | backend NPE500 → chatbot502 for schema-valid legacy row |
| P1 | F04 missing-payment read contract gap | backend deliberately409 → chatbot502; Python optional-payment wording unreachable in this real path |
| P1 | F06 lexical relevance | discount request gets return-policy citation; home exchange gets payment source |
| P1 | F07 guard scope | read-only guide and negated action incorrectly refused |
| P2 | F09/F02 | requested2 cards becomes3; laptop synonym category lost though current Dell data masks bad card |

F03 is application normalization failure after a correct model plan; do not label it a model hallucination. F01/F08 have early incorrect model extraction plus insufficient validation. F09 has invalid model identities correctly rejected, followed by a deterministic fallback losing the requested cardinality. F04 is a contract/incomplete-fixture issue requiring review, not permission to weaken checkout rules. No actual payment inference or cross-customer disclosure was observed.

## C. Unverified risks

Product-index DB outage/timeout, knowledge encoder-version mismatch, cancellation/worker-death lease behavior, frontend history-switch races, live active-promotion combinations and production-scale load are not proven by this run. Their code basis and proposed tests are in the register. Absence of active promotion fixtures and only3 repeats must not be presented as broad coverage or statistical stability.

## D. Deferred/unimplemented features

Four multi-intent cases remain design-only (T56/N43/N44/N48). No official Knowledge source is approved. Typos require confirmation. All-warehouse aggregation is unsupported; app-global warehouse profiles were tested, not a user-facing warehouse switch API. Feedback/trace cache remains RAM; history restore is text-only. Failed ASSISTANT persistence leaves an orphan USER (B10), a demonstrated design limitation needing turn/audit semantics review.

## E. Blocked tests and execution errors

0 main cases blocked,0 unclassified, no suite skip. Controlled transport tests are labeled and not sold as real Qwen/backend E2E. A harness import-path correction and evaluator correction (final clarification vs initial plan, Vitest counts) did not modify frozen expectations or application behavior. Dependency failure probes returned503/502 or safe messages as documented. HTTP200 alone never assigned PASS.

## F. Recommended fixes in order

1. P1 — Preserve target-scoped hard constraints and validate unknown brands/unsupported operators (F01/F03/F08). Add the recorded plans/prompts as regressions before implementation.
2. P1 — Review owned order read contracts for nullable warehouse and absent payment (F04/F05); preserve ownership and mutation integrity.
3. P1 — Correct relevance/abstention for promotion/home-exchange paraphrases and safe negation/guide scope (F06/F07), without inventing policy or enabling mutation tools.
4. P1 — Decide atomic turn or failed-turn-envelope semantics for persistent incomplete turns (L06); no schema change or silent history deletion assumed.
5. P2 — Quantity/synonym/brand paging fixes, explicit unsupported responses and scoped production observability; implement deferred features only after contract approval.

Every item has files, minimal change, regression proof and compatibility dependencies in03. No recommended business fix has been implemented in this task.

## G. Demo readiness

**Suitable for a controlled, documented demo**, using seed data, configured warehouse/index/models and DEMO knowledge, with the above negative scenarios disclosed. Historical flows work, but new paraphrases/comparisons can still return ineligible answers or inappropriate refusals. This is a diagnostic execution handoff, not automatic acceptance.

## H. Production readiness

**Not production-ready.** Resolve high-impact hard-filter/contract/RAG faults, decide incomplete-turn policy, approve official corpus, and validate pagination/encoder provenance/cancellation/load/privacy retention. Production defaults differ from the tested opt-in AI/real/postgres flags; do not deploy defaults and assume the tested pipeline. Tracing is development/local/test-gated and should not be enabled over non-synthetic data during this validation.

## Commands, performance and preservation

Python focused109 PASS; full251 PASS (isolated PG,0 skip); Java selected auth/product/order53 PASS; frontend9 PASS/build/lint PASS. [Commands/timestamps/exit codes](evidence/commands.json), [Java summary](evidence/java-test-summary.json). Starlette/httpx emits one deprecation warning. Tests passing do not negate the9 E2E semantic failures.
Observed API duration range 45.53–6729.49 ms, median 1512.68 ms over requests with timings; sequential local observations, not a load benchmark.

All original reports/hash checks passed; source business hash identical before/after. Disposable clone/fixtures and6 diagnostic processes were removed; source DB running state restored. Normal application stack was not started or stopped. [Cleanup proof](evidence/cleanup.json).

## Handoff and Git state

C2C EXECUTED. ChatGPT/user own review/acceptance. Source HEAD1c62129 and local/remote main/dev synchronization occurred before diagnosis under explicit authorization. Final changed files are only the new `chatbot-service/debug/post-upgrade-validation/` dossier/harness; production tracked diff empty, migrations unchanged, no diagnostic commit/push. [Changed files](evidence/final_changed_files.txt).

Start review with F03/N12, F01/N03, F05/N28, F04/N27 and F06/N34, then inspect every FAIL/WARNING in results.json/register. Preserve the evidence and freeze before accepting a future fix. Stop here; no next implementation checkpoint begins automatically.
