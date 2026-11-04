# Benchmark Gemini có giới hạn lượt gọi

Bản cloud là `run_cloud_benchmark.py`; giữ nguyên `run_benchmark.py`, các case và grader local. Bộ test hiện tại có 205 case / 290 lượt (bao gồm multi-turn). Không tự chạy lại case trượt chất lượng như tool local, không chuyển provider để che lỗi. Riêng lỗi Gemini HTTP 503 có retry giới hạn.

## Policy

- Một POST chat tại một thời điểm; khóa file `.local/cloud-benchmark.lock` ngăn hai bản tool cloud chạy song song trên workspace này.
- User cung cấp quota: 15 request/phút, 250.000 token/phút. Chưa biết RPD. Theo [Google](https://ai.google.dev/gemini-api/docs/rate-limits), hạn mức thực theo project/model và xem tại AI Studio; tool không truy cập dashboard billing để xác minh.
- Sau phản hồi, nghỉ ít nhất **20 giây** trước POST kế tiếp (có thể tăng bằng `--interval`). Một POST có thể chứa tối đa 3 lần gọi model theo implementation hiện tại: 1 QU + 1 repair + 1 response generation. Vì thế dự phòng khoảng 9 model call/phút khi chạy tuần tự, dưới quota user cung cấp. Không thể bảo đảm quota nếu key/project còn được ứng dụng khác dùng đồng thời hoặc contract số model call thay đổi.
- Budget mặc định **50 lần gọi model** trên toàn run, bao gồm resume, dự phòng trước mỗi lượt 3 lần. Không đủ phần dự phòng thì dừng trước POST; đây không phải quota ngày của Google. Giảm budget nếu muốn giữ lại RPD cho thao tác tay.
- Ngưỡng **100.000 token được báo cáo** trên toàn run; kiểm tra sau response trước lượt tiếp theo. Đây là ngưỡng dừng mềm, một response có thể vượt ngưỡng. Không phải hard TPM/billing cap; Google vẫn thực thi TPM thực. Không có token usage khi upstream lỗi thì không suy diễn usage bằng 0.
- Với **503 thực từ Gemini**, retry tối đa **5 lần/case**, chờ **30 → 60 → 120 → 240 → 300 giây**, cộng jitter 0–5 giây. Dùng `--max-503-retries 0` để tắt; tối đa cho phép 10. Không retry chỉ dựa vào HTTP 503 của chatbot vì đó có thể là lỗi backend khác.
- Dừng khi 429/quota, lỗi khác, thiếu trace, provider sai/Ollama chat hoặc cạn budget. Hết 5 retries cũng dừng và giữ checkpoint, không quay vòng vô hạn, không tự chạy sang ngày khác.
- **Session mới cho mỗi case retry.** Backend đã lưu USER trước khi gọi model, nên lặp POST vào session cũ sẽ làm bẩn history. Nếu case nhiều lượt bị 503, lưu toàn bộ attempt (kể cả các lượt trước đó thành công) vào `attempts/`, rồi chạy lại case từ đầu ở session mới. Chỉ attempt hoàn tất được chấm cuối; retry không được dùng để chọn câu trả lời tốt hơn khi model trả 200 nhưng nội dung sai.
- Mỗi lần lỗi/replay đều tính vào budget và lưu evidence riêng theo trace ID, không ghi đè bằng chứng cũ. Thời điểm retry, retry count và tổng budget được lưu qua resume.
- Nếu timeout/mất tiến trình sau khi đã gửi POST, giữ reservation/inflight và không tự gửi lại request chưa rõ kết quả. Review trace trước khi tiếp tục.
- Giữ `.env`, key và cookie ngoài artifacts; output chỉ dữ liệu development synthetic. Không bật billing, không thay config hoặc logic chatbot.

## Chạy và lưu kết quả

Từ repo root:

```sh
chatbot-service/.venv/bin/python chatbot-service/debug/benchmark/run_cloud_benchmark.py --plan-only
chatbot-service/.venv/bin/python chatbot-service/debug/benchmark/run_cloud_benchmark.py
```

Lệnh thứ hai gọi API thật. Mặc định chỉ chạy đến budget hoặc circuit breaker, không hứa chạy đủ 290 lượt trong Free tier. Kết quả ghi vào `runs/<UTC timestamp>-gemini/`, gồm `results.json`, `REPORT.md`, `evidence/<case>-<turn>.json`. Checkpoint sau mỗi lượt giữ session/history để có thể tiếp tục multi-turn sau khi tool dừng. Không ghi đè benchmark local.

Resume thủ công, chỉ khi đã xem nguyên nhân dừng và có đủ quota:

```sh
chatbot-service/.venv/bin/python chatbot-service/debug/benchmark/run_cloud_benchmark.py \
  --resume chatbot-service/debug/benchmark/runs/<timestamp>-gemini \
  --interval 20 --max-model-calls 50 --max-tokens 100000
```

Resume giữ ngân sách đã dùng, kiểm tra model, source hash, cases và business fingerprint không đổi. Các case hoàn tất không gửi lại. Case BLOCKED do Gemini 503 có bằng chứng sẽ được archive và retry trong session mới, theo giới hạn còn lại. BLOCKED do 429 hoặc lỗi khác không tự retry. Không sửa kết quả lịch sử để biến nó thành PASS. Không tự tăng budget khi hết lượt. Toàn bộ 290 lượt cần tối thiểu gần 97 phút nghỉ với interval 20 giây, cộng thời gian xử lý; có thể phải chia nhiều đợt theo RPD.

Tool đọc catalog từ container `ecommerce-postgres` và snapshot tài khoản synthetic theo tool local. Chỉ ghi chat session/history/trace, không tạo đơn/thay giỏ/bảo hành. Fingerprint business chỉ kiểm tra phần snapshot có giới hạn pagination; không coi là audit toàn database. Dữ liệu thay đổi do người khác có thể ảnh hưởng fingerprint và so sánh.

## Lần thực thi đầu tiên — 03/11/2026

- Run: `runs/20261103T121502Z-gemini/`.
- Lần này bắt đầu với policy thận trọng ban đầu: nghỉ 65 giây, budget 50 calls / 100.000 tokens. Sau khi user cung cấp RPM/TPM, default của tool được điều chỉnh thành 20 giây cho lần chạy sau; không ghi đè policy đã thực thi.
- Case `G1-01`, prompt **`laptop Dell`**.
- Trace `a84653b1-952f-4412-b67b-20fcec394164`.
- Gemini `gemini-3.1-flash-lite` thực sự được gọi 1 lần, Google trả **HTTP 503** sau khoảng 3,38 giây; API chatbot 503, phía client 3,48 giây.
- Trace lỗi tại Query Understanding; chưa có QueryPlan/retrieval/response. Không có token usage trả về. Không có bằng chứng đây là lỗi key/quota; mã 503 chỉ service unavailable.
- Tool tự dừng ngay, không retry và không fallback. **1 lượt BLOCKED_ENVIRONMENT, 289 lượt NOT_RUN**; 204 case chưa bắt đầu. Không chấm điểm chất lượng toàn bộ, chưa thể so sánh với local.
- Fingerprint giỏ/đơn/bảo hành/phiếu trước/sau không đổi.
- Log thực thi: `.local/logs/cloud-benchmark.log`. Tiến trình benchmark đã dừng; các project vẫn chạy.

## Kiểm tra tool offline

```sh
chatbot-service/.venv/bin/python -m pytest chatbot-service/debug/benchmark/test_cloud_benchmark.py -q
```

Kết quả sau bổ sung retry: **15 passed**. Kiểm tra budget dự phòng, token threshold, giãn cách cả khi resume, giữ inflight sau timeout, không retry 429, chỉ retry 503 có trace chứng minh, backoff có trần, multi-turn retry bằng session mới, provider mismatch, thiếu trace và báo cáo partial không tự nhận điểm toàn bộ. Chạy `--plan-only` không gọi API. Không chạy full test suite hoặc benchmark local lại.

## Tiếp tục toàn bộ benchmark với retry

Sau khi đã biết quota RPM/TPM, có thể cấp budget đủ cho toàn bộ bài (vẫn dừng nếu Google trả 429/RPD):

```sh
chatbot-service/.venv/bin/python chatbot-service/debug/benchmark/run_cloud_benchmark.py \
  --resume chatbot-service/debug/benchmark/runs/20261103T121502Z-gemini \
  --interval 20 --max-503-retries 5 \
  --max-model-calls 1000 --max-tokens 2000000
```

Đây là giới hạn do tool đặt, không phải nâng quota Google hoặc bật billing. Không bảo đảm chạy hết nếu RPD Free tier không đủ hoặc 503 kéo dài. Có thể Ctrl+C để giữ checkpoint; khi đang có POST chưa rõ kết quả, không tự gửi lại. Log retry không thay đổi logic ứng dụng chính.
