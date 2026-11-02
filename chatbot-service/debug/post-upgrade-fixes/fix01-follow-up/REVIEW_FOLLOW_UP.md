# Fix01 — Review follow-up

TASK_ID: c2c_chatbot_fix01_query_contract · review iteration1 · follow-up completed.

## Accepted review baseline

User/ChatGPT verdict: **PASS_WITH_CONDITIONS** for commit **6d7dbc1984555bb49e5ae6b6cf8b2bec3b556588**, branch `dev/thai-duy-vu`. [Accepted GitHub commit](https://github.com/ThaiDuyVu/KLCN-QL_CHTBDT-Ecommerce/commit/6d7dbc1984555bb49e5ae6b6cf8b2bec3b556588). This is the commit reviewed with340 Python tests and35 runtime cases, not a claim those historical runtime cases were newly executed during this small follow-up.

[Machine-readable review record](accepted_commit.json). The follow-up correction is published in a separate commit descending from this accepted baseline; use Git history for its final commit ID.

## Finding and correction

Added tests for an unknown brand **before** the product type, both when model brands are empty and when the model preserves the unknown brand:

- `Tìm Lenovo laptop RAM 16GB.`
- `Lenovo laptop RAM 16GB dưới 20 triệu.`
- `Tìm Acer máy tính xách tay RAM 16GB.`
- `Tìm Acme điện thoại dưới 20 triệu.`
- `Tìm Unlisted smartphone RAM 12GB.`

Before correction: **5 failed,5 passed**. When the model omitted the brand, normalization silently broadened the request. When the model kept the unknown brand, the existing validation already clarified. [Recorded reproduction](before-tests.txt).

After correction: unknown adjacent identity slots on either side of product-type aliases are checked against live taxonomy. For example, an input model plan with `category=Laptop, brands=[]` for “Tìm Lenovo laptop RAM16GB” now returns `UNKNOWN + clarification`, `product_search=null`. It does not return other brands or reach Product Advisor.

This adds the symmetric identity position to the existing validation, without hardcoding Lenovo/Acer or other unknown brands. Multiword known brands are checked as whole grounded names, so “Hewlett Packard laptop” is not rejected because its last token is Packard. Known catalog product names are also exempted when grounded as a whole.

Syntactic/use-case boundaries protect “Tìm laptop”, “gaming laptop”, “văn phòng laptop”, “mỏng nhẹ laptop”, “một chiếc laptop”, “2 chiếc laptop”, “cheap laptop”, “high performance laptop”, and comparison wording “So sánh iPhone…”. A bare “16GB laptop” still requires capacity clarification under the existing contract; it is not treated as an unknown brand.

## Changes

- `app/features/query_understanding/constraints.py`: check the adjacent identity slot before product types, preserve whole grounded brands/product names, extend syntactic boundary vocabulary.
- `tests/test_query_integrity.py`:22 additional parameterized test instances:10 unknown-brand/model-output combinations,9 boundary/known-brand inputs,1 multiword brand and2 ChatService pipeline checks. Pipeline doubles explicitly fail if retrieval/cards are called; responses must clarify with no cards.
- `debug/post-upgrade-fixes/harness/fix01_followup_check.py`: disposable schema-only PostgreSQL test harness, with cleanup in finally.
- This new follow-up directory only; original reports/evidence remain unchanged.

No schema, public contract, prompt, Java, authentication, inventory or unrelated business changes. This remains a bounded adjacent identity-slot check; it does not claim universal parsing of arbitrary distant or reordered brand phrases.

## Executed tests

| Command | Exit | Result | Evidence |
|---|---:|---|---|
| `.venv/bin/python -m pytest tests/test_query_integrity.py -q` | 0 |111 passed | Full suite below includes all111 |
| `.venv/bin/python -m pytest tests/test_query_integrity.py tests/test_query_product_v2.py tests/test_query_contracts.py tests/test_real_product_advisor.py tests/test_ai_chat_flow.py -q` | 0 |185 passed,0 skipped | [output](evidence/commands/followup-focused.txt) |
| `.venv/bin/python -m pytest tests -q` | 0 |362 passed,0 failed,0 skipped | [output](evidence/commands/followup-full.txt) |

[Commands, timestamps and exit codes](evidence/commands.json). Test groups overlap; totals are not additive. One existing Starlette/httpx deprecation warning remains. The isolated database used a read-only schema dump of the development source, with no business rows copied; integration tests write synthetic fixtures only inside the disposable container. [Cleanup proof](cleanup.json).

No new real-Qwen/Spring E2E batch or browser run was performed for this follow-up. The historical35-case runtime evidence remains preserved, not relabeled as execution of the follow-up source. New pipeline evidence is deterministic testing with model/adapter doubles and is not represented as live Qwen E2E.

## Evidence preservation and handoff

All **662 tracked diagnostic files** from eval60, upgrade, post-upgrade-validation and the accepted Fix01 dossier were compared byte-for-byte with the accepted commit. [SHA-256 manifest](preserved_evidence_sha256.json). None were overwritten. New reports and test outputs are separate from the historical evidence.

The three review follow-up items are completed: before-type unknown-brand tests/correction, accepted commit recorded, original evidence preserved. Fix01 follow-up is complete and submitted with the push requested by the user. **Fix02 is not started and requires separate approval.**
