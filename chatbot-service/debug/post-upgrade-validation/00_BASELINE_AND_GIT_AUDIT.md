# 00 — Baseline and Git Audit

Task `c2c_chatbot_post_upgrade_validation`, iteration0. New diagnostic execution **20/10/2026**, not a reuse of the13/10 runtime verdicts. Source commit **1c62129d290023ee577e08aade2ff0ee70a52e8f**, branch `dev/thai-duy-vu`, workspace_info **QL_CHTBDT_Ecommerce**.

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
M	Fontend/src/features/chatbot/ChatbotLab.test.jsx
M	Fontend/src/features/chatbot/ChatbotLabPage.jsx
M	Fontend/src/features/chatbot/chatbotApi.js
M	backend/src/main/java/com/example/backend/auth/dto/LoginSessionResponse.java
M	backend/src/main/java/com/example/backend/auth/service/AuthServiceImpl.java
M	backend/src/test/java/com/example/backend/auth/integration/AuthMeIntegrationTest.java
M	chatbot-service/app/features/chat/dependencies.py
A	chatbot-service/app/features/chat/policy.py
M	chatbot-service/app/features/chat/router.py
M	chatbot-service/app/features/chat/service.py
M	chatbot-service/app/features/conversation/models.py
A	chatbot-service/app/features/conversation/postgres_repository.py
A	chatbot-service/app/features/conversation/references.py
M	chatbot-service/app/features/conversation/repository.py
M	chatbot-service/app/features/conversation/service.py
M	chatbot-service/app/features/customer_context/models.py
M	chatbot-service/app/features/customer_context/service.py
M	chatbot-service/app/features/knowledge/service.py
M	chatbot-service/app/features/product_advisor/backend.py
M	chatbot-service/app/features/product_advisor/real_service.py
A	chatbot-service/app/features/query_understanding/normalization.py
M	chatbot-service/app/features/query_understanding/ollama_service.py
M	chatbot-service/app/features/response_generation/ollama_service.py
M	chatbot-service/app/shared/backend/client.py
M	chatbot-service/app/shared/config.py
M	chatbot-service/app/shared/contracts/product.py
M	chatbot-service/app/shared/contracts/query.py
M	chatbot-service/tests/conftest.py
M	chatbot-service/tests/test_ai_chat_flow.py
M	chatbot-service/tests/test_ai_services.py
M	chatbot-service/tests/test_backend_client.py
A	chatbot-service/tests/test_final_grounding_safety.py
A	chatbot-service/tests/test_persistent_conversation.py
A	chatbot-service/tests/test_query_product_v2.py
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
