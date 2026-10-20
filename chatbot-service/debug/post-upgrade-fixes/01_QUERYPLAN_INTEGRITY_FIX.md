# 01 — QueryPlan Integrity Fix

[C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_fix01_query_contract · ITERATION: 1

## 1. Source commit và Git baseline

Workspace connector xác nhận **QL_CHTBDT_Ecommerce**, branch **dev/thai-duy-vu**. Đã fetch GitHub và fast-forward tới **55fe4ed1ca37526f1f84fa575602c0556fb41271** (`55fe4ed`, PR44). Working tree sạch trước triển khai. `origin/dev/thai-duy-vu` vẫn ở `f6a04f7`; ahead1 là merge commit đã có trên GitHub/main, không phải commit Fix01. Không commit/push trong checkpoint này.

Git xác minh source `chatbot-service/app` và tests ở HEAD đồng nhất với commit kiểm thử **1c62129** trước khi sửa, không dựa vào kết luận Markdown. Current source được đọc qua connector và filesystem. [Lệnh Git và test](evidence/commands.json); [hash source thực thi](evidence/fix01/runtime/evidence/source_worktree_sha256.json). Source sửa còn trong working tree; HEAD không đại diện đầy đủ cho code được test.

Đã đọc matrix/results/register/final report và trace N03/N04/N12/N46/N47. Toàn bộ dossier gốc `eval60`, `chatbot-upgrade`, `post-upgrade-validation` được giữ nguyên; [hash kiểm tra](evidence/fix01/original_reports_sha256.json), [cleanup](evidence/fix01/cleanup.json).

## 2. Root causes đã tái hiện

Trước sửa, replay chính các QueryPlan đã ghi lại từ trace cũ: **5 failed**, đều là assertion chức năng, không phải lỗi môi trường. [Output](evidence/fix01/before-tests.txt). N47 replay UNKNOWN vì cả hai model attempts cũ đều sai schema; các test riêng thực sự chạy malformed JSON/repair rồi fallback, không coi model JSON giả là E2E.

- F01: model bỏ Lenovo/bịa SKU; normalizer loại SKU nhưng làm rộng sang mọi hãng. Cần đối chiếu thương hiệu được nêu với taxonomy và giữ trạng thái chưa resolve.
- F02: literal category matcher không hiểu “máy tính xách tay”, ghi đè Laptop thành null.
- F03: rebuild selectors từ tên và cấu hình toàn câu làm mất điều kiện riêng 12/256, 6/128.
- F08: parser không phân biệt đủ toán tử/filler; “RAM phải dưới” rơi mất thay vì bị từ chối.
- F09: top_k hardcode5 sau fallback, không đọc số sản phẩm user yêu cầu.

## 3. Files changed và lý do

| File | Thay đổi |
|---|---|
| `app/features/query_understanding/constraints.py` (mới) | Internal typed CapacityConstraint/Relation/ProductEvidence; grammar slot/toán tử, kiểm tra coverage, taxonomy/category/brand, cardinality và integrity gate. |
| `app/features/query_understanding/normalization.py` | Dùng utterance evidence thay model filters không có căn cứ; chia target theo vị trí tên trong câu; chuẩn hóa category; loại điều kiện chưa hỗ trợ bằng clarification. |
| `app/features/product_advisor/backend.py` | Báo taxonomy không đủ coverage khi brand pagination còn trang, tránh xem hãng vắng trong trang đầu là không tồn tại. Không thêm endpoint. |
| `app/features/conversation/references.py` | Dùng chung cú pháp storage_reference với normalizer; giữ nguyên ownership/snapshot/backend refresh. |
| `app/features/response_generation/ollama_service.py` | Với explicit requested count, nếu kết quả ít hơn thì nói rõ X/N sản phẩm; không đếm variants thành nhiều sản phẩm. |
| `tests/test_query_integrity.py` (mới) | **89** test instances: replay5 findings, paraphrases, model hallucination/repair/fallback, per-target configs/colors, eligibility/cards/text, category contamination, taxonomy failure, T25/T29. |
| `debug/post-upgrade-fixes/harness/` | Reproducible focused runtime, cleanup, report generation. Chỉ harness; không sửa harness/reports cũ. |

Không đổi `QueryPlan`, `ProductSelector`, `ProductSearchPlan`, public enum/API, prompt/schema Ollama, ChatService orchestration contract, RealProductAdvisor eligibility/stock/ranking, Java, authentication, schema hoặc migration. Luồng Ollama hiện tại đã gọi normalizer cho cả valid JSON và repair/fallback; không cần kéo dài system prompt. Internal Enum/dataclasses không mở rộng wire contract.

## 4. Before/after QueryPlans và mandatory evidence

Bảng “Before” là plan sau normalization cũ; “After” là runtime mới. Model plan ban đầu, grounding, response đầy đủ nằm trong [before_after.json](evidence/fix01/before_after.json). Không đánh đồng historical trace với execution mới.

| Case | Original Fault | Before QueryPlan | After QueryPlan | Expected | Actual | Verdict | Trace/Evidence |
|---|---|---|---|---|---|---|---|
| N03 | F01: Lenovo bị bỏ, SKU do model bịa | PRODUCT_DISCOVERY; Laptop; RAM>=16; top_k=5 | UNKNOWN + clarification; product_search=null | Clarify; không card hãng khác | 200; 0 cards; 0 eligible variants | PASS | `bd8df813-f1cd-4f9a-8f6b-eacef072810e` [trace](evidence/fix01/runtime/evidence/traces/N03.json) |
| N04 | F02: mất Laptop | PRODUCT_DISCOVERY; Dell; max=20000000; top_k=5 | PRODUCT_DISCOVERY; Laptop; Dell; max=20000000; top_k=5 | Laptop + Dell + max20M | 200; 1 cards; 1 eligible variants | PASS | `3ef16ba5-b6b6-4f0d-8d6c-8864f764f21f` [trace](evidence/fix01/runtime/evidence/traces/N04.json) |
| N12 | F03: mất RAM/storage từng target | PRODUCT_COMPARE; Điện thoại; Xiaomi,Apple; Xiaomi 15,iPhone 15; top_k=2 | PRODUCT_COMPARE; Điện thoại; Xiaomi,Apple; Xiaomi 15/12/256,iPhone 15/6/128; top_k=2 | Đúng 12/256 và 6/128; 2 cards | 200; 2 cards; 2 eligible variants | PASS | `9ba065e8-7c3a-4019-be8e-79c42b7cca47` [trace](evidence/fix01/runtime/evidence/traces/N12.json) |
| N46 | F08: maxRAM bị bỏ | PRODUCT_DISCOVERY; Laptop; max=20000000; top_k=5 | UNKNOWN + clarification; product_search=null | Clarify trước product retrieval | 200; 0 cards; 0 eligible variants | PASS | `4c865395-3e26-4f3a-afab-13a81d07a66c` [trace](evidence/fix01/runtime/evidence/traces/N46.json) |
| N47 | F09: count2 thành5/3cards | PRODUCT_DISCOVERY; Điện thoại; Xiaomi; top_k=5 | PRODUCT_DISCOVERY; Điện thoại; Xiaomi; top_k=2 | top_k2; tối đa2 sản phẩm Xiaomi | 200; 2 cards; 6 eligible variants | PASS | `14172550-0a62-49b4-b665-2545bfb9ee89` [trace](evidence/fix01/runtime/evidence/traces/N47.json) |

## 5. Per-target validation

Tên catalog được resolve theo vị trí xuất hiện, không theo thứ tự backend trả về. Mỗi span sau tên đến target kế tiếp được parse RAM/storage/color riêng. Với N12: Xiaomi15→RAM12/storage256, iPhone15→RAM6/storage128. Compact `12GB/256GB` và dạng có nhãn đều hỗ trợ. Không truyền cấu hình A sang B; target không được nêu cấu hình vẫn không bị gán cấu hình từ target khác.

So sánh có global configuration mơ hồ, capacity minimum riêng mỗi target, nhiều giá riêng hoặc không rõ lượng GB thuộc RAM/storage thì clarify, vì contract hiện tại chỉ có exact per-target và global minima. Không biến exact comparison thành discovery toàn bộ variants. RealProductAdvisor hiện có dùng `_target_variants` và `_eligible`; build_cards refresh cùng evidence cho text/cards. N12 final có đúng hai ProductMatches, mỗi match một EligibleVariant và hai cards.

Synthetic unit fixture thêm candidate Dell ngoài category Laptop, deliberately trả cả hai từ structured adapter: sau eligibility/enrichment chỉ còn laptop; message không chứa điện thoại. Nhờ vậy N04 không chỉ “pass do catalog hiện tại chưa có Dell phone”. Không ghi fixture này vào business DB.

## 6. Hard-filter preservation rules

1. Category synonym được map về tên thật của active taxonomy: Laptop/notebook/máy tính xách tay; Điện thoại/Smartphone/phone; tablet chỉ được dùng khi taxonomy hiện tại có danh mục tương ứng. Category model phải có căn cứ trong utterance; không xem string bất kỳ là canonical.
2. Brand lấy từ active taxonomy, không từ danh sách năm hãng hiện tại. Hỗ trợ fixture Framework để chứng minh. Unknown stated brand ở slot sau category, explicit hãng/thương hiệu/brand hoặc model brand có mặt trong utterance phải clarify. Use-case boundary như để lập trình/gaming/phục vụ học tập không phải identity. SKU/ID chỉ tạo từ literal user, không nhận SKU model bịa.
3. CapacityConstraint giữ quan hệ EXACT/MINIMUM/MAXIMUM/RANGE/STRICT_MINIMUM trong nội bộ; wire contract không đổi. Discovery “RAM16GB” tiếp tục nghĩa RAM>=16 theo tương thích hiện có; exact target RAM16GB nghĩa đúng16. “Đúng RAM16GB” mà thiếu identity thì hỏi rõ.
4. Price parser phân biệt GB/TB với tiền, đọc VND/tr/triệu và price range. Multiple bounds cùng loại được lấy giao (max lower/min upper); mâu thuẫn thì hỏi rõ. Không lấy số lượng2 hay model15 làm giá/RAM.
5. Requested cardinality được đọc độc lập từ utterance, giới hạn1–20; không tin top_k do model tự thêm. Default5 khi user không nêu count. Invalid JSON, repair thất bại hoặc Ollama unavailable vẫn đi qua cùng normalizer. ProductSearchPlan.top_k được real advisor áp lên số products, không số variants; thiếu kết quả được nói rõ.
6. ProductEvidence kiểm tra equality phạm vi category/brands/targets/price/minima và count bound trước trả normalized plan; tests cố tình broaden target hoặc top_k để chứng minh gate từ chối. Backend eligibility và live card refresh tiếp tục là lớp kiểm chứng cuối.
7. Backend/taxonomy lỗi/rỗng/incomplete không thành “không có sản phẩm”: propagate availability error để ChatService trả503. Unknown brand chỉ là chưa xác minh trong catalog, không khẳng định hãng đó không tồn tại toàn cầu.

## 7. Unsupported-condition behavior

RAM/SSD upper bound (“dưới”, “phải dưới”, “chỉ”, “không quá”, “tối đa”, <=, under/at most), capacity range hoặc strict greater-than được nhận diện rồi trả UNKNOWN+clarification, product_search=null. Không thêm maxRAM/minus/exclusion backend fields; không đổi nghĩa thành minimum. Các condition thiếu slot, màu không xác định, loại trừ brand/category cũng clarify thay vì bỏ lọc.

Runtime N03/N46 không có effective product search, ProductMatch hoặc card; chỉ có catalog reads cần cho validation. Những trường hợp cần resolver như “Bản512GB” được để lại cho owned snapshot resolver; không dùng số512 làm global filter của một loại sản phẩm chưa biết. Sau resolve, backend kiểm chứng identity/variant và tồn kho hiện tại.

## 8. Tests, integration và runtime

Môi trường thật: Python3.12.15; Qwen `qwen3:4b-instruct`; embedding `qwen3-embedding:0.6b`; real Product Advisor; tracing enabled; PostgreSQL durable history; Spring Boot thật trên disposable pgvector clone. Clone có16 products/38 variants, hai warehouses; dùng selected warehouse hiện có. Knowledge DEMO hiện có, không tạo chính sách mới. Synthetic CUSTOMER từ repository, không đưa credentials/IDs cá nhân vào dossier. [Environment](evidence/fix01/environment_sanitized.json).

| Execution | Kết quả cuối | Evidence |
|---|---|---|
| Focused QU/contracts/ProductAdvisor/ChatFlow/tracing | 169 passed,0 failed,0 skipped | [output](evidence/commands/fix01-focused-acceptance.txt) |
| PostgreSQL knowledge/persistence group | 20 passed,0 failed,0 skipped, gồm5 PG integration tests | [output](evidence/commands/fix01-postgres-acceptance.txt) |
| Full chatbot Python suite | 340 passed,0 failed,0 skipped | [output](evidence/commands/fix01-full-python-acceptance.txt) |
| Real Qwen + Spring + isolated PostgreSQL | 35 distinct cases:33 PASS,1 warning,1 deferred;0FAIL/blocked | [results](evidence/fix01/runtime/results.json), [trace index](evidence/fix01/runtime/trace_index.json) |
| Git protected reports / diff --check | exit0 | [commands](evidence/commands.json) |

Các nhóm pytest có overlap, không cộng169+20+340 thành tổng unique tests. Có một Starlette/httpx deprecation warning. Smoke run không truyền DB env từng có104 passed/4 skipped; các skip đó được chạy lại với isolated DSN và final suite không skip. Các focused run trung gian140/153/161/167 và full330/338 là snapshot trong lúc thêm test, không thay thế kết quả final340. Initial harness import-path error được sửa trước khi thu baseline5 failures; đó không phải lỗi app.

Java không đổi: environment package backend thành công (exit0), không tuyên bố đã rerun Java tests ở Fix01. Frontend không đổi và không rerun build/component/browser suite; frontend-visible ChatResponse/message/cards được xác minh qua API. Không coi mocked models/MockTransport là real Qwen evidence.

Historical regressions được chạy mới T07–T22,T23–T30,T51–T56. T51 tiếp tục yêu cầu xác nhận typo an toàn và ghi warning; T56 tiếp tục DEFERRED_APPROVED_SCOPE, không được đánh PASS cho multi-intent.

| Case | Intent actual | Cards | Verdict | Evidence |
|---|---|---:|---|---|
| T07 | PRODUCT_DISCOVERY | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T07.json) |
| T08 | PRODUCT_DISCOVERY | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T08.json) |
| T09 | PRODUCT_DISCOVERY | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T09.json) |
| T10 | PRODUCT_DISCOVERY | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T10.json) |
| T11 | PRODUCT_DISCOVERY | 3 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T11.json) |
| T12 | PRODUCT_DISCOVERY | 4 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T12.json) |
| T13 | PRODUCT_DISCOVERY | 4 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T13.json) |
| T14 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T14.json) |
| T15 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T15.json) |
| T16 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T16.json) |
| T17 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T17.json) |
| T18 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T18.json) |
| T19 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T19.json) |
| T20 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T20.json) |
| T21 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T21.json) |
| T22 | PRODUCT_COMPARE | 2 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T22.json) |
| T23 | PRODUCT_DISCOVERY | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T23.json) |
| T24 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T24.json) |
| T25 | PRODUCT_DISCOVERY | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T25.json) |
| T26 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T26.json) |
| T27 | PRODUCT_DISCOVERY | 3 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T27.json) |
| T28 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T28.json) |
| T29 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T29.json) |
| T30 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T30.json) |
| T51 | UNKNOWN | 0 | PASS_WITH_WARNINGS | [trace](evidence/fix01/runtime/evidence/traces/T51.json) |
| T52 | PRODUCT_DETAIL | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T52.json) |
| T53 | PRODUCT_DISCOVERY | 3 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T53.json) |
| T54 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T54.json) |
| T55 | PRODUCT_DISCOVERY | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/T55.json) |
| T56 | UNKNOWN | 0 | DEFERRED_APPROVED_SCOPE | [trace](evidence/fix01/runtime/evidence/traces/T56.json) |
| N03 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/N03.json) |
| N04 | PRODUCT_DISCOVERY | 1 | PASS | [trace](evidence/fix01/runtime/evidence/traces/N04.json) |
| N12 | PRODUCT_COMPARE | 2 | PASS | [trace](evidence/fix01/runtime/evidence/traces/N12.json) |
| N46 | UNKNOWN | 0 | PASS | [trace](evidence/fix01/runtime/evidence/traces/N46.json) |
| N47 | PRODUCT_DISCOVERY | 2 | PASS | [trace](evidence/fix01/runtime/evidence/traces/N47.json) |

## 9. Evidence cho từng case bắt buộc

### N03

Prompt: Tìm laptop Lenovo RAM 16GB.

Expected: Clarify; không card hãng khác.

Actual (PASS):

```text
Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong catalog. Bạn vui lòng xác nhận hãng/tên hoặc SKU; mình sẽ không thay bằng hãng khác.
```

HTTP 200; 1959.37ms; trace `bd8df813-f1cd-4f9a-8f6b-eacef072810e`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.

### N04

Prompt: Tìm máy tính xách tay Dell dưới 20 triệu.

Expected: Laptop + Dell + max20M.

Actual (PASS):

```text
Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
```

HTTP 200; 2032.7ms; trace `3ef16ba5-b6b6-4f0d-8d6c-8864f764f21f`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.

### N12

Prompt: So sánh Xiaomi 15 bản 12GB/256GB với iPhone 15 bản 6GB/128GB.

Expected: Đúng 12/256 và 6/128; 2 cards.

Actual (PASS):

```text
Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
iPhone 15
- DEV-IP15-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 17,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
```

HTTP 200; 2490.94ms; trace `9ba065e8-7c3a-4019-be8e-79c42b7cca47`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.

### N46

Prompt: Tìm laptop dưới 20 triệu nhưng RAM phải dưới 16GB.

Expected: Clarify trước product retrieval.

Actual (PASS):

```text
Bộ lọc dung lượng tối đa/khoảng/giới hạn nghiêm ngặt chưa được hỗ trợ. Bạn vui lòng dùng mức tối thiểu GB/TB hoặc một biến thể cụ thể.
```

HTTP 200; 2595.92ms; trace `4c865395-3e26-4f3a-afab-13a81d07a66c`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.

### N47

Prompt: Chỉ gợi ý đúng 2 điện thoại Xiaomi, nếu không đủ thì nói rõ.

Expected: top_k2; tối đa2 sản phẩm Xiaomi.

Actual (PASS):

```text
Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi 14T Pro
- DEV-X14TP-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 16,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-GRY-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 19,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
```

HTTP 200; 5507.39ms; trace `14172550-0a62-49b4-b665-2545bfb9ee89`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.

## 10. Regressions phát hiện và sửa trong triển khai

Không che các lần chạy chưa đạt. Có bốn development replay batches, mỗi batch cùng35 prompts; chỉ final batch là ma trận acceptance35 distinct. Các batch trước là rerun/debug, không tăng coverage thành140 cases và không dùng để suy luận statistical reliability.

- [Attempt01](evidence/fix01/attempt-01/results.json): T25 hỏi rõ quá mức do grammar thiếu filler “vẫn”. Initial evaluator đã thiếu assertion giữ RAM/cheaper baseline; được siết theo expected đã đóng băng và record được đánh FAIL với lý do, không thay đổi expected hoặc response quan sát. Unit test mới bắt filler và không trộn assistant variant facts vào user filters.
- [Attempt02](evidence/fix01/attempt-02/results.json): T25 và toàn bộ batch hoạt động; sau đó thêm capacity coverage/exclusion protection.
- [Attempt03](evidence/fix01/attempt-03/results.json): coverage gate chặn T29 “Có phiên bản512GB không?” trước resolver. Chia sẻ storage_reference grammar với resolver hiện có; scoped literal của một reference không bị coi là global search. Thêm test có/không có snapshot.
- [Final](evidence/fix01/runtime/results.json): T25 giữ RAM>=16 và max_price15989999, backend không có kết quả; T29 resolve đúng product trước và trả hai variants512GB; T26 không bịa sản phẩm thứ hai. Hash production source lúc final runtime được kiểm tra trùng source hiện tại.

Các sửa này thuộc query/reference integrity, không triển khai thêm persistent-memory hay multi-intent feature. Frozen prompts/expected trong cases.json không đổi giữa các batches.

## 11. Remaining limitations

- Đây là bounded deterministic slot grammar + model extraction + backend verification, không phải chứng minh hỗ trợ mọi cách diễn đạt tiếng Việt/Anh. Unknown identity hoặc cấu hình thiếu slot được xử lý bảo thủ, có thể hỏi lại dù người dùng thấy tên đó rõ.
- Chưa có maxRAM, per-target minimum/range/price, arbitrary Boolean filter, nhiều SKU trong một comparison hoặc summary/multi-intent execution. Không thêm contracts chưa duyệt. Một số ngữ pháp cấu hình/màu ngoài vocabulary được hỏi lại thay vì bỏ lọc.
- Taxonomy còn trang sau100 brands hiện trả availability error, chưa triển khai full brand pagination; catalog/identity cap500 vẫn giữ nguyên. Cần review contract completeness/latency trước mở rộng.
- Price bounds tiếp tục inclusive theo contract/backend hiện có; không bổ sung strict-VND API. ProductCard vẫn một representative eligible variant mỗi product; message có thể liệt kê nhiều eligible variants nhưng luôn ghi đúng SKU/config/price/stock.
- Không rerun toàn bộ120-case diagnostic vì checkpoint này yêu cầu focused query/product regression. Các lỗi ngoài phạm vi như missing-payment/null warehouse order mapping, lexical RAG, transaction guard false positive, orphan USER persistence, RAM feedback/trace, official corpus missing còn nguyên phạm vi review trước.
- Test fixtures synthetic/unit và isolated DB; không có live active-promotion matrix hoặc load/reliability claim. Không tuyên bố production-ready.

## 12. Compatibility risks và cleanup

Public schemas/enums/API không đổi, không SQL do Qwen sinh, không migrations/Java/business mutations. Behavior chủ ý chặt hơn: unknown identity, unsupported filter hoặc taxonomy incomplete dừng/clarify thay vì broad search. Unknown brand sẽ không trả nhầm Dell/ASUS/Apple. Downstream phải tiếp tục tôn trọng `clarification`; current resolver đã có guard không overwrite domain errors và tests xác minh.

Chỉ app stack/container của diagnostic được dừng/xóa; source business fingerprint trước/sau bằng nhau, source DB running state được khôi phục, reports gốc không đổi. [Cleanup proof](evidence/fix01/cleanup.json). Không để fixture trên business DB, không thay authentication/ownership hoặc normal application processes.

## 13. Final HANDOFF cho review

**C2C EXECUTED, chưa accepted.** N03/N04/N12/N46/N47 đều có executed deterministic regression và real-stack trace PASS. 89 test instances mới; full340 passed. 35 real cases gồm33PASS/1warning/1deferred. Không commit/push; không triển khai Fix02.

Reviewer nên xem target scoping/grammar conservatism, unknown-brand ambiguity policy, taxonomy coverage error và semantics count/variant trước khi chấp thuận. [Diff/status](evidence/fix01/final_git_status.txt), [before/after](evidence/fix01/before_after.json), [machine summary](evidence/fix01/summary.json).
