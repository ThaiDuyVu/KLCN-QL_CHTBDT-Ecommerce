"""Generate the review dossier from new executions and independent Git evidence."""
from collections import Counter
import statistics,xml.etree.ElementTree as ET
from common import *
rows=load(OUT/'results.json');cases=load(OUT/'cases.json');findings=load(OUT/'evidence/root_causes.json');environment=load(OUT/'environment_sanitized.json');commands=load(OUT/'evidence/commands.json');cleanup=load(OUT/'evidence/cleanup.json')
assert len(rows)==120 and all(r['verdict']!='EXECUTED_PENDING_DIAGNOSIS' for r in rows)
assert not any(r['verdict'] in ['FAIL','PASS_WITH_WARNINGS'] and not r.get('diagnosis_ids') for r in rows)
assert preserve_hashes()==load(OUT/'evidence/original_reports_sha256.json')
counts=Counter(r['verdict'] for r in rows)
source=environment['source_commit'];baseline={}
for p in (ROOT/'chatbot-service/debug/eval60/results').glob('phase0*.json'):
 for c in load(p)['cases']:baseline[c['case_id']]=c['verdict']
versions={id:(OUT/f'evidence/commands/{id}.txt').read_text().strip() for id in ['python-version','pytest-version','node-version','java-version','docker-version']}
frameworks={name:load(ROOT/'Fontend/node_modules'/name/'package.json')['version'] for name in ['vitest','vite','react']}
props=ET.parse(next((ROOT/'backend/target/surefire-reports').glob('TEST-*.AuthMeIntegrationTest.xml'))).getroot().find('properties');classpath=next((p.attrib.get('value','') for p in props if p.attrib.get('name')=='java.class.path'),'')
m=re.search(r'junit-jupiter-api/([^/]+)/',classpath) or re.search(r'junit-jupiter-api-([\d.]+)\.jar',classpath)
frameworks['JUnit']=m[1] if m else 'UNKNOWN; Surefire summary available'
environment.update(tool_versions=versions,test_frameworks=frameworks,fixture_overview=load(OUT/'evidence/final_fixture_facts.json'),command_log='evidence/commands.json',source_business_unchanged=cleanup['source_business_equal'],original_reports_unchanged=cleanup['original_reports_unchanged'])
dump(OUT/'environment_sanitized.json',environment)
def esc(s):return str(s or '').replace('|','/').replace('\n',' ')
def short(s,n=155):return esc(s)[:n]
def evidence(r):return r.get('trace_evidence') or ('evidence/browser.json' if r['id']=='T59' else f'evidence/boundaries/{r["id"]}.json' if r['id'].startswith('B') else 'results.json')
def filelink(r):return f'[{r["id"]}]({evidence(r)})'
def source_ref(location,prefix='../../../../'):
 return f"{location['file']}:{location['line']}"

changed=(OUT/'evidence/commands/git-source-diff.txt').read_text().strip()
audit=f'''# 00 — Baseline and Git Audit

Task `c2c_chatbot_post_upgrade_validation`, iteration0. New diagnostic execution **20/10/2026**, not a reuse of the13/10 runtime verdicts. Source commit **{source}**, branch `dev/thai-duy-vu`, workspace_info **QL_CHTBDT_Ecommerce**.

## Identity and synchronization

Initial local HEAD1660315 differed from connector HEAD1c62129. User authorized synchronizing main/dev. `git fetch origin` revealed main1c62129 (PR43 already merges1660315); local main and dev were fast-forwarded, dev remote updated. No new merge commit or source implementation was needed. Both local main/dev now1c62129. Connector name/HEAD and the executed checkout agree. Diagnostic artifacts were not committed or pushed.

## Independent historical Git evidence

Actual commands/output: [branches](evidence/commands/git-branches.txt), [HEAD](evidence/commands/git-head.txt), [history438227c..HEAD](evidence/commands/git-history.txt), [source diff](evidence/commands/git-source-diff.txt), [diff stat](evidence/commands/git-historical-stat.txt), [1660315 vs current tree](evidence/commands/git-merge-tree.txt), [migration diff](evidence/commands/git-migrations.txt).

- Baseline438227c contains the original runtime diagnostic implementation/report.
- d235df5 adds eval60 evidence/reports; it does not itself prove source fixes.
- 323ea50 is the combined implementation commit: QueryPlan/ProductSelector, normalization/exact identity, owned durable history/snapshot, canonical customerId, customer selection, RAG/grounding/guards and tests.
- 1660315 adds upgrade reports01–04 and sanitized evidence.
- 1c62129 is main merge PR43. **Tree diff1660315..1c62129 is empty**; source was already present on dev.
- Reports02/03/04 are logical checkpoints; Git does not contain independent implementation commits for each. Markdown chronology alone is not independent Git verification.
- No Flyway migration change from438227c to HEAD. Current V1–V8 applied successfully in copied DB.

## Actual chatbot source/test changes

```text
{changed}
```

Introduced regression modules: `test_query_product_v2.py`, `test_persistent_conversation.py`, `test_final_grounding_safety.py`. Existing backend-client/AI-flow tests were updated; Java AuthMe integration adds canonical customer-ID proof; frontend Lab tests add durable-session behavior. [Current Python code inventory with definitions/lines/hashes](evidence/python_code_inventory.json).

## Contract and behavioral changes verified in current code

| Component | Verified change | Important limit |
|---|---|---|
| QueryPlan/Search | targets[] exact identity and target configuration; application-controlled clarification | Single-intent; no task/outcome contract |
| Domain normalization | Canonical backend category/brand; VND/GB/TB; deterministic hard filters | New cases show lost unknown brands, synonyms, per-target config and unsupported grammar |
| Product retrieval | Structured eligibility before semantic ranking; exact SKU/product/variant; stock from warehouse |500 catalog/identity bound, brand first100 |
| Grounding | ProductMatch has identity/specifications; final refresh aligns Match/Card; deterministic evidence text | One representative card/product; no independent performance recommendation |
| Customer | Canonical customerId separate from userId; exact owned order code/latest sorting; payment evidence | Missing-payment read contract and nullable warehouse need review |
| Conversation | postgres opt-in; versioned ASSISTANT snapshot in existing TEXT; ownership/paging/close/lease | USER and ASSISTANT separate commits; ratings/trace RAM |
| Knowledge | Existing status-eligible vector search plus lexical topic gate; extractive DEMO/source labeling | Official corpus absent; lexical false positives observed |
| Frontend | Own-session list/close/history paging and text reload | Historical cards/sources not restored |

## Code review coverage and supported conclusions

Inspected FastAPI lifespan/config/dependencies/router; QU model/schema repair/fallback/normalization; exact and semantic product adapters/indexing; Spring advanced search/detail/pricing/inventory/owned order contracts; customer DTO mapping; PG history/ownership/reference/concurrency; knowledge chunking/ingestion/status/vector eligibility; deterministic/model response paths; read-only policy/tracing; Lab API/history restore; tests and SQL constraints.

The advanced search uses parameterized SQL, variant-level effective prices and warehouse inventory quantity-reserved_quantity. Exact identity avoids semantic replacement. Product semantic index filters embedding_model/dimensions; knowledge index filters dimensions/status but has no encoder provenance field. Chunking remains fixed windows, no importer/official policy added. Authentication continues through Spring cookies; no mutation tool is added to chatbot. Code inspection is separated from executed proof in the root register.

## Documentation synchronization

Original eval60, upgrade and runtime reports are unchanged ([original hashes](evidence/original_reports_sha256.json), [cleanup proof](evidence/cleanup.json)). Prior04 contains58 PASS/1 warning/1 deferred; new historical subset has57 PASS/2 warnings/1 deferred because T59 now explicitly assesses known RAM feedback/trace and text-only UI limits, not because durable text restore failed. Claims such as missing-payment wording must be qualified: current Spring read409 prevents that nullable context from reaching real response generation (N27). Unit suites passing did not expose the new paraphrase/configuration defects.

Current known deferred features: multi-intent tasks/partial outcomes, cross-session personal memory, long-term summary, all-warehouse aggregation, fuzzy identity confirmation workflow, durable feedback/trace and full response-history UI restoration. No fix is implemented here.
'''
(OUT/'00_BASELINE_AND_GIT_AUDIT.md').write_text(audit)

plan='''# 01 — Frozen Test Plan and Matrix

Expected results were written before runtime collection. `cases.json` SHA-256 is recorded in [plan_sha256.json](evidence/plan_sha256.json) and rechecked after execution; it was not rewritten from model outputs. Subsequent harness corrections distinguished final GroundedContext clarification from initial QueryPlan and Vitest test-file counts from test counts; they did not change expectations or application code.

120 distinct planned cases:60 historical +48 new (6 in each A–H) +12 controlled boundaries. New warehouse cases intentionally repeat the stock prompt under different essential fixtures/configurations; these are not stability repeats.15 stability requests are separately reported and excluded from120.

## Fixtures and execution classes

- REAL API: unmodified FastAPI → real Qwen/Ollama → real Spring → isolated pgvector clone. Product variants, source mapping and facts are read back from actual API/DB.
- REAL browser/security: Chrome reload/restart and authenticated/anonymous ownership checks.
- CONTROLLED: explicit MockTransport/model transport faults and real isolated PG persistence injection; not real-Qwen/backend E2E proof.
- Source DB: read-only pg_dump/business fingerprint; only seed.* customers allowed before clone. New orders/payments/warranty/ticket/mixed-stock fixtures live only in disposable clone. [Fixture overview](evidence/fixtures.json).
- Sessions REG_A=T23–T26 and REG_B=T27–T29; other prompts fresh unless an explicit setup is listed. N13 changes server-configured warehouse after same-session evidence; N21–N24 have separate owned setups.
- PASS requires semantic checks and evidence agreement, not just HTTP200. FAIL/WARNING has a structured diagnosis. DEFERRED is executed observation of an approved-unimplemented feature, never a feature PASS.

| ID | Group/mode | Exact prompt or action | Expected (frozen) | Profile / setup |
|---|---|---|---|---|
'''
for c in cases:plan+=f"| {c['id']} | {c['group']} / {c['mode']} | {esc(c['prompt'])} | {esc(c['expected'])} | {esc(c['profile'])}; {esc(c.get('setup',c['session_class']))} |\n"
plan+='''
## Resilience boundary coverage

B01/B02/B12 exercise dependency timeout handling; B03/B04 malformed/inconsistent DTOs; B05/B06/B07 pagination bounds; B08 real concurrent session POST and lease release; B09/B10 unavailable USER/ASSISTANT writes with isolated PG; B11 malformed trace header before persistence. No business schema, migration, application config file or authentication rule is changed for a test.

## Reproduce without overwriting this dossier

Use the exact source commit1c62129 and installed local dependencies/models. The harness requires the documented synthetic CUSTOMER password (optionally supply `DIAGNOSTIC_CUSTOMER_PASSWORD`; never put it in commands/logs). It reads connection settings locally without publishing them. Choose new ignored output/private directories, keep the published frozen `cases.json`, and use the same expectations. See [harness README](harness/README.md) for commands and cleanup. Diagnostic process environment overrides are temporary and scoped to the cloned stack, not production configuration edits.
'''
(OUT/'01_TEST_PLAN_MATRIX.md').write_text(plan)

execution=f'''# 02 — Execution Results

Source `{source}`; new run20/10/2026. **{dict(counts)}**;0 blocked and0 unclassified. [Machine results](results.json), [trace index](trace_index.json), [environment](environment_sanitized.json). Main cases and repeats are separated.

## Test/build commands

All command entries include timestamp, exit code and sanitized output. Java totals come from selected Surefire XML, not Maven quiet output. Vitest counts only the Tests line (9), not Tests+TestFiles (11). Runner exit0 means evidence collection/evaluation completed; it does not mean all functional cases passed.

| Command ID | Exact argv (workspace:/ aliases local root) | Exit | Counts | Output |
|---|---|---|---|---|
'''
for c in commands:execution+=f"| {c['id']} | `{esc(c['command'])}` | {c['exit_code']} | {esc(c['counts'])} | [output]({c['sanitized_output']}) |\n"
execution+='''
## Main120 outcomes

| ID | HTTP | Verdict | First demonstrated stage | Checks met | Evidence |
|---|---|---|---|---|---|
'''
for r in rows:
 checks=r.get('acceptance_checks',[]);execution+=f"| {r['id']} | {esc(r.get('http_status','see observations'))} | {r['verdict']} | {r.get('first_fault','NO_FAULT')} | {sum(c['passed'] for c in checks)}/{len(checks)} | {filelink(r)} |\n"
execution+='\n## Case-by-case evidence\n\nActual response text/cards/sources, initial/model/effective plan, normalization/reference events, backend calls/parameters, semantic checks and stage timings are in results.json and per-case trace artifacts. Personal identifiers are removed/pseudonymized in the published copy; trace IDs and public product/warehouse IDs remain. Raw synthetic traces stay only in ignored private storage.\n'
for r in rows:
 c=next(c for c in cases if c['id']==r['id']);actual=(r.get('response') or {}).get('message') or r.get('response') or r.get('observations')
 execution+=f"\n### {r['id']} — {r['verdict']}\n\nPrompt/action: {r['prompt']}\n\nExpected: {r['expected']}\n\nActual: {short(actual,650)}\n\nEvidence: {filelink(r)}; trace `{r.get('trace_id') or 'N/A (controlled/browser/security observation)'}`; first stage `{r.get('first_fault','NO_FAULT')}`; diagnoses {', '.join(r.get('diagnosis_ids',[])) or 'none/deferred observation'}.\n"
repeats=load(OUT/'evidence/repeats/summary.json');execution+='\n## Separate repeats\n\n3×T07/T15/T30/T31/T51=15 actual requests. All semantic checks satisfied; T51 remains a known clarification limitation. Intent/strategy/card identity stable across these three samples per prompt. This small sample does not establish statistical reliability. [Per-request evidence](evidence/repeats/results.json), [summary](evidence/repeats/summary.json).\n'
(OUT/'02_EXECUTION_RESULTS.md').write_text(execution)

register='''# 03 — Root Cause Register

This register covers every FAIL and PASS_WITH_WARNINGS case with supported stage attribution. VERIFIED means demonstrated by current trace/code or controlled observation; approved limitations are explicitly not new bugs. No implementation fix is applied. [Structured register](evidence/root_causes.json).

| Finding | Cases | Stage | Root status | Severity / priority | Owner |
|---|---|---|---|---|---|
'''
for f in findings:register+=f"| {f['id']} | {', '.join(f['cases'])} | {f['category']} | {f['root_cause_status']} | {f['severity']} / {f['priority']} | {esc(f['owner'])} |\n"
for f in findings:
 register+=f"\n## {f['id']} — {f['symptom']}\n\n- Cases: {', '.join(f['cases'])}; category **{f['category']}**; root status **{f['root_cause_status']}**.\n- Root cause: {f['root_cause']}\n- User/business impact: {f['impact']}\n- Severity/priority: {f['severity']} / {f['priority']}; owner: {f['owner']}.\n- Code: "+'; '.join(f"`{l['file']}:{l['line']}` — `{l['code']}`" for l in f['code_locations'])+f"\n- Secondary defects/limits: {esc(f['secondary_defects']) or 'none demonstrated'}.\n- Minimal proposed fix: {f['minimal_recommended_fix']}\n- Regression proving fix: {f['regression_test']}\n- Dependencies/compatibility: {f['dependencies_and_compatibility_risk']}\n"
 for e in f['evidence']:register+=f"\n**{e['case_id']}** Expected: {e['expected']}\n\nActual: {short(e['actual'],1000)}\n\nTrace `{e.get('trace_id') or 'N/A controlled/browser'}`; [evidence]({e['path']}).\n"
register+='''
## Suspected risks — not demonstrated defects

| Risk | Code/evidence basis | What remains unverified | Test needed |
|---|---|---|---|
| Product vector DB errors/connect timeout | product_advisor/semantic.py `_connect` has no explicit connect_timeout; ChatService catches HTTPError/ValueError | Actual index outage behavior/latency was not injected in these12 boundaries; do not claim it occurred | Controlled refused/timed-out product-index DB connection through ChatService |
| Encoder provenance for knowledge | knowledge.repository search checks status/dimensions/norm, not encoder version; ingest needs explicit force after change | No mixed-encoder corruption was introduced; current encoder/index alignment assumed from configured workflow, not a model-version field | Reindex/version mismatch contract test with approved metadata strategy |
| Cancellation/lease cleanup and frontend async switches | request lease crosses asyncio.to_thread; browser history callbacks asynchronous | B08 proves two ordinary concurrent requests, not cancellation/worker death or history-switch races | Cancel before/after lease enter; verify no orphan advisory lock and correct selected-session UI |
| Active promotions |0 active promotion rows in isolated development corpus | Live overlapping/fractional promotion resolution was not exercised in this E2E run | Controlled promotion fixtures with exact price agreement, approved existing statuses only |

## Explicit unsupported/deferred capabilities

T56/N43/N44/N48 do not execute independently bounded tasks: clarification or a supported subset is returned, with some subset replies not explicitly naming the omitted task. Reported DEFERRED, not PASS. Official corpus is absent (CONFIGURATION, confirmed absence, correct response must abstain). All-warehouse aggregation, fuzzy correction, durable ratings/trace and full history response UI require design review. Existing catalog/identity500, order500 and brand100 coverage limits are documented; B05/B06 show explicit failure for oversized catalogs/orders, B07 shows truncated brands.
'''
(OUT/'03_ROOT_CAUSE_REGISTER.md').write_text(register)

lat=[r['duration_ms'] for r in rows if r.get('duration_ms') is not None]
final=f'''# 04 — Final Acceptance Report

**C2C EXECUTED — diagnostic deliverable, awaiting ChatGPT/user acceptance.** Source `{source}`, workspace QL_CHTBDT_Ecommerce, branch dev/thai-duy-vu. Execution20/10/2026. No diagnostic commit/push or recommended application fix.

## Coverage and verdicts

| Scope | PASS | WARN | FAIL | DEFERRED | BLOCKED |
|---|---:|---:|---:|---:|---:|
'''
for name,subset in [('Historical60',[r for r in rows if r['id'].startswith('T')]),('New48',[r for r in rows if r['id'].startswith('N')]),('Controlled/boundary12',[r for r in rows if r['id'].startswith('B')]),('Total120',rows)]:
 ct=Counter(r['verdict'] for r in subset);final+=f"| {name} | {ct['PASS']} | {ct['PASS_WITH_WARNINGS']} | {ct['FAIL']} | {ct['DEFERRED_APPROVED_SCOPE']} | {ct['BLOCKED_ENVIRONMENT']} |\n"
final+='''
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
'''
final+=f'Observed API duration range {min(lat):.2f}–{max(lat):.2f} ms, median {statistics.median(lat):.2f} ms over requests with timings; sequential local observations, not a load benchmark.\n\n'
final+='''All original reports/hash checks passed; source business hash identical before/after. Disposable clone/fixtures and6 diagnostic processes were removed; source DB running state restored. Normal application stack was not started or stopped. [Cleanup proof](evidence/cleanup.json).

## Handoff and Git state

C2C EXECUTED. ChatGPT/user own review/acceptance. Source HEAD1c62129 and local/remote main/dev synchronization occurred before diagnosis under explicit authorization. Final changed files are only the new `chatbot-service/debug/post-upgrade-validation/` dossier/harness; production tracked diff empty, migrations unchanged, no diagnostic commit/push. [Changed files](evidence/final_changed_files.txt).

Start review with F03/N12, F01/N03, F05/N28, F04/N27 and F06/N34, then inspect every FAIL/WARNING in results.json/register. Preserve the evidence and freeze before accepting a future fix. Stop here; no next implementation checkpoint begins automatically.
'''
(OUT/'04_FINAL_ACCEPTANCE_REPORT.md').write_text(final)
dump(OUT/'evidence/verdict_summary.json',{'source_commit':source,'counts':dict(counts),'case_count':120,'repeats':15,'root_findings':len(findings),'blocked':0,'production_ready':False,'diagnostic_complete':True})
print('Generated five required Markdown reports; source',source,'counts',dict(counts))
