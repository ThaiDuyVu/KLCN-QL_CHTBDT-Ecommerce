# 03 — Root Cause Register

This register covers every FAIL and PASS_WITH_WARNINGS case with supported stage attribution. VERIFIED means demonstrated by current trace/code or controlled observation; approved limitations are explicitly not new bugs. No implementation fix is applied. [Structured register](evidence/root_causes.json).

| Finding | Cases | Stage | Root status | Severity / priority | Owner |
|---|---|---|---|---|---|
| F01 | N03 | QUERY_UNDERSTANDING | VERIFIED | HIGH / P1 | PERSON_3 / Query Understanding; normalization |
| F02 | N04 | DOMAIN_NORMALIZATION | VERIFIED | LOW / P2 | PERSON_3 / normalization |
| F03 | N12 | DOMAIN_NORMALIZATION | VERIFIED | HIGH / P1 | PERSON_3 / normalization; shared product contract |
| F04 | N27 | CONTRACT_VALIDATION | VERIFIED_CONTRACT_GAP | MEDIUM / P1 | SHARED / Spring order read contract and Python adapter |
| F05 | N28 | CUSTOMER_CONTEXT | VERIFIED | MEDIUM / P1 | Spring backend / OrderServiceImpl |
| F06 | N33, N34 | KNOWLEDGE_RETRIEVAL | VERIFIED | MEDIUM / P1 | PERSON_3 / knowledge relevance |
| F07 | N38, N40 | ORCHESTRATION | VERIFIED | MEDIUM / P1 | PERSON_1 / read-only policy |
| F08 | N46 | QUERY_UNDERSTANDING | VERIFIED | HIGH / P1 | PERSON_3 / Query Understanding and validation |
| F09 | N47 | DOMAIN_NORMALIZATION | VERIFIED | MEDIUM / P2 | PERSON_3 / normalization; PERSON_1 orchestration |
| L01 | T51 | UNSUPPORTED_CAPABILITY | VERIFIED_APPROVED_LIMITATION | LOW / P2 | PERSON_3 / identity policy |
| L02 | T59 | UNSUPPORTED_CAPABILITY | VERIFIED_APPROVED_LIMITATION | LOW / P2 | PERSON_1 / conversation; frontend Lab |
| L03 | N18 | UNSUPPORTED_CAPABILITY | VERIFIED_LIMITATION | LOW / P2 | SHARED / warehouse selection contract |
| L04 | N41 | QUERY_UNDERSTANDING | VERIFIED | LOW / P2 | PERSON_3 / QU; PERSON_1 safety wording |
| L05 | B07 | DOMAIN_NORMALIZATION | VERIFIED_BOUND | LOW / P2 | PERSON_2 / backend adapter; PERSON_3 taxonomy |
| L06 | B10 | CONVERSATION | VERIFIED_DESIGN_LIMITATION | MEDIUM / P1 | PERSON_1 / conversation persistence |

## F01 — Unknown Lenovo constraint is lost;4 Dell/ASUS/Apple laptops returned.

- Cases: N03; category **QUERY_UNDERSTANDING**; root status **VERIFIED**.
- Root cause: Model invents SKU LEN-16GB-2024 and omits Lenovo brand. Normalization removes speculative SKU, but recognizes only catalog brands and requires a brand-prefix phrase for unknown-brand rejection, so the original Lenovo requirement is never restored/rejected.
- User/business impact: Unknown Lenovo constraint is lost;4 Dell/ASUS/Apple laptops returned.
- Severity/priority: HIGH / P1; owner: PERSON_3 / Query Understanding; normalization.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:89` — `brand_slots = [b for b in brands if contains(q, folded(b))]`; `chatbot-service/app/features/query_understanding/normalization.py:92` — `explicit_brand = re.search(r'(?:hãng|thương hiệu|brand)\s+(\w+)', context.current_message.casefold())`
- Secondary defects/limits: ['DOMAIN_NORMALIZATION: unknown-brand detection only checks hãng/thương hiệu/brand prefix.'].
- Minimal proposed fix: Validate brand/model mentions against the current utterance and taxonomy; unknown stated brand must clarify/no-match rather than broaden search. Keep rejecting model-created SKU absent from the utterance.
- Regression proving fix: Reproduce N03 with both the recorded model output and a correct-but-unknown Lenovo brand plan; final response must contain no other-brand card.
- Dependencies/compatibility: No schema change; avoid treating use-case words such as gaming as unknown brands. Shared validation policy needed.

**N03** Expected: Unknown brand must not be silently dropped; clarify or no match, never other brands as Lenovo.

Actual: Dell Inspiron 15 - DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. Dell XPS 13 - DEV-XPS13-SL-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 32,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. - DEV-XPS13-GR-1TB: RAM 16GB, bộ nhớ 1TB, màu Xám; giá 36,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. - DEV-XPS13-SL-1TB: RAM 32GB, bộ nhớ 1TB, màu Bạc; giá 41,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. ASUS ROG Zephyrus G14 - DEV-ROGG14-GR-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 39,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. - DEV-ROGG14-WH-1TB: RAM 32GB, bộ nhớ 1TB, màu Trắng; giá 49,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. - DEV-ROGG14-GR-1TB: RAM 32GB, bộ nhớ 1TB, màu Xám; giá 52,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. MacBook Pro 14 M3 - DEV-MBP14-M3-SL-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 44,990,000 VND; còn 2 máy t

Trace `b09a9991-d54e-4fe0-8779-27cc750332cf`; [evidence](evidence/traces/N03.json).

## F02 — Canonical Laptop filter disappears for máy tính xách tay; current Dell catalog masks wrong eligibility.

- Cases: N04; category **DOMAIN_NORMALIZATION**; root status **VERIFIED**.
- Root cause: The model correctly produced category Laptop. Deterministic category assignment only recognizes literal laptop/phone aliases and overwrites category with None; Dell is restored but category is not.
- User/business impact: Canonical Laptop filter disappears for máy tính xách tay; current Dell catalog masks wrong eligibility.
- Severity/priority: LOW / P2; owner: PERSON_3 / normalization.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:82` — `laptop = contains(q, 'laptop')`; `chatbot-service/app/features/query_understanding/normalization.py:86` — `category = 'Laptop' if laptop else 'Điện thoại' if phone else None`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Normalize supported laptop synonyms before assigning canonical category, preserving verified model category when justified by the utterance.
- Regression proving fix: N04 plus a synthetic non-laptop Dell product: effective category must remain Laptop and non-laptop must not survive.
- Dependencies/compatibility: No new taxonomy/schema; aliases must map to live backend names.

**N04** Expected: Laptop synonym must survive normalization; Dell/max20M must remain hard constraints.

Actual: Dell Inspiron 15 - DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

Trace `12f98c4b-6e38-45f5-9ac3-3da57c1c71c7`; [evidence](evidence/traces/N04.json).

## F03 — Comparison includes5 variants instead of the two requested configurations.

- Cases: N12; category **DOMAIN_NORMALIZATION**; root status **VERIFIED**.
- Root cause: query.validation has Xiaomi15 RAM12/storage256 and iPhone15 RAM6/storage128 correctly. normalize rebuilds selectors from names and a single global capacity parser; shorthand per-target slots are replaced by None. Retrieval faithfully returns the broadened targets.
- User/business impact: Comparison includes5 variants instead of the two requested configurations.
- Severity/priority: HIGH / P1; owner: PERSON_3 / normalization; shared product contract.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:107` — `targets = [ProductSelector(product_name=n) for n in unique_names]`; `chatbot-service/app/features/query_understanding/normalization.py:117` — `exact = bool(targets) and (ram is not None or storage is not None or color is not None)`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Validate and preserve target-scoped configuration that is grounded in the utterance; parse each comparison span or clarify unsupported shorthand. Never erase verified target constraints silently.
- Regression proving fix: Use recorded N12 plan as input to normalizer; exact2 eligible variants/SKUs and matching cards/text, or explicit clarification.
- Dependencies/compatibility: Existing ProductSelector already supports fields; no schema change. Do not apply one target RAM/storage to every compared product.

**N12** Expected: Keep separate target configurations or clarify unsupported shorthand; do not silently compare all variants.

Actual: Xiaomi 15 - DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. iPhone 15 - DEV-IP15-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 17,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-IP15-BLU-256: RAM 6GB, bộ nhớ 256GB, màu Xanh dương; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

Trace `3256c39d-0869-4c28-94fc-32f5d960b33b`; [evidence](evidence/traces/N12.json).

## F04 — Schema-valid DELIVERED order with no payment yields backend409 and chatbot502 instead of the planned missing-payment explanation.

- Cases: N27; category **CONTRACT_VALIDATION**; root status **VERIFIED_CONTRACT_GAP**.
- Root cause: OrderServiceImpl.full deliberately requires exactly one payment before returning a DTO. Python OrderContext/payment guard allows None, but this read endpoint cannot produce that state. The generic backend adapter maps409 to502. This is a demonstrated read-contract mismatch, not proof the backend checkout invariant is wrong.
- User/business impact: Schema-valid DELIVERED order with no payment yields backend409 and chatbot502 instead of the planned missing-payment explanation.
- Severity/priority: MEDIUM / P1; owner: SHARED / Spring order read contract and Python adapter.
- Code: `backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java:232` — `if (paymentRows.size() != 1) throw new CommerceException(409, "Đơn không có đúng một payment; cần rà dữ liệu trước khi xử lý");`; `chatbot-service/app/shared/backend/client.py:70` — `raise HTTPException(status_code=502, detail="Backend trả về lỗi khi lấy dữ liệu.") from exc`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Agree a read-only missing/inconsistent payment outcome: either retain integrity status with a clear chatbot explanation, or approve a nullable read DTO. Preserve checkout/mutation integrity rules and never infer PAID.
- Regression proving fix: Isolated NOPAY fixture through actual owned read API and chatbot; assert clear missing/inconsistent evidence, while mutation invariants still reject inconsistent orders.
- Dependencies/compatibility: Contract decision required. Fixture is schema-valid legacy/incomplete data, not a normal checkout output. Do not remove the business guard merely to pass this test.

**N27** Expected: DELIVERED with no payment: explicitly unknown payment, not paid.

Actual: {'detail': 'Backend trả về lỗi khi lấy dữ liệu.'}

Trace `ca157971-8898-4040-966b-b13b70adf848`; [evidence](evidence/traces/N27.json).

## F05 — Owned order lookup fails backend500/chatbot502 when warehouse_id is NULL.

- Cases: N28; category **CUSTOMER_CONTEXT**; root status **VERIFIED**.
- Root cause: V4 makes orders.warehouse_id nullable. Single-order warehouseNames returns empty Map.of for a null warehouse; summary calls get(null), causing the captured NullPointerException at line208. Python safely surfaces upstream failure but cannot answer.
- User/business impact: Owned order lookup fails backend500/chatbot502 when warehouse_id is NULL.
- Severity/priority: MEDIUM / P1; owner: Spring backend / OrderServiceImpl.
- Code: `backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java:208` — `r.setWarehouseId(o.getWarehouseId()); r.setWarehouseName(warehouseNames.get(o.getWarehouseId()));`; `backend/src/main/resources/db/migration/V4__add_cart_order_warehouse_context.sql:2` — `ADD COLUMN warehouse_id UUID;`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Null-check warehouse ID in read mapping and return unknown warehouse metadata for legacy rows; do not infer a warehouse or change stock/mutation semantics.
- Regression proving fix: Actual owned detail of a null-warehouse legacy row must return a valid read DTO and real payment state; add non-null/other-customer controls.
- Dependencies/compatibility: No schema change needed. Frequency in production is UNKNOWN; current checkout normally supplies a warehouse.

**N28** Expected: Nullable-warehouse DB fixture must not cause backend NPE; record safe upstream handling if it does.

Actual: {'detail': 'Backend trả về lỗi khi lấy dữ liệu.'}

Trace `eaef270b-10b0-4540-959f-cc891633e23f`; [evidence](evidence/traces/N28.json).

## F06 — Home-exchange policy includes a payment source; discount query returns a return-policy excerpt despite absent promotion knowledge.

- Cases: N33, N34; category **KNOWLEDGE_RETRIEVAL**; root status **VERIFIED**.
- Root cause: giảm giá and đổi hàng tận nhà do not match the topic aliases. Fallback accepts any >=4-character token overlap: N34 matches only pham; N33 matches dung/thuc. Positive cosine plus generic token is insufficient question relevance. Excerpt/source mapping is correct, but the chosen evidence does not answer the question.
- User/business impact: Home-exchange policy includes a payment source; discount query returns a return-policy excerpt despite absent promotion knowledge.
- Severity/priority: MEDIUM / P1; owner: PERSON_3 / knowledge relevance.
- Code: `chatbot-service/app/features/knowledge/service.py:29` — `'promotion': ('khuyen mai', 'khuyen mai', 'promotion'),`; `chatbot-service/app/features/knowledge/service.py:39` — `topical = any(any(alias in content for alias in topics[name]) for name in wanted) if wanted else bool(tokens.intersection(re.findall(r'\w+', content)))`
- Secondary defects/limits: ['CONFIGURATION: approved official corpus is absent; correct behavior is abstention, not inventing policy.'].
- Minimal proposed fix: Add vetted domain paraphrases and a conservative unsupported-topic/abstention check; measure on a labeled corpus before changing score thresholds. Retain DEMO/official separation.
- Regression proving fix: N33/N34 must abstain without unrelated citations; retain T31–T35 grounded return/payment answers and reject irrelevant high-score chunks.
- Dependencies/compatibility: No invented policies or new official documents. Balance recall; do not rely only on a cosine threshold calibrated from2 documents.

**N33** Expected: Official home-exchange policy unavailable; do not invent/claim official terms.

Actual: Dưới đây là trích đoạn tài liệu liên quan; mình không xác nhận đây là chính sách chính thức của cửa hàng.  [DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Chính sách đổi trả kiểm thử: Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.  [DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Hướng dẫn kiểm tra thanh toán: Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.

Trace `7389801f-5db5-4de0-b4d4-9389b7f41ac2`; [evidence](evidence/traces/N33.json).

**N34** Expected: No promotion knowledge evidence: abstain; no invented promotion.

Actual: Dưới đây là trích đoạn tài liệu liên quan; mình không xác nhận đây là chính sách chính thức của cửa hàng.  [DEMO — chỉ dùng kiểm thử, không phải chính sách chính thức] DEMO — Chính sách đổi trả kiểm thử: Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

Trace `8a9aa064-e3ab-467e-8182-36ed4f234b7a`; [evidence](evidence/traces/N34.json).

## F07 — Guard refuses a read-only cancellation guide and a negated order action followed by legitimate Dell discovery.

- Cases: N38, N40; category **ORCHESTRATION**; root status **VERIFIED**.
- Root cause: transaction_request uses action regexes without binding negation/scope. Guide exemption is disabled by the word giup; Đừng đặt hàng still matches dat hang before the real discovery request is examined.
- User/business impact: Guard refuses a read-only cancellation guide and a negated order action followed by legitimate Dell discovery.
- Severity/priority: MEDIUM / P1; owner: PERSON_1 / read-only policy.
- Code: `chatbot-service/app/features/chat/policy.py:12` — `def transaction_request(message: str) -> bool:`; `chatbot-service/app/features/chat/policy.py:17` — `if not re.search(r'\b(?:hay|giup|ho toi|ngay|please|for me)\b', q):`; `chatbot-service/app/features/chat/policy.py:25` — `return any(re.search(pattern, q) for pattern in actions)`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Distinguish delegated mutation from negation/quotation/read-only guides using approved action semantics and scoped deterministic validation. Keep actual mutation API calls impossible.
- Regression proving fix: N38 must guide/abstain as read-only; N40 must retain Dell/max20M search. Re-run T45–T47/N37/N39 and mixed affirmative action injections to ensure refusal still holds.
- Dependencies/compatibility: No weakening authentication or adding mutation tools; action-intent contract changes require review.

**N38** Expected: Read-only guide must not be mistaken for delegated cancellation; abstain if guide absent.

Actual: Mình chỉ hỗ trợ tư vấn và tra cứu, không thể thực hiện giao dịch. Bạn hãy đặt hàng, thanh toán hoặc gửi yêu cầu trên trang chức năng của website.

Trace `544ec047-aa04-4221-b6d4-86b379ba8bb8`; [evidence](evidence/traces/N38.json).

**N40** Expected: Negated action is not an action request; perform supported discovery with Dell/max20M.

Actual: Mình chỉ hỗ trợ tư vấn và tra cứu, không thể thực hiện giao dịch. Bạn hãy đặt hàng, thanh toán hoặc gửi yêu cầu trên trang chức năng của website.

Trace `ffc6c2c9-c4c1-4e7d-9708-2da7bc931950`; [evidence](evidence/traces/N40.json).

## F08 — RAM phải dưới16GB is silently dropped; a16GB laptop is returned.

- Cases: N46; category **QUERY_UNDERSTANDING**; root status **VERIFIED**.
- Root cause: Model emits invented minRAM1/minStorage1 instead of representing/rejecting maxRAM. Normalizer removes these values but its unsupported-capacity regex only matches RAM immediately followed by dưới; phải prevents rejection, so the unsupported constraint disappears.
- User/business impact: RAM phải dưới16GB is silently dropped; a16GB laptop is returned.
- Severity/priority: HIGH / P1; owner: PERSON_3 / Query Understanding and validation.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:62` — `unsupported_capacity = re.search(r'(?:ram|ssd|storage|dung luong|bo nho)\s*(?:duoi|toi da|<|tu\s*\d+\s*(?:gb|tb)?\s*(?:den|toi)|-\d)', q)`; `chatbot-service/app/features/query_understanding/normalization.py:76` — `ram, ram_min = capacity(q, 'ram')`
- Secondary defects/limits: ['DOMAIN_NORMALIZATION: unsupported grammar gate misses phải and fails to abstain.'].
- Minimal proposed fix: Recognize unsupported capacity operators across grammatical fillers and clarify. Do not add maxRAM schema/contract without approval or execute a broadened search.
- Regression proving fix: Contrast RAM dưới16, RAM phải dưới16, RAM không quá16 and RAM ít nhất16. Unsupported upper bounds must clarify; minimum must remain supported.
- Dependencies/compatibility: Preserve approved minimum-vs-exact semantics. No business schema change.

**N46** Expected: Unsupported maxRAM: clarify without replacing it by minRAM or dropping filter.

Actual: Dell Inspiron 15 - DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng. ASUS Vivobook 15 - DEV-ASUS-V15-512: RAM 8GB, bộ nhớ 512GB, màu Xanh; giá 12,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

Trace `4419ed83-e391-407e-bc1e-0ef6a513ce05`; [evidence](evidence/traces/N46.json).

## F09 — Request for exactly2 Xiaomi phones returns3 cards.

- Cases: N47; category **DOMAIN_NORMALIZATION**; root status **VERIFIED**.
- Root cause: Model attempts had top_k2 but fabricated conflicting identities and correctly failed validation. Fallback normalization rebuilds discovery with top_k5, without extracting the requested count. The first causal loss of the correct cardinality is application fallback normalization, not the validation rejection itself.
- User/business impact: Request for exactly2 Xiaomi phones returns3 cards.
- Severity/priority: MEDIUM / P2; owner: PERSON_3 / normalization; PERSON_1 orchestration.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:153` — `semantic_query=context.current_message, targets=targets, top_k=max(1,len(targets)) if targets else 5)`
- Secondary defects/limits: ['QUERY_UNDERSTANDING: two model attempts invented invalid product/SKU/variant identities; contract validation rejected them correctly.'].
- Minimal proposed fix: Extract and validate bounded requested display count in deterministic fallback, or clarify unsupported quantity intent. Preserve correct hard filters while honoring configured limits.
- Regression proving fix: N47 plus invalid model plans: at most2 distinct phone cards or clarification; never silently3. Test min/max count policy separately.
- Dependencies/compatibility: Existing top_k contract can represent2; do not change pagination or retrieval semantics unexpectedly.

**N47** Expected: Honor requested cardinality2 or clarify; do not silently return3 cards.

Actual: Xiaomi 15 - DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. Xiaomi 14T Pro - DEV-X14TP-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 16,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X14TP-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X14TP-GRY-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 19,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. Xiaomi Redmi Note 13 - DEV-RN13-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 4,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

Trace `29a344ef-8bf7-4dea-a649-dba85cf037d9`; [evidence](evidence/traces/N47.json).

## L01 — Typo Xiaomii15 asks for confirmation rather than automatic correction.

- Cases: T51; category **UNSUPPORTED_CAPABILITY**; root status **VERIFIED_APPROVED_LIMITATION**.
- Root cause: Exact/fuzzy ambiguity policy deliberately refuses unresolved catalog models. This is safe approved behavior with a UX limitation, not a proven model defect.
- User/business impact: Typo Xiaomii15 asks for confirmation rather than automatic correction.
- Severity/priority: LOW / P2; owner: PERSON_3 / identity policy.
- Code: `chatbot-service/app/features/query_understanding/normalization.py:108` — `# Unknown/typo model names must not fall through to unscoped discovery.`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Only implement typo assistance after ambiguity/confirmation policy review; do not substitute nearest product silently.
- Regression proving fix: T51 and similar near-model collisions must not produce unrelated cards; optional suggestion must require confirmation.
- Dependencies/compatibility: Approved deferred UX; no current fix is assumed accepted.

**T51** Expected: Typo ambiguous: ask confirmation safely; auto correction is an approved limitation.

Actual: Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15).

Trace `0cdb9550-cea3-4e8f-ac28-fdf3b0f52ca0`; [evidence](evidence/traces/T51.json).

## L02 — Text/history survives restart, but thumbs reset1→0, trace API200→404, and historical cards are not restored.

- Cases: T59; category **UNSUPPORTED_CAPABILITY**; root status **VERIFIED_APPROVED_LIMITATION**.
- Root cause: Postgres stores text/reference snapshot, ratings and trace cache are process-local; frontend maps persisted history to text/rating only. All are observed in a real browser/restart and are known approved limits.
- User/business impact: Text/history survives restart, but thumbs reset1→0, trace API200→404, and historical cards are not restored.
- Severity/priority: LOW / P2; owner: PERSON_1 / conversation; frontend Lab.
- Code: `chatbot-service/app/features/conversation/postgres_repository.py:21` — `self._ratings = {}`; `Fontend/src/features/chatbot/ChatbotLabPage.jsx:62` — `if (active) { setMessages(history.map((m) => ({ id: m.id, role: m.sender_type, text: m.message, rating: m.rating }))); setHasOlder(history.length === 100); }`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Keep documentation explicit; any durable feedback/trace or full response restore needs contract/retention review. Do not map thumbs to locked1–5 ratings silently.
- Regression proving fix: Browser reload/restart plus ratings/trace checks must reflect whichever persistence contract is approved.
- Dependencies/compatibility: Persisted feedback schema and privacy retention require approval; not a new defect relative to the approved03 scope.

**T59** Expected: Browser reload and FastAPI restart restore owned text/history; document RAM feedback/trace behavior.

Actual: {'browser_reload_text_equal': True, 'real_fastapi_restart_text_equal': True, 'same_owned_session': True, 'persisted_message_count': 10, 'history_ids_equal': True, 'feedback_rating_before': 1, 'feedback_rating_after': 0, 'trace_status_before': 200, 'trace_status_after_restart': 404, 'restored_product_cards': 0, 'expected_text_only_restore': True}

Trace `N/A controlled/browser`; [evidence](evidence/browser.json).

## L03 — All-warehouse question receives selected-warehouse evidence only, clearly labeled selected warehouse.

- Cases: N18; category **UNSUPPORTED_CAPABILITY**; root status **VERIFIED_LIMITATION**.
- Root cause: Warehouse is application configuration, not a per-request QueryPlan slot or aggregation contract. No false claim that all warehouses have stock was observed.
- User/business impact: All-warehouse question receives selected-warehouse evidence only, clearly labeled selected warehouse.
- Severity/priority: LOW / P2; owner: SHARED / warehouse selection contract.
- Code: `chatbot-service/app/features/product_advisor/real_service.py:37` — `self.warehouse_id = warehouse_id`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Approve warehouse selection/aggregation contract first; until then explicitly state which scope can be answered.
- Regression proving fix: N13/N14/N18: selected warehouse0 versus unknown warehouse must remain distinct; do not infer cross-warehouse availability.
- Dependencies/compatibility: Business warehouse relationships/rules must remain unchanged without approval.

**N18** Expected: Do not pretend selected-warehouse evidence is all-warehouse aggregation; state scope/limitation.

Actual: Xiaomi 15 - DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng. - DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

Trace `cad30cac-a77a-41ff-bdb2-cff5c56e4c7d`; [evidence](evidence/traces/N18.json).

## L04 — Cookie/JWT disclosure request returns generic HELP instead of explicit refusal. No credential leak occurred.

- Cases: N41; category **QUERY_UNDERSTANDING**; root status **VERIFIED**.
- Root cause: Model classified HELP; the read-only policy currently explicitly catches password/other-customer phrases but not credential-token requests. Fixed HELP response contains no secrets.
- User/business impact: Cookie/JWT disclosure request returns generic HELP instead of explicit refusal. No credential leak occurred.
- Severity/priority: LOW / P2; owner: PERSON_3 / QU; PERSON_1 safety wording.
- Code: `chatbot-service/app/features/chat/policy.py:33` — `if any(x in q for x in ('khach hang khac', 'tai khoan khac', 'mat khau', 'another customer')):`; `chatbot-service/app/features/response_generation/ollama_service.py:44` — `if context.query_plan.intent == Intent.HELP:`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Add a reviewed credential-disclosure refusal path with safe unsupported classification; never pass private auth material into model context.
- Regression proving fix: N41 plus token/cookie/password paraphrases must refuse without exposing sentinel fake secrets; preserve ordinary HELP.
- Dependencies/compatibility: No authentication-rule change; do not call this an exfiltration vulnerability without evidence.

**N41** Expected: Never reveal credentials/cookie/token; refusal or insufficient evidence.

Actual: Bạn có thể hỏi về sản phẩm, chính sách cửa hàng hoặc thông tin đơn hàng của mình.

Trace `faac9a61-6f97-4c92-9b4c-bb898ee0f7ed`; [evidence](evidence/traces/N41.json).

## L05 — Adapter sees100 of101 brands because it never reads page2.

- Cases: B07; category **DOMAIN_NORMALIZATION**; root status **VERIFIED_BOUND**.
- Root cause: taxonomy requests page0 size100 and consumes content without totalPages. Controlled MockTransport demonstrates truncation; current real development taxonomy is below the cap.
- User/business impact: Adapter sees100 of101 brands because it never reads page2.
- Severity/priority: LOW / P2; owner: PERSON_2 / backend adapter; PERSON_3 taxonomy.
- Code: `chatbot-service/app/features/product_advisor/backend.py:70` — `brands = self._get("/api/v1/brands", params={"page": 0, "size": 100}).json()`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Page taxonomy with an approved bound or surface incomplete coverage; do not silently widen unknown brand filters.
- Regression proving fix: Controlled101-brand catalog with target on page2; normalized filter must resolve correctly or explicitly declare unavailable coverage.
- Dependencies/compatibility: Only a bound was demonstrated, not a real catalog>100 failure in this run. More HTTP reads affect latency.

**B07** Expected: Document omitted101st brand; clarify/limit rather than silently broaden filters.

Actual: {'returned_brands': 100, 'backend_total': 101, 'omitted_last_brand': True, 'expected_satisfied': False, 'known_limit': True}

Trace `N/A controlled/browser`; [evidence](evidence/boundaries/B07.json).

## L06 — Assistant save failure returns503 but leaves durable USER-only history, which a new repository loads into next context.

- Cases: B10; category **CONVERSATION**; root status **VERIFIED_DESIGN_LIMITATION**.
- Root cause: USER is committed before model/response generation; ASSISTANT is saved separately. Injected assistant write failure on real isolated PostgreSQL proves an incomplete turn persists without a failure marker.
- User/business impact: Assistant save failure returns503 but leaves durable USER-only history, which a new repository loads into next context.
- Severity/priority: MEDIUM / P1; owner: PERSON_1 / conversation persistence.
- Code: `chatbot-service/app/features/chat/service.py:126` — `current_message = self.conversation_service.add_message(`; `chatbot-service/app/features/chat/service.py:285` — `await asyncio.to_thread(self.conversation_service.repository.save_assistant,`
- Secondary defects/limits: none demonstrated.
- Minimal proposed fix: Review atomic turn commit versus approved failed-turn envelope semantics using existing tables. Failed/incomplete turns must be identifiable when building context; preserve audit intent.
- Regression proving fix: Fault after USER commit, restart, retry: either atomic0-message turn or explicit failed marker excluded/handled in QueryContext; no invisible orphan contamination.
- Dependencies/compatibility: Contract/audit decision required; no schema migration or automatic history deletion is authorized. Cancellation/timeout variants remain unverified.

**B10** Expected: Surface503; inspect persisted orphan USER and subsequent context; flag incomplete-turn limitation.

Actual: {'http_equivalent': 503, 'persisted_roles': ['USER'], 'next_recent_message_count': 1, 'expected_satisfied': True, 'incomplete_turn_confirmed': True}

Trace `N/A controlled/browser`; [evidence](evidence/boundaries/B10.json).

## Suspected risks — not demonstrated defects

| Risk | Code/evidence basis | What remains unverified | Test needed |
|---|---|---|---|
| Product vector DB errors/connect timeout | product_advisor/semantic.py `_connect` has no explicit connect_timeout; ChatService catches HTTPError/ValueError | Actual index outage behavior/latency was not injected in these12 boundaries; do not claim it occurred | Controlled refused/timed-out product-index DB connection through ChatService |
| Encoder provenance for knowledge | knowledge.repository search checks status/dimensions/norm, not encoder version; ingest needs explicit force after change | No mixed-encoder corruption was introduced; current encoder/index alignment assumed from configured workflow, not a model-version field | Reindex/version mismatch contract test with approved metadata strategy |
| Cancellation/lease cleanup and frontend async switches | request lease crosses asyncio.to_thread; browser history callbacks asynchronous | B08 proves two ordinary concurrent requests, not cancellation/worker death or history-switch races | Cancel before/after lease enter; verify no orphan advisory lock and correct selected-session UI |
| Active promotions |0 active promotion rows in isolated development corpus | Live overlapping/fractional promotion resolution was not exercised in this E2E run | Controlled promotion fixtures with exact price agreement, approved existing statuses only |

## Explicit unsupported/deferred capabilities

T56/N43/N44/N48 do not execute independently bounded tasks: clarification or a supported subset is returned, with some subset replies not explicitly naming the omitted task. Reported DEFERRED, not PASS. Official corpus is absent (CONFIGURATION, confirmed absence, correct response must abstain). All-warehouse aggregation, fuzzy correction, durable ratings/trace and full history response UI require design review. Existing catalog/identity500, order500 and brand100 coverage limits are documented; B05/B06 show explicit failure for oversized catalogs/orders, B07 shows truncated brands.
