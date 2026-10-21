# Defect register và proposed fixes

Không sửa RAG, deferred tasks hoặc public contracts. Mỗi actualFAIL/WARN có diagnosis; approved deferral không đồng nghĩa PASS.

| ID | Cases | First demonstrated stage | Severity/Priority | Disposition |
|---|---|---|---|---|
| RAG01 | N33,N34 | KNOWLEDGE_RETRIEVAL | MEDIUM/P1_AFTER_RAG_APPROVAL | OPEN_DEFERRED_RAG |
| L01 | T51 | UNSUPPORTED_CAPABILITY | LOW/P2 | KNOWN_LIMITATION |
| L02 | T59 | CONVERSATION | LOW/P2 | KNOWN_LIMITATION |
| L03 | N18 | UNSUPPORTED_CAPABILITY | LOW/P2 | KNOWN_LIMITATION |
| D01 | T56,N43,N44,N48 | UNSUPPORTED_CAPABILITY | MEDIUM/P2_AFTER_MULTI_INTENT_APPROVAL | DEFERRED_MULTI_INTENT |

## RAG01

**Symptom:** N33 cites unrelated payment chunk; N34 returns return-policy chunk for promotion question.

**Root cause:** relevant_chunks aliases omit doi hang / giam gia; with no matched topic, shared lexical tokens from DEMO boilerplate satisfy fallback overlap. Positive cosine alone does not establish support.

**Root status:** VERIFIED

**Owner:** PERSON_3 / Knowledge

**Files:** ['chatbot-service/app/features/knowledge/service.py:relevant_chunks', 'chatbot-service/app/features/response_generation/ollama_service.py:generate']

**Minimal recommendation:** After separate RAG approval, add evidence-based topic/relevance tests and require source support; do not fabricate official corpus or change algorithms in this checkpoint.

**Regression proving fix:** N33/N34 must retain FAIL until unrelated chunks are excluded and grounded abstention is verified. Test boilerplate-only overlap, zero relevant evidence and DEMO/official separation.

**Compatibility/dependency risk:** RAG quality design and official corpus/workflow require separate approval; no schema change proposed.

| Case | Expected | Actual | Trace | Evidence |
|---|---|---|---|---|
| N33 | Official home-exchange policy unavailable; do not invent/claim official terms. | Dưới đây là trích đoạn tài liệu liên quan; mình không xác nhận đây là chính sách chính thức của cửa hàng.<br><br>[DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Chính sách đổi trả kiểm thử:<br>Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.<br><br>[DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Hướng dẫn kiểm tra thanh toán:<br>Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP. | 9b05142c-af65-43b5-a507-fddcdb80a289 | evidence/traces/N33.json |
| N34 | No promotion knowledge evidence: abstain; no invented promotion. | Dưới đây là trích đoạn tài liệu liên quan; mình không xác nhận đây là chính sách chính thức của cửa hàng.<br><br>[DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Chính sách đổi trả kiểm thử:<br>Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận. | 06f6e452-958c-4a37-bc18-5eb3143be605 | evidence/traces/N34.json |

## L01

**Symptom:** Typo Xiaomii15 is clarified rather than silently corrected.

**Root cause:** Exact identity validation deliberately rejects uncertain model identity.

**Root status:** VERIFIED_LIMITATION

**Owner:** PERSON_3 / Identity

**Files:** ['chatbot-service/app/features/query_understanding/normalization.py']

**Minimal recommendation:** Keep safe clarification; fuzzy suggestions require approved identity confidence policy.

**Regression proving fix:** Typo must never bind unrelated product or invent stock.

**Compatibility/dependency risk:** Broader fuzzy matching can weaken exact identity; not authorized here.

| Case | Expected | Actual | Trace | Evidence |
|---|---|---|---|---|
| T51 | Typo ambiguous: ask confirmation safely; auto correction is an approved limitation. | Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15). | 6a9f5e3a-93a7-48cf-b77c-1bf02c2513d7 | evidence/traces/T51.json |

## L02

**Symptom:** Text/IDs survive process restart; historical cards absent, feedback1→0 and trace200→404.

**Root cause:** History API exposes text rows; feedback and dev trace index remain RAM-backed.

**Root status:** VERIFIED_CONTRACT_LIMITATION

**Owner:** PERSON_1 / Conversation + frontend

**Files:** ['chatbot-service/app/features/conversation/postgres_repository.py', 'chatbot-service/app/shared/tracing/__init__.py', 'Fontend/src/features/chatbot/ChatbotLabPage.jsx']

**Minimal recommendation:** Do not promise durable metadata. Obtain approval for feedback1–5 vs API-1/0/1 mapping, trace retention and rich-history DTO if needed.

**Regression proving fix:** Current T59 guarantees text/IDs; future approved richer contract must restore real evidence, not derive it from text.

**Compatibility/dependency risk:** Requires explicit contracts/schema decisions, not an arbitrary mapping.

| Case | Expected | Actual | Trace | Evidence |
|---|---|---|---|---|
| T59 | Browser reload and FastAPI restart restore owned text/history; document RAM feedback/trace behavior. | {'browser_reload_text_equal': True, 'real_fastapi_restart_text_equal': True, 'same_owned_session': True, 'persisted_message_count': 10, 'history_ids_equal': True, 'feedback_rating_before': 1, 'feedback_rating_after': 0, 'trace_status_before': 200, 'trace_status_after_restart': 404, 'restored_product_cards': 0, 'expected_text_only_restore': True} | N/A controlled/browser | evidence/browser.json |

## L03

**Symptom:** Only configured warehouse stock is returned; no all-warehouse aggregation.

**Root cause:** Current chatbot warehouse is server-configured; response names selected-warehouse scope.

**Root status:** VERIFIED_LIMITATION

**Owner:** SHARED / Warehouse contract

**Files:** ['chatbot-service/app/features/product_advisor/real_service.py', 'chatbot-service/app/shared/config.py']

**Minimal recommendation:** Keep selected-warehouse wording; warehouse switching/aggregation needs approved design.

**Regression proving fix:** Do not claim all warehouses sold out from one warehouse zero stock.

**Compatibility/dependency risk:** New selection contract not authorized.

| Case | Expected | Actual | Trace | Evidence |
|---|---|---|---|---|
| N18 | Do not pretend selected-warehouse evidence is all-warehouse aggregation; state scope/limitation. | Xiaomi 15<br>- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.<br>- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.<br>- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. | 86e3b6f5-080d-441b-81f8-20b3e5e10f12 | evidence/traces/N18.json |

## D01

**Symptom:** No bounded independent task execution; N44 returns cart subset without explicitly acknowledging omitted comparison.

**Root cause:** Single-plan routing selects cart/order or asks a clarifying question; multi-task contract deferred.

**Root status:** VERIFIED_APPROVED_DEFERRAL

**Owner:** PERSON_1 / Orchestration + SHARED contract

**Files:** ['chatbot-service/app/features/chat/policy.py', 'chatbot-service/app/features/chat/service.py']

**Minimal recommendation:** Design bounded tasks and explicit partial-completion wording after separate approval; do not advertise full multi-intent support.

**Regression proving fix:** N44 must disclose omitted comparison before its observed FAIL can become PASS; all4 remain scope-deferred even safe clarification is observed.

**Compatibility/dependency risk:** Public task/evidence orchestration contract not finalized.

| Case | Expected | Actual | Trace | Evidence |
|---|---|---|---|---|
| T56 | Multi-intent is design-only: ask which supported task first; do not claim full execution. | Mình hỗ trợ tìm từng nhóm sản phẩm. Bạn muốn tìm loại sản phẩm nào trước? | 34e36de6-d07e-4b4a-a4ad-5c4d8271a330 | evidence/traces/T56.json |
| N43 | Multi-intent design-only: acknowledge limit and clarify task, no full-completion claim. | Mình hỗ trợ tìm từng nhóm sản phẩm. Bạn muốn tìm loại sản phẩm nào trước? | 81079524-b7f3-4286-af4c-358b5f2fa8dc | evidence/traces/N43.json |
| N44 | Multi-intent deferred: expose partial/deferred scope, do not claim all tasks done. | Xiaomi 14T Pro (DEV-X14TP-BLU-512): 1 sản phẩm; giá mỗi sản phẩm 18990000.0 VND.<br>Tạm tính: 18990000.0 VND. | d2889c91-5fda-407e-8890-60c9e90f5363 | evidence/traces/N44.json |
| N48 | Deferred independent tasks: explain partial scope or clarify; no full-completion claim. | ORD-DIAG-DELIVERED: trạng thái đơn hàng DELIVERED; tổng tiền 18990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa.<br>ORD-DIAG-FAILED: trạng thái đơn hàng PENDING; tổng tiền 18990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa.<br>ORD-DIAG-NOPAY: trạng thái đơn hàng DELIVERED; tổng tiền 18990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa.<br>ORD-DIAG-NULLWAREHOUSE: trạng thái đơn hàng PENDING; tổng tiền 18990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa.<br>ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29: trạng thái đơn hàng PENDING; tổng tiền 49990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa.<br>ORD-DIAG-EXPIRED: trạng thái đơn hàng DELIVERED; tổng tiền 18990000.0 VND; chưa có dữ liệu thanh toán để xác nhận đã thanh toán hay chưa. | e336ddaa-a349-4b25-a3af-a75abbb1a12f | evidence/traces/N48.json |

## Regression hotfixes completed, not open defects

T03→ORCHESTRATION: pre-hotfix trace initialGREETING; full HELP text corrupted in routing_text by accent-folded separator. Exact helper test failed before, then2testsPASS including affirmative mutation control; finalT03HELP. Files `chat/policy.py`, `tests/test_final_release_regressions.py`.

UI09→frontend session recovery: real503 injected before first persist + actual history404 caused permanent needsSync; component reproduction failed; minimal status-scoped catch now allows user-triggered retry. Final real browser shows one USER and one ASSISTANT; no automatic replay. Files `ChatbotLabPage.jsx`, `ChatbotLab.test.jsx`.

## Harness defects corrected, not application regressions

Original N42 assertion rejected safe clarification explicitly allowed in frozen expectation. Correcting this is evaluation repair, not model bug/fix. Original N27 expected unavailable meaning is preserved while allowing accepted Fix02 wording. Extra backend ticket URL404 and focused missing-file command were harness mistakes, logs retained. Initial UI10 attempted to interact with intentionally disabled selector, leaving delayed network route pending and causing later UI timeout; final test checks the actual UI prevention semantics. No expectation changed.

## Unverified risks

Process death at COMMIT and exactly-once delivery are not proven; no idempotency API. Arbitrary multilingual prompt/quote nesting, arbitrary malformed card arrays and cross-tab/account-change races were not exhaustively verified. UI15 covers one incomplete200 DTO sample only. Earlier generic console404s lack URL and cannot all be conclusively attributed; supplemental real network run identifies normal anonymous401 and nonexistent-session404 with no pageerror. Full WCAG audit, production load/model statistical stability, official corpus quality and storefront chat are outside this acceptance evidence.

## Exact RAG evidence — final run

| Case | Chunk | Similarity | Title / relevance |
|---|---|---:|---|
| N33 | 466d9bd6-e605-4688-83e7-0f0e825736e8 | 0.446213 | DEMO đổi trả; không chứng minh official home-exchange policy |
| N33 | 74bcff7a-616d-4e34-bf35-e72eade1408c | 0.279151 | DEMO payment; không liên quan câu hỏi đổi hàng tận nhà |
| N34 | 466d9bd6-e605-4688-83e7-0f0e825736e8 | 0.283045 | DEMO đổi trả; không chứng minh promotion |

Trace N33 `9b05142c-af65-43b5-a507-fddcdb80a289`, N34 `06f6e452-958c-4a37-bc18-5eb3143be605`: `knowledge.relevance.topics=[]`, positive-cosine/lexical fallback vẫn giữ các chunk trên. Document/chunk mapping đúng không đồng nghĩa nguồn hỗ trợ kết luận. Không sửa threshold/aliases/scoring trong checkpoint này.
