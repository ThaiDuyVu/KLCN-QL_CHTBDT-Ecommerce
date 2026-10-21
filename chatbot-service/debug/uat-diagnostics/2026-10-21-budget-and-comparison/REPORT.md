# Chẩn đoán hai tình huống UAT: ngân sách 20tr và so sánh với MacBook

Ngày phân tích:21/10/2026, timezone Asia/Ho_Chi_Minh. Chỉ debug/đọc/đối chứng, **không sửa chatbot, prompt, contracts hoặc dữ liệu nghiệp vụ**. Hai file cart/service đang sửa sẵn được giữ nguyên. Source Git `df6438156589155510dcd88ba983aab852df87d9` + worktree hiện tại; [hash/source](source.json). Các lượt trong ảnh được nhận diện bằng đúng session và exact prompt trong log gốc; không chỉ suy luận từ screenshot.

## 1. Kết luận trực tiếp

**Tình huống 1:** bạn đã nêu laptop,20triệu và mục đích học CNTT. Qwen thực tế hiểu đúng. **Bộ chuẩn hóa của ứng dụng bác bỏ cách nói “đang có20tr”, xóa QueryPlan rồi trả câu hỏi chung có sẵn.** Câu trả lời kém vì hỏi lại cả những điều bạn đã nói; không phải do backend/Ollama mất kết nối.

**Tình huống 2:** DB vẫn giữ snapshot đúng Dell Inspiron15 vừa gợi ý, nhưng luồng so sánh **chưa ghép được một target từ ngữ cảnh với một target mới**. Đồng thời **catalog thật không có MacBook AirM1**. Hệ thống không phân biệt/giải thích hai việc này mà trả chung “hãy xác định đúng hai sản phẩm”. Không có căn cứ để bịa thông số M1; câu trả lời đúng cần chỉ rõ Dell đã biết và M1 thiếu dữ liệu.

Đối chứng thêm phát hiện một lỗi riêng: ngay cả khi đổi sang **MacBook AirM2 có thật** và viết đầy đủ cả hai tên, globalfilter`Dell` loại MacBook Apple. Lỗi này không gây trực tiếp câu M1 trong ảnh (vì case đó chưa tới retrieval), nhưng cần ghi riêng vì nó làm flow comparison vẫn hỏng sau khi qua bước nhận diện.

## 2. Trace gốc của đúng các câu trong ảnh

| Tình huống | Giờ Việt Nam | Trace ID | History messages | HTTP / nhánh cuối |
|---|---|---|---:|---|
| Câu đầu20tr | 21/10/2026 13:19:59 UTC+7 | `c264dcb2-38a8-4521-ac81-25ce11780767` | 0 |200 / deterministic clarification|
| Trả lời lại20tr | 21/10/2026 13:21:28 UTC+7 | `66b04315-8e0d-402f-9c6e-d7669510feb7` | 2 |200 / deterministic clarification|
| So sánh sau một Dellcard | 21/10/2026 13:23:49 UTC+7 | `94e19c12-5e80-4dab-a874-89fddb77e252` | 10 |200 / deterministic clarification|

[Original session excerpts](original-session-excerpts.json) giữ thứ tự events, QueryPlan model/normalized, counts, response và selected history; đã bỏ customer/user/session identifiers, credentials và raw personal DTO. Trace gốc nằm trong `.local/logs/chatbot-traces.jsonl`, không sao chép raw file vào report.

**HTTP200, trace.status=ok và badge “AI thật” không đánh giá chất lượng câu trả lời.** Nhánh`response.strategy` là `deterministic_guard/clarification`; [response generator](../../../app/features/response_generation/ollama_service.py:25) trả nguyên `query_plan.clarification`. Câu hỏi chung trong ảnh do code chọn, không phải Qwen Response Generation tự viết một câu tư vấn mới.

## 3. Case1 — Vì sao đã nói đủ nhưng bot hỏi lại?

Prompt gốc:

> tôi đang có 20tr tư vấn giúp tôi một laptop học công nghệ thông tin

Chuỗi thực tế:

```text
User: Laptop +20tr + học CNTT
→ Qwen JSON: PRODUCT_DISCOVERY / category=Laptop / max_price=20000000
→ price_evidence: co20tr → APPROXIMATE, unresolved
→ validate_price_coverage: raise ConstraintError(GENERAL_CLARIFICATION)
→ normalized QueryPlan: UNKNOWN, product_search=null
→ Product Advisor không chạy, semantic ranking không chạy, GroundedContext.products=[]
→ response.generator trả đúng generic clarification
```

Model raw/validated đã có:

```json
{"intent":"PRODUCT_DISCOVERY","product_search":{"category":"Laptop","max_price":20000000,"semantic_query":"laptop học công nghệ thông tin"}}
```

Sau domain validation:

```json
{"intent":"UNKNOWN","clarification":"Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?","product_search":null}
```

### Nguyên nhân kỹ thuật chính xác

[PRICE_OPERATORS](../../../app/features/query_understanding/constraints.py:143) có`co` trong nhóm approximate. Sau bỏ dấu, **“có” và “cỡ” cùng thành`co`**; cụm “đang có20tr” bị đọc như số tiền xấp xỉ. Amount20tr được nhận diện; không phải parser không biết đơn vị`tr`. Nhưng amount chưa được phê duyệt làm cận giá nên bị đưa vào`unresolved`, không thành max_price.

[validate_price_coverage](../../../app/features/query_understanding/constraints.py:226) từ chối mọi unresolved amount. [DomainNormalizer.normalize](../../../app/features/query_understanding/normalization.py:40) bắt ConstraintError và thay toàn bộ plan bằng UNKNOWN. Bộ chuẩn hóa không chỉ hỏi lại ngân sách; nó làm mất cả category/use-case đã rõ.

Có lý do thiết kế cho việc fail-safe này: report Phase1 tại `debug/production-readiness/02_FIX_EXECUTION_REPORT.md` nêu **DR-01 (budget/approximate) và DR-13b (wording) còn PENDING**, nên dùng đúng generic clarification có sẵn. Đây là chính sách thận trọng đã được triển khai, nhưng trải nghiệm UAT hiện tại rất kém. Không nên vá bằng cách tin mọi slot model hoặc tự chọn khoảng±X% khi chưa duyệt.

### Vì sao câu thứhai cũng lặp lại?

“20tr , hoc cong nghe thong tin” không có operator (`dưới`, `tối đa`...), nên parser ghi`UNSPECIFIED`. Trace đã có2messages lịch sử và Qwen vẫn trả Laptop/max20000000; validator lại bác bỏ. **History có được nạp**, không phải bot mất session; nó bị phủ nhận ở lớp chuẩn hóa. Vì dừng ở price coverage, chưa quan sát được downstream category resolution của câu này.

[Parser probes](parser-and-normalizer-probes.json):

| Cách nói | Parser relation | Kết quả |
|---|---|---|
| đang có20tr |approximate/unresolved|hỏi lại chung|
|20tr |unspecified/unresolved|hỏi lại chung|
| ngân sách20triệu |budget/unresolved|DR-01 pending|
| tầm20triệu |approximate/unresolved|DR-01 pending|
| dưới20triệu |maximum=20000000|chạy được|

Đối chứng realtime “Tìm laptop dưới20triệu để học công nghệ thông tin.” trả PRODUCT_DISCOVERY và2card hợp lệ. Điều này khoanh vùng lỗi ở cách đọc/validation ngữ nghĩa budget, không phải không có laptop hoặc search/index chết.

## 4. Case2 — Vì sao “sản phẩm trên” không được so sánh?

Prompt gốc sau một card Dell:

> so sánh sản phẩm trên với macbook air m1

### Bằng chứng context còn nguyên

[Owned snapshot](owned-snapshot.json) kiểm ownership thật và đọc envelope ASSISTANT được persist lúc13:23:37, trước request13:23:49: có đúng một card **Dell Inspiron15 / DEV-DELL-I15-512**, RAM16GB/512GB,15.990.000đ. Trace querycontext có10messages, gồm chính câu gợi ý Dell đó. Không có dấu hiệu context bị reset/RAM mất dữ liệu ở case này.

### Các điểm nghẽn thực tế

1. Qwen nhận `PRODUCT_COMPARE`, nhưng plan sau validate chỉ có một target SKU Dell; M1 không thành target độc lập. Đây là extraction chưa đầy đủ trong lần model cụ thể; không nên chỉ dựa vào nó để kết luận toàn lỗi thuộc model.
2. [Normalizer](../../../app/features/query_understanding/normalization.py:71) dựng targets từ **tên catalog xuất hiện trong current_message**, không ghép owned snapshot. Trước khi resolver chạy, [guard comparison](../../../app/features/query_understanding/normalization.py:151) yêu cầu đúng2targets; thiếu thì đổi UNKNOWN + generic comparison clarification.
3. [detect_reference](../../../app/features/query_understanding/constraints.py:247) không có pattern “sản phẩm trên”. Pattern hiện hỗ trợ một số “đầu tiên/thứhai/vừa gợi ý/...”. Probe hiện tại trảNone cho exact câu trong ảnh.
4. Đổi sang “máy vừa gợi ý” có nhận LAST, nhưng [resolver guard](../../../app/features/conversation/references.py:45) không được vượt clarification “xác định đúng hai sản phẩm” vì không nằm trong whitelist. Nó vẫn dừng. Ngoài ra nhánh LAST hiện resolve thành **PRODUCT_DETAIL một target**, chưa phải mixed-target comparison.
5. [ChatService](../../../app/features/chat/service.py:178) gọi normalizer thông qua QU trước, rồi mới get_snapshot/resolve_reference. Tới lúc này plan đã bị chặn. Không có `product.search`, semantic ranking hoặc product cards ở original case.

Controlled replay cấp sẵn một QueryPlan có đủ DellSKU + **M2 có thật**, kèm snapshot đúng, vẫn bị UNKNOWN ở dạng tham chiếu. Vì vậy **chỉ sửa prompt Qwen hoặc thêm một regex “sản phẩm trên” chưa đủ**; cần sửa phối hợp normalizer→reference resolver→comparison completeness.

### M1 có trong dữ liệu không?

**Không có trong catalog backend của đúng request gốc**. Trace`backend.product_result` chứa16/16products,1page; MacBook chỉ gồm **MacBook AirM2** và **MacBook Pro14M3**. Đọc backend hiện tại cũng cho cùng hai mẫu. [Catalog evidence](catalog-evidence.json).

Do đó không thể hứa trả comparison đầy đủ với M1 từ business evidence hiện tại. Nhưng câu hỏi chung “hãy nêu cả hai tên” vẫn không đúng trọng tâm: một bên đã rõ, bên còn lại đã có tên nhưng thiếu dữ liệu catalog. Cần phân biệt **reference unresolved**, **named product not in catalog**, **ambiguous variant** và **backend unavailable** thay vì gộp vào cùng UNKNOWN.

## 5. Lỗi kèm được xác minh bằng đối chứng

### F04 — Budget lịch sử bị xóa ở follow-up

Trong cùng session, “laptop dell” có modelplan max_price20000000 từ history, nhưng normalizedplan max_price=None. Kết quả có DellXPS13 giá32.990.000đ trở lên. Đây là trace`ed2aa839-1920-4cc6-bb19-0ca28de2c892`; current-only normalization làm mất slot đã có trong hội thoại. Cần chính sách kế thừa slot có provenance theo session/scope, không lấy textASSISTANT làm nguồn giá hiện tại.

### F05 — So sánh Dell với Apple bị global brand filter chặn

Đối chứng đầy đủ “So sánh Dell Inspiron15 với MacBook AirM2.” có hai tên thật, normalize thành PRODUCT_COMPARE, nhưng`brands=[Dell]`. [RealProductAdvisor._eligible](../../../app/features/product_advisor/real_service.py:46) áp globalbrand cho mọi target, loại Apple và raise VARIANT_NOT_FOUND. Câu trả lời “chưa có biến thể ... phù hợp” che khuất việc filter bị áp sai phạm vi.

Trace`6383fb47-7d5e-4db9-a53f-67acfbfeeaf8`. [Pure eligibility probe](cross-brand-probe.json) trên detail backend thật: **DEV-MBA-M2-256 ACTIVE**, actual plan →False; chỉ bỏ brandconstraint trong **controlled in-memory probe** →True. Không sửa plan của request production và không đổi inventory. Lỗi này được ghi riêng, không quy nó làm nguyên nhân của requestM1 vốn chưa tới retrieval.

### F06 — “16G ram” mất hard filter

Trace`cd16696d-2011-42c6-878b-82a1b058db22`: model đọc min_ram16 nhưng normalizer đổiNone. Probe `capacity('16G ram')=(None,False)` còn `RAM16GB` và `16GB RAM` ra16. Card trong ảnh tình cờ RAM16GB không chứng minh filter được giữ. Đây là vấn đề grammar/coverage shorthand, không phải nguyên nhân trực tiếp của câu clarification đầu.

## 6. Các lượt tái hiện và kết quả

10HTTPrequests mới trên stack thật,2lượt là setup riêng. Tất cả dùng synthetic CUSTOMER và **session mới**, không POST thêm vào session trong screenshot. Chỉ đọc catalog/owned snapshot và gọi chat; không gọi order/payment/cart mutation. Các pure parser/normalizer/eligibility probes được gắn nhãn CONTROLLED, không giả làm QwenE2E.

| Run | Prompt | HTTP | Plan sau chuẩn hóa | Cards | Ý nghĩa |
|---|---|---|---|---|---|
| A1 | tôi đang có 20tr tư vấn giúp tôi một laptop học công nghệ thông tin | 200 | UNKNOWN | 0 | Tái hiện budget bị hỏi lại toàn bộ |
| A2 | 20tr , hoc cong nghe thong tin | 200 | UNKNOWN | 0 | Tái hiện vòng lặp hỏi lại trong cùng session |
| A-control | Tìm laptop dưới 20 triệu để học công nghệ thông tin. | 200 | PRODUCT_DISCOVERY | 2 | Giới hạn tối đa20triệu tường minh chạy được |
| B-setup | Gợi ý cho tôi một laptop Dell RAM 16GB dưới 20 triệu. | 200 | PRODUCT_DISCOVERY | 1 | Setup một card Dell |
| B1 | so sánh sản phẩm trên với macbook air m1 | 200 | UNKNOWN | 0 | M1 không có catalog + reference không resolve |
| B-control | So sánh Dell Inspiron 15 với MacBook Air M1. | 200 | UNKNOWN | 0 | Không coi là positive control hợp lệ: M1 không có catalog |
| C-setup | Gợi ý cho tôi một laptop Dell RAM 16GB dưới 20 triệu. | 200 | PRODUCT_DISCOVERY | 1 | Setup một card Dell |
| C-ref | so sánh sản phẩm trên với MacBook Air M2 | 200 | UNKNOWN | 0 | M2 tồn tại nhưng reference vẫn bị chặn |
| C-last | so sánh máy vừa gợi ý với MacBook Air M2 | 200 | UNKNOWN | 0 | Cụm vừa gợi ý nhận LAST nhưng vẫn bị chặn |
| C-explicit | So sánh Dell Inspiron 15 với MacBook Air M2. | 200 | PRODUCT_COMPARE | 0 | Đủ hai tên; qua QU nhưng lỗi global brand lọc Apple |


[Realtime six requests](live-reproduction.json), [M2 controls four requests](supported-live-controls.json), [plans frozen trước gửi](reproduction-plan.json), [supported control plan](supported-control-plan.json). B-control với M1 không được coi là positive comparison test hợp lệ vì fixture không có M1; điều này được công khai, không đổi kỳ vọng sau khi thấy output. Chạy control mới với M2 để tách lỗi context khỏi thiếu dữ liệu.

Không chạy lại fullpytest/120case vì nhiệm vụ này chỉ chẩn đoán và không sửa business logic. Không có tuyên bố “testPASS” dựa trên HTTP200. Các lỗi quan sát được giữ nguyên để review.

## 7. Vì sao UI báo AI thật nhưng câu trả lời vẫn kém?

- AI thật nghĩa là Qwen sẵn sàng và được dùng ở các stage cần model, không bảo đảm mỗi message cuối được model viết hay semantic interpretation đúng.
- Trong ba câu hỏi lại ở ảnh, response generator chỉ trả chuỗi clarification do code đặt; backend products không vào GroundedContext.
- `query.validation` là plan đọc từ model sau schema validation; **`query.plan.initial` thực tế được log sau normalizer và reference resolver**, dù nhãn TracePanel gọi là “QueryPlan do model tạo”. Chỉ xem initial có thể nhầm lỗi application thành lỗi model. Cần đối chiếu raw output/validation/domainvalidation.
- Các early-return comparison không phát `query.domain_validation` như constraint-error path; report kết hợp exact source + controlled replay, không suy diễn event bị thiếu thành stage không chạy.
- Khi productretrieval thành công, generator cũng thường dùng deterministic SKU/price/stock listing; hiện chưa bảo đảm tư vấn sâu cho nhu cầu họcCNTT hoặc so sánh ưu/nhược điểm. Không nên coi dòng catalog list là đã hoàn tất task tư vấn.

## 8. Hướng khắc phục đề xuất — chưa triển khai

| Ưu tiên | Việc cần làm | Regression cần có |
|---|---|---|
|P1|Chốt DR-01/DR-13b cho “đang có/ngân sách/tầm”, phân biệt có/cỡ saufolding; không tự đặt±%|Exact hai câu trong ảnh, bare20trfollowup; chỉ hỏi slot còn mơ hồ; vẫn giữ price0/upper/lower/range constraints|
|P1|Giải quyết target tham chiếu trước comparison completeness, giữ target mới và intentCOMPARE|Một card Dell + M2; một card + M1absent; nhiều cards phải hỏi chọn; nohistory; stale price/stock phải refreshbackend|
|P1|Per-target brand/variant scope cho comparison|Dell+Apple, Apple+Xiaomi, samebrand; không làm yếu brandfilters của discovery|
|P1|State/slot provenance cho hội thoại ngắn hạn, có scope-change rules|Budget→brandfollowup không rơi giá; current explicit override; không carry filters sang nhu cầu mới sai scope|
|P2|Coverage cho shorthand RAM được duyệt|16G ram/RAM16G/16GBRAM; exact variant vs minimum; không vô tình nhận unrelated numbers|
|P2|Clarification đúng phần thiếu và trace label đúngstage|Đã biết laptop/budget/usecase không hỏi lại cả3; biếtDellthiếuM1 chỉ nói phầnthiếu; original-vs-effectivePlan rõràng|

Ví dụ wording **đề xuất**, không phải kết quả đã sửa:

- “Mình đã rõ bạn cần laptop học công nghệ thông tin.20triệu là ngân sách tối đa bạn muốn chi, đúng không?”
- “Mình hiểu bạn muốn so sánh Dell Inspiron15 vừa gợi ý với MacBook AirM1. Hiện catalog chưa có dữ liệu M1 để so sánh chính xác. Bạn muốn chọn mẫu khác có trong catalog, chẳng hạn AirM2?”

M2 cũng còn lỗi globalbrand đã nêu; ví dụ wording không phải cam kết flow đó hiện đã PASS. Không thêm specs/giá của M1 bằng trí nhớ model. Các quyết định semantic/wording còn pending phải review trước sửa.

## 9. Phạm vi bằng chứng và trạng thái repo

Đã dùng skill codebase-memory qua localCLI ([graph evidence](graph-evidence.json)): list_projects/index_status, schema, targeted search, trace_path(direction=both), snippet và coveragecheck. Graphgeneration20/10/2026 bị metadata_changed ở các evidencepaths; graph chỉ dùng để định hướng. Mọi kết luận về price/compare được kiểm lại trên **file hiện tại**, không dựa vào negative graph lookup stale. Không tự coi graph là exhaustive.

Có một lỗi chạy script chẩn đoán ban đầu: cwdrepo khiến get_settings không đọc chatbot-service/.env, nên chưa thể mở repository. Chỉ sửa harness sang explicit envfile rồi chạy lại; không sửa app/config. Rawtrace/session/credentials không xuất ra public report. Existing uncommitted cart hotfix và Phase2reports giữ nguyên. Các service vẫn chạy cho bạn tiếp tục kiểm thử.

[Findings machine-readable](findings.json), [source hashes](source.json). Không commit/push và chưa thực hiện bất kỳ hướng sửa nào trong mục8.
