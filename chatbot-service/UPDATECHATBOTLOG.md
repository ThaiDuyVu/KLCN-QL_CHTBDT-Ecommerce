# Nhật ký nâng cấp chatbot (UPDATECHATBOTLOG)

Ghi lại từng giai đoạn nâng cấp trợ lý AI: nâng cấp gì, cải thiện điều gì, file chính, cách kiểm chứng và giới hạn còn lại. Mục mới nhất ở trên cùng. Mỗi lần nâng cấp chatbot đều phải thêm một mục vào đây.

Cách kiểm thử ngữ cảnh dùng chung cho các giai đoạn:

```bash
chatbot-service/.venv/bin/python -m pytest -q chatbot-service/tests                        # unit
chatbot-service/.venv/bin/python chatbot-service/debug/context-eval/run_context_eval.py     # hội thoại thật, bộ chính
chatbot-service/.venv/bin/python chatbot-service/debug/context-eval/run_context_eval.py --holdout   # câu chữ mới, kiểm tra tổng quát hóa
```

---

## GĐ 11 — Sửa 6 lỗi ưu tiên của benchmark và lưu phản hồi tin nhắn (27/10/2026)

Thuộc kế hoạch sửa hệ thống, mục S1.1 và S1.3 (`docs/bao-cao/KE_HOACH_SUA_HE_THONG.md`).

**Lỗi đã sửa** (mã nguyên nhân theo `debug/benchmark/BAO_CAO_KET_QUA_BENCHMARK.md`):
- **R1 – số trong tên model bị đọc thành giá.** "giá iPhone 15" từng thành bộ lọc 13–17 đồng. Nay một con số không có đơn vị chỉ được coi là tiền khi giữa "giá"/"ngân sách" và con số chỉ có từ bổ nghĩa ("khoảng", "dưới", "là"…). `constraints._unitless_price_context`.
- **R2 – "bản 512GB" kèm tên sản phẩm bị chặn.**
  - Khi câu đã nêu tên, SKU hoặc id sản phẩm, "bản N GB/TB" (N ≥ 64GB) là bộ nhớ của sản phẩm đó (`variant_storage_wording`).
  - "bản 16GB" vẫn hỏi lại vì có thể là RAM.
  - Dung lượng nằm trong mã SKU (`DEV-ROGG14-WH-1TB`) không còn bị coi là điều kiện.
- **R3 – tên sản phẩm thiếu hãng.**
  - "Galaxy S24", "Inspiron 15", "XPS 13" được viết lại thành tên catalog (`normalization.expand_brandless_names`), dựa trên danh mục hãng thật.
  - Phần tên còn lại phải có chữ cái, nên "15" của Xiaomi 15 không ăn vào "iPhone 15".
  - Câu chỉ có tên rút gọn không còn bị chặn ở cổng đầu (`_mentions_product_name`).
- **R5 – từ chối giao dịch.** Nhận thêm "đặt giúp/đặt luôn/đặt cho mình/mua giúp/thêm vào giỏ". "Đắt" cũng thành "dat" khi bỏ dấu, nên chỉ nhận khi đi kèm từ nhờ vả hoặc số lượng, và không nhận "đắt cho mình quá".
- **R6 – mã đơn không tồn tại.** Câu có mã `ORD-…` luôn đi vào tra cứu đơn của chính khách, nên mã sai được báo "không tìm thấy", thay vì liệt kê đơn khác.
- **R7 – định tuyến câu chính sách.**
  - Câu hỏi thông tin cửa hàng (giờ mở cửa, nơi bảo hành, giao tận nơi, hóa đơn VAT) và câu hướng dẫn thuộc chủ đề có trong kho tri thức đi vào RAG; không có tài liệu thì chatbot nói chưa đủ dữ liệu.
  - "Cách hủy đơn hàng" (chưa có tài liệu) giữ câu từ chối cũ (N38).

**Nâng cấp – S1.3:** đánh giá tin nhắn được lưu vào bảng `message_feedback` thay vì bộ nhớ.
- Ghi: "hữu ích" → 5, "chưa hữu ích" → 1, bỏ đánh giá → xóa dòng.
- Đọc lại: 4–5 → hữu ích, 1–2 → chưa hữu ích.
- Trang `/status` báo `feedback_storage: message_feedback`.

**Kết quả:**

| Bộ đo | Trước (GĐ 10) | Sau |
|---|---|---|
| Unit test | 618 đạt | **649 đạt** (+31 ca, `tests/test_benchmark_priority_fixes.py`, test phản hồi PostgreSQL) |
| Context-eval bộ chính / holdout | 50/50 · 18/18 | **54/54 lượt · 18/18 lượt** |
| Benchmark tổng | 84,5 | **91,9 / 100 (9,2/10 – Xuất sắc)** |
| — Năng lực cốt lõi | 85,8 | **96,8** |
| — Ngữ cảnh nâng cao | 54,5 | **59,1** |
| — An toàn | 99,3 | **100,0** |

- **Thay đổi giữa hai lần chạy:** 16 lượt chuyển từ trượt sang đạt, **0 lượt chuyển từ đạt sang trượt**. Lần chạy: `debug/benchmark/runs/20261027T110447Z`.
- **Nhóm đạt 100%:** G3, G6, G7, G8 (trước lần lượt là 72%, 92%, 62%, 88%).

**Còn lại** (nhóm B/C, kế hoạch S5.3):
- so sánh 3 máy (G5-06);
- "laptop rẻ nhất";
- đơn vị USD;
- lỗi gõ "laptp", "xiaomi15", "lap 1tb", "bản 16/512";
- ngữ cảnh nâng cao G4b.

**Kiểm chứng phản hồi PostgreSQL:** test chạy trên một CSDL tạm, tạo từ migration V1–V8 và xóa sau khi chạy. Cách chạy lại: đặt `CONVERSATION_TEST_DATABASE_URL` trỏ tới CSDL tạm đó.

---

## GĐ 10 — Benchmark baseline: 205 case / 290 lượt (27/10/2026)

**Đánh giá** (không thay đổi mã chatbot): bộ benchmark `debug/benchmark/` gồm 11 nhóm (G1–G10 + G4b). Đặc tả ở `BENCHMARK_CASES.md`; chạy bằng `run_benchmark.py`.

**Kết quả: 84,5/100 (8,5/10 — Tốt).**

| Thành phần | Điểm |
|---|---|
| Năng lực cốt lõi | 85,8 |
| Ngữ cảnh nâng cao | 54,5 |
| An toàn | 99,3 |

- **Đạt 100%**: G1 tìm sản phẩm, G2 giá/combo, G4 ngữ cảnh cơ bản.
- **Thấp nhất**: G7 chính sách (62%), G5 so sánh (67%), G3 chi tiết sản phẩm (72%).
- **An toàn**: 0 lượt bịa số liệu, 0 thay đổi dữ liệu.

**Lỗi chính phát hiện** (chi tiết trong `debug/benchmark/BAO_CAO_KET_QUA_BENCHMARK.md`):
- Số trong tên model bị đọc thành giá ("giá iPhone 15" → 13–17 đồng): **hồi quy do GĐ 8**.
- "bản 512GB" kèm tên sản phẩm bị chặn.
- Tên sản phẩm thiếu hãng ("Galaxy S24") không nhận ra.
- Thiếu từ chối "đặt giúp tôi…".
- Định tuyến sai vài câu chính sách.

Đây là mốc so sánh cho các giai đoạn sau.

---

## GĐ 9 — So sánh sản phẩm bằng thông số kỹ thuật (26/10/2026)

**Nâng cấp**
- So sánh 2–3 sản phẩm **cùng danh mục** bằng số liệu catalog: giá, RAM, bộ nhớ, CPU, màn hình, pin, bảo hành, tồn kho tại chi nhánh.
- API trả thêm trường `comparison`. Trang `/chatbot` hiển thị **bảng so sánh**: mỗi sản phẩm một cột, ô có thông số đo được tốt hơn được tô và gắn nhãn "Tốt hơn". Bảng vừa khung trên điện thoại.
- Phần "Tóm lại" ngắn gọn, chỉ nêu khác biệt đo được, ví dụ "Dell XPS 13 có RAM cao hơn (16GB–32GB so với 8GB)". Thông số dạng chữ (CPU, pin) chỉ hiển thị, không phán "tốt hơn". Màn hình lớn hơn được nêu như một sự thật, không phải ưu điểm.
- Hai sản phẩm **khác danh mục** (ví dụ iPhone 15 với Dell XPS 13): giải thích mỗi máy thuộc nhóm nào và **từ chối so sánh**, không hiện thẻ.
- Nhận ra câu so sánh không có chữ "so sánh": "X với Y cái nào hơn", "X vs Y", "X khác gì Y".

**Cải thiện / lỗi đã sửa**
- Trước đây câu "so sánh iPhone 15 với Dell XPS 13" báo sai "chưa có biến thể đang bán". Nguyên nhân: từ khóa "iphone" gán bộ lọc danh mục Điện thoại nên Dell XPS 13 bị loại. Câu so sánh có tên sản phẩm cụ thể giờ không gán danh mục.
- Trước đây câu trả lời so sánh chỉ liệt kê phiên bản của từng máy, không đặt cạnh nhau.

**Quy tắc "tốt hơn"**: một sản phẩm chỉ thắng khi *mọi* cấu hình của nó vượt *mọi* cấu hình của máy kia. Cấu hình chồng lấn thì không phân thắng thua. Giá so theo giá khởi điểm ("từ X").

**Ghi chú mở rộng danh mục**: tiêu chí so sánh khai báo theo từng danh mục trong `app/features/comparison/profiles.py`. Laptop, Điện thoại, Tablet dùng bộ "thiết bị công nghệ". Khi cửa hàng thêm danh mục mới, chỉ cần thêm profile, ví dụ:
- Tủ lạnh: dung tích (lít, số càng lớn càng tốt), nhãn năng lượng (chữ).
- TV: kích thước màn hình (inch), độ phân giải (chữ).

Danh mục chưa có profile tự dùng giá, bảo hành, tồn kho và các thông số chung của sản phẩm (chỉ hiển thị).

**Chưa làm (để sau)**: so sánh theo hãng ("laptop Dell với laptop ASUS"); "so sánh 2 máy vừa gợi ý" (tham chiếu danh sách).

**File chính**: `app/features/comparison/{profiles.py,builder.py}`, `app/shared/contracts/comparison.py`, `app/shared/formatting.py`, `query_understanding/normalization.py`, `chat/service.py`, `response_generation/ollama_service.py`; frontend `Fontend/src/features/chatbot/{CustomerChatParts.jsx,customerChat.js,CustomerChatbotPage.jsx,CustomerChatbotPage.css}`.

**Kiểm chứng**: unit 618 passed (+7 `tests/test_comparison.py`); frontend 159 passed (+4 `CustomerComparison.test.jsx`), lint và build sạch; context-eval bộ chính không còn FAIL (+C20–C22), holdout 18/18; chụp màn hình bảng so sánh ở 1440px và 390px, không tràn trang, không lỗi JS.

---

## GĐ 8 — Combo theo ngân sách và giá "khoảng X" (26/10/2026)

**Nâng cấp**
- **Combo nhiều loại**: "Giờ tôi có 100tr tôi cần mua 1 điện thoại và 1 laptop" → tìm riêng từng loại, chọn tổ hợp **còn hàng** có tổng gần ngân sách nhất mà không vượt. Câu trả lời liệt kê từng món, tổng cộng và số tiền còn dư. Không có tổ hợp nào vừa thì đưa combo rẻ nhất và nói rõ vượt bao nhiêu.
- **Cách hiểu giá** (chốt các quyết định DR-01/DR-02 trước đây còn treo):
  - "khoảng / tầm / cỡ / giá X" = X ± 15%, máy có giá gần X xếp trước.
  - "ngân sách X", "tôi có X" = tối đa X.
  - "rẻ / thấp / ít hơn X" = dưới X, không tính đúng X.
- "dt" được hiểu là điện thoại; viết "rẽ" vẫn hiểu là "rẻ".

**Cải thiện / lỗi đã sửa**: "laptop khoảng 25tr" trước đây bị báo "Thương hiệu chưa xác minh", vì bộ đoán hãng lấy chữ ngay sau "laptop" ("khoảng", "tầm", "ngân") làm tên hãng.

**Giới hạn**: combo chỉ 1 sản phẩm mỗi loại; chưa hỗ trợ RAM/dung lượng riêng cho từng loại trong combo (chatbot sẽ hỏi lại). Biên độ "khoảng" chỉnh ở `APPROXIMATE_TOLERANCE` (`constraints.py`).

**File chính**: `product_advisor/bundle.py` (mới), `query_understanding/{constraints,normalization}.py`, `product_advisor/real_service.py`, `chat/service.py`, `response_generation/ollama_service.py`, `shared/contracts/query.py` (`target_price`, `bundle`, `budget`; hai trường combo ẩn khỏi schema gửi LLM).

**Kiểm chứng**: unit 611 passed (+`tests/test_bundle_and_price.py`); context-eval 50/50, holdout 18/18.

---

## GĐ 7 — Bộ kiểm thử ngữ cảnh và sửa các điểm yếu hội thoại nhiều lượt (21/10/2026)

**Nâng cấp**: tạo bộ kiểm thử hội thoại thật `debug/context-eval/`: 15 kịch bản ban đầu, chấm theo catalog thật và trace, kèm bộ holdout. Sửa các điểm yếu:
- **Câu nối ngắn giữ điều kiện cũ**: "chỉ Apple thôi", "dưới 35 triệu", "của ASUS", "RAM 16GB trở lên".
- **Đại từ và câu hỏi tiếp nối**: "nó / máy này / con đó", "con nào rẻ nhất", "cái nào RAM nhiều hơn", "còn mẫu nào khác" (loại sản phẩm đã hiển thị).
- **Bảo hành**: "máy này bảo hành mấy tháng" không còn bị tra nhầm bảo hành của tài khoản.
- **Nhớ danh sách gần nhất** (`list_cards`) khi khách đang xem chi tiết 1 máy, nên "máy đầu tiên" vẫn đúng.
- **Mốc giá**: mốc do lần so sánh trước tự sinh không bị kế thừa sang lần sau.
- **Bộ đoán hãng** không còn báo nhầm ("gợi ý laptop…", "xem điện thoại Samsung", "vài điện thoại Apple"); "mẫu" không bị hiểu là "màu".

**Kết quả**: 22/43 (51%) → 42/43; holdout 18/18. Chi tiết: `debug/context-eval/FINDINGS.md`.

---

## GĐ 6 — Hiểu "mắc hơn / đắt hơn" (21/10/2026)

**Nâng cấp**: thêm loại tham chiếu `PRICIER`, đối xứng với "rẻ hơn". Lấy giá cao nhất vừa hiển thị làm mốc và giữ danh mục/RAM của lần tìm trước.

**Lỗi đã sửa**: "có sản phẩm nào mắc hơn xíu" trước đây làm mất ngữ cảnh và hỏi lại từ đầu.

---

## GĐ 5 — Câu trả lời sản phẩm tự nhiên hơn (21/10/2026)

**Cải thiện**:
- Phần chữ của câu trả lời sản phẩm viết như hội thoại: có câu mở đầu theo loại câu hỏi và câu kết gợi ý bước tiếp.
- Giá theo định dạng `12.990.000 đ` như thẻ sản phẩm.
- Bỏ mã SKU khỏi phần chữ, vì thẻ sản phẩm đã hiển thị.

---

## GĐ 4 — Trang trợ lý AI cho khách hàng `/chatbot` (10/2026)

**Nâng cấp**: tab "Trợ lý AI" trên storefront. Có danh sách cuộc trò chuyện, lịch sử, thẻ sản phẩm, nguồn tham khảo, gợi ý câu hỏi, đóng phiên, xử lý mất kết nối và hết thời gian chờ.

---

## GĐ 2–3 — Nghiệm thu Phase 2 và hotfix giỏ hàng (trước 21/10/2026, commit `5dcbd73`)

**Nâng cấp**: sửa trường hợp giỏ hàng trống đã xác minh (`VerifiedEmptyCart`); nghiệm thu bộ 120 tình huống. Kết quả: PASS_WITH_CONDITIONS / sẵn sàng UAT có điều kiện. Báo cáo: `debug/production-readiness/03_PHASE2_ACCEPTANCE_REPORT.md`.

## GĐ 1 — Khắc phục sẵn sàng production, Phase 1 (commit `df64381`)

**Nâng cấp**:
- Bộ đọc giá có quan hệ (dưới/trên/khoảng).
- Guard chỉ-đọc và phân tích phạm vi hành động.
- Kiểm chứng tất định theo catalog thật (`ProductEvidence`).
- Khóa theo phiên chat.
- Tracing có che dữ liệu nhạy cảm, kiểm tra cấu hình production.

Báo cáo: `debug/production-readiness/02_FIX_EXECUTION_REPORT.md`.
