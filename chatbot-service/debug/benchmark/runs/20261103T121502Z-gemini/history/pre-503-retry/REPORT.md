# Gemini cloud benchmark

Model: `gemini-3.1-flash-lite`; bắt đầu: 2026-11-03T12:15:02.884262+00:00; commit `371934c1ba7e0b7806c40731687decf547d5893f`.

**INCOMPLETE** — 1/290 lượt, 1/205 case đã bắt đầu.
Verdicts: {'PASS': 0, 'FAIL': 0, 'INFO': 0, 'BLOCKED_ENVIRONMENT': 1}. Gemini calls quan sát: 1; quota dự phòng đã tính: 1; token: 0.
Dừng: HTTP_503: dừng, không retry/fallback..

Chạy tuần tự; không retry; không đổi provider; lưu sau từng lượt. Giữ nguyên cases/graders của benchmark local.
Nghỉ 65 giây sau mỗi lượt; trong một lượt pipeline vẫn có thể gọi model tối đa 3 lần (QU repair + generation).
User báo quota 15 request/phút, 250.000 token/phút. Mặc định 20 giây giữa các lượt: dự phòng tối đa khoảng 9 call/phút với contract hiện tại, chừa khoảng trống cho lượt gọi khác.
Trần token là ngưỡng quan sát sau response, không phải hard billing cap. Hạn mức Google thực theo project/model chưa được xác minh.
Không chấm điểm toàn bộ khi chưa chạy đủ; lượt chưa chạy là NOT_RUN, không phải PASS.

| Case | Lượt | Verdict | Trace | Thời gian |
|---|---|---|---|---|
| G1-01 | 1 | BLOCKED_ENVIRONMENT | `a84653b1-952f-4412-b67b-20fcec394164` | 3.48s |

Kết quả local tham chiếu: `../20261027T110447Z/results.json`. Khác ngày/code/data nên không quy mọi chênh lệch cho model.
Evidence chi tiết trong `results.json` và `evidence/`. Không chứa API key, cookies hoặc Authorization headers.
Giỏ/đơn/bảo hành so sánh fingerprint trước/sau; read-only snapshot có giới hạn phân trang của tool cũ, không phải audit toàn DB.
Business fingerprint thay đổi: False.
