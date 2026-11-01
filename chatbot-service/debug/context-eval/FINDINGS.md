# Context eval — kết luận

## Kết quả sau khi sửa

| Lần chạy | Bộ test | PASS |
|---|---|---|
| `20261021T121112Z` (trước sửa) | `cases.json` | 22/43 (51%) |
| `20261021T122809Z` (sau sửa) | `cases.json` | **42/43 (98%)** |
| `20261021T123020Z-holdout` (sau sửa) | `cases_holdout.json`: 18 lượt, câu chữ mới không dùng khi sửa | **18/18** |

### Cập nhật 26/10: combo theo ngân sách và giá "khoảng"

| Lần chạy | Bộ test | PASS |
|---|---|---|
| `20261026T122817Z` | `cases.json` (thêm C16–C19: combo, khoảng giá, mức giá cố định) | **50/50** |
| `20261026T123230Z-holdout` | `cases_holdout.json` | **18/18** |

- **Quyết định đã chốt** (trước đây là DR-01/DR-02, `PENDING_APPROVAL`):
  - "khoảng / tầm / cỡ / giá X" = X ± 15%, xếp theo độ gần X.
  - "ngân sách X", "tôi có X" = giá tối đa X.
  - "rẻ / thấp / ít hơn X" = dưới X, không tính đúng X.
- **Combo nhiều loại** ("100tr mua 1 điện thoại và 1 laptop"): normalizer tạo `bundle`, mỗi loại một lần tìm. `product_advisor/bundle.py` chọn tổ hợp còn hàng có tổng gần ngân sách nhất mà không vượt. Nếu không có tổ hợp nào vừa thì đưa combo rẻ nhất kèm ghi chú vượt ngân sách. Chưa hỗ trợ: nhiều máy cùng loại, cấu hình RAM/dung lượng riêng cho từng loại; với các câu đó chatbot sẽ hỏi lại.
- **"laptop khoảng 25tr" trước đây lỗi** vì bộ đoán hãng lấy "khoảng / tầm / ngân" làm tên hãng. Các từ chỉ giá đã được thêm vào danh sách loại trừ. "dt" giờ được hiểu là điện thoại.
- Lượt C14.2 bên dưới đã PASS sau khi chốt DR-01.

Lượt còn FAIL (lần chạy 21/10): C14.2 "tăng ngân sách lên 25 triệu". Cách hiểu "ngân sách X" là quyết định DR-01, vẫn `PENDING_APPROVAL`; mã cố ý hỏi lại thay vì tự đoán.

| Điểm yếu | Cách sửa | File |
|---|---|---|
| W1 câu nối ngắn mất điều kiện | `refine()`: câu không nêu danh mục mới, chỉ nêu hãng/giá/RAM/dung lượng/"bỏ điều kiện giá", được ghép vào bộ lọc của snapshot. Nêu danh mục thì vẫn là yêu cầu mới | `conversation/references.py` |
| W2 đại từ, so sánh nhất, "mẫu khác" | Nhận diện "nó / máy này / con đó", "rẻ nhất / đắt nhất / RAM nhiều hơn", "mẫu khác". Câu "máy này bảo hành…" không còn bị chuyển sang bảo hành của tài khoản. "Mẫu khác" loại các sản phẩm đã hiển thị | `constraints.py`, `references.py`, `chat/service.py` |
| W3 bộ chặn hãng báo nhầm | Bỏ qua từ đệm/động từ trước danh mục ("gợi ý", "xem", "vài"). Không coi hãng mà LLM ghi nhầm thành tên sản phẩm là model lạ. Không lọc thêm theo hãng khi đã có tên sản phẩm cụ thể. "mẫu khác" không còn bị hiểu là màu | `constraints.py`, `normalization.py` |
| W4 mốc giá bị kế thừa | Snapshot chỉ lưu cận giá do người dùng nêu (`carry_min_price` / `carry_max_price`) | `references.py`, `chat/service.py` |
| W5/W6 phạm vi tham chiếu | Snapshot có thêm `list_cards`: danh sách nhiều sản phẩm gần nhất, giữ nguyên khi khách hỏi chi tiết 1 sản phẩm trong đó. "Máy đầu tiên", "cái nào rẻ hơn" dùng danh sách này; "nó" dùng sản phẩm vừa xem. "Cái nào rẻ hơn" là chọn trong danh sách; "có cái nào rẻ hơn không" là tìm mới | `conversation/models.py`, `references.py`, `chat/service.py` |

Unit test: 600 passed (có thêm `tests/test_context_followups.py`, 23 test).

---

# Lần chạy đầu (run `20261021T121112Z`)

Bộ test: `cases.json` (15 kịch bản, 46 lượt, 43 lượt được chấm). Chạy thật trên Vite 5173 → chatbot 8090 → Spring 8080 + Ollama `qwen3:4b-instruct`, tài khoản seed `seed.customer1`. Chi tiết từng lượt: `runs/20261021T121112Z/REPORT.md`.

**Kết quả: 22 PASS / 21 FAIL (51%).** Mọi FAIL đều đã được đối chiếu với câu trả lời và trace thật. C05.3 có kỳ vọng còn tranh cãi; C02.3, C08.2, C08.3 bị ảnh hưởng dây chuyền từ lượt trước.

## Điểm làm tốt

- Tham chiếu thứ tự khi có nhiều thẻ: "máy thứ hai" (C05.2), "cái đầu tiên lúc nãy" cách 3 lượt (C10.4).
- So sánh giá đơn: "mắc hơn xíu" (C01.2, C13.3), "rẻ hơn" sau khi chen câu hỏi giỏ hàng (C09.3).
- Đổi hãng nhưng vẫn giữ danh mục khi có chữ "còn … thì sao" (C03.2).
- Sửa điều kiện bằng một câu đầy đủ (C12.2). Yêu cầu mới không kế thừa hãng cũ (C15.2).

## Điểm yếu, xếp theo nguyên nhân

| # | Điểm yếu | Lượt | Nguyên nhân (theo trace) |
|---|---|---|---|
| W1 | **Câu nối ngắn chỉ nêu 1 điều kiện làm mất các điều kiện còn lại** | C03.3 "Apple thì có mẫu nào", C07.3 "còn bản RAM 16GB thì sao", C12.3 "của ASUS", C14.2 "tăng ngân sách lên 25 triệu", C14.3 "chỉ Apple thôi", C02.3 "dưới 35 triệu" | `normalization._normalize` dựng bộ lọc chủ yếu từ câu hiện tại. Kế thừa từ lượt trước chỉ chạy cho câu so sánh giá/thứ tự. Bộ lọc thực tế chỉ còn `brands=[Apple]` hoặc `min_ram_gb=16`, mất `category`, `max_price`, sản phẩm đang hỏi |
| W2 | **Đại từ và câu hỏi tiếp nối không theo mẫu có sẵn** | C06.2 "nó còn hàng không", C06.3 "máy này bảo hành mấy tháng", C13.2 "con nào rẻ nhất", C11.2 "còn mẫu nào khác không", C08.3 "cái nào RAM nhiều hơn" | `detect_reference` chỉ biết thứ tự, "vừa gợi ý", rẻ/đắt hơn, dung lượng. Không có "nó / máy này / con đó", không có so sánh nhất ("rẻ nhất"), không có "khác". C06.3 còn bị định tuyến nhầm sang bảo hành của tài khoản (`WARRANTY_STATUS`) |
| W3 | **Bộ chặn hãng/tên sản phẩm báo nhầm ở câu đơn**, làm đứt cả chuỗi phía sau | C02.1 "gợi ý laptop RAM 16GB", C02.2 "chỉ lấy hãng Dell thôi", C04.2 "giờ tôi muốn xem điện thoại Samsung", C08.1 so sánh XPS 13 với MacBook Air M2 | C02.1, C02.2, C04.2: LLM trả `UNKNOWN`, normalizer trả "Thương hiệu hoặc tên sản phẩm … chưa xác minh". C08.1: bộ lọc có thêm `brands=[Dell]` nên MacBook Air M2 bị loại |
| W4 | **Mốc giá của lần so sánh trước bị kế thừa sang lần so sánh sau** (lỗi do bản sửa "mắc hơn" vừa làm) | C01.3 "rẻ hơn chút", C01.4 "rẻ hơn nữa" | Sau "mắc hơn", snapshot lưu `min_price=15.990.001`. "Rẻ hơn" giữ `min_price` kế thừa nên loại Dell Inspiron 15 (15,99 triệu), dù catalog có |
| W5 | **Tham chiếu dung lượng/thứ tự khi phạm vi không rõ** | C02.4 "bỏ điều kiện giá, có bản 1TB không" → hỏi lại "máy nào"; C05.3 "máy đầu tiên" → lấy thẻ của lượt chi tiết vừa xong, không phải danh sách ban đầu | Hệ thống chỉ dùng snapshot gần nhất. Với C05.3, kỳ vọng còn tranh cãi |
| W6 | **"Cái nào rẻ hơn" trong ngữ cảnh so sánh bị hiểu là "tìm máy rẻ hơn"** | C08.2 | Được nhận là `CHEAPER` (đi tìm sản phẩm mới) thay vì chọn 1 trong 2 sản phẩm đang so sánh. Lần này lỗi còn dây chuyền từ C08.1 |

## Gợi ý thứ tự sửa

1. **W4:** sửa nhỏ, cùng chỗ vừa sửa. So sánh giá chỉ nên kế thừa mốc giá mà người dùng tự nêu, không kế thừa mốc sinh ra từ lần so sánh trước.
2. **W1:** giá trị lớn nhất. Với câu nối ngắn (có hãng/giá/RAM nhưng không có danh mục mới), ghép vào bộ lọc của snapshot trước rồi ghi đè đúng điều kiện được nêu.
3. **W3:** bộ chặn hãng/tên đang chặn cả câu hợp lệ. Cần xem trace từng câu.
4. **W2:** thêm "nó / máy này / con đó", "rẻ nhất / đắt nhất", "mẫu khác".
5. **W5, W6:** cần quyết định nghiệp vụ trước khi sửa.

Chạy lại sau mỗi lần sửa: `chatbot-service/.venv/bin/python chatbot-service/debug/context-eval/run_context_eval.py` (thêm mã kịch bản, ví dụ `C01 C14`, để chạy một phần). Kết quả có thể dao động nhẹ vì LLM.
