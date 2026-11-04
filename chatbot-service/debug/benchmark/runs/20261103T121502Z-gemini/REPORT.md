# Gemini cloud benchmark

Model: `gemini-3.1-flash-lite`; bắt đầu: 2026-11-03T12:15:02.884262+00:00; commit `371934c1ba7e0b7806c40731687decf547d5893f`.

**INCOMPLETE** — 38/290 lượt, 38/205 case đã bắt đầu.
Verdicts: {'PASS': 37, 'FAIL': 0, 'INFO': 0, 'BLOCKED_ENVIRONMENT': 1}. Gemini calls quan sát: 38; quota dự phòng đã tính: 41; token: 60773.
Dừng: G2-18: retry exhausted or non-retryable cloud failure; review evidence..

Chạy tuần tự; retry 503 tối đa 5 lần/case; không đổi provider; lưu sau từng lượt.
Số case retry đã lên lịch: 1. Lần lỗi lưu riêng trong attempts/, không tính là kết quả chất lượng cuối.
Nghỉ 20.0 giây sau mỗi lượt; trong một lượt pipeline vẫn có thể gọi model tối đa 3 lần (QU repair + generation).
User báo quota 15 request/phút, 250.000 token/phút. Mặc định 20 giây giữa các lượt: dự phòng tối đa khoảng 9 call/phút với contract hiện tại, chừa khoảng trống cho lượt gọi khác.
Trần token là ngưỡng quan sát sau response, không phải hard billing cap. Hạn mức Google thực theo project/model chưa được xác minh.
Không chấm điểm toàn bộ khi chưa chạy đủ; lượt chưa chạy là NOT_RUN, không phải PASS.

| Case | Lượt | Verdict | Trace | Thời gian |
|---|---|---|---|---|
| G1-01 | 1 | PASS | `724c972e-a7c7-49fd-8be8-8f3f28f39488` | 42.38s |
| G1-02 | 1 | PASS | `5b7d85e4-8430-49ac-8f65-98952675317d` | 28.29s |
| G1-03 | 1 | PASS | `2d9c880a-9aa6-42b9-a592-6b8d771b025e` | 26.3s |
| G1-04 | 1 | PASS | `9f92b1d8-57b4-4d53-8689-95816e7b621d` | 26.14s |
| G1-05 | 1 | PASS | `811aeb75-6a3b-436a-be9a-82c747fb09c2` | 22.56s |
| G1-06 | 1 | PASS | `31a1c9a7-6180-40eb-bdd7-f8f2d390fb8d` | 27.01s |
| G1-07 | 1 | PASS | `ab03679f-7040-4f3c-9d1a-e3fbc6b1ba8f` | 22.16s |
| G1-08 | 1 | PASS | `689bc13a-9222-4a18-b0e7-36f1d45668ea` | 21.79s |
| G1-09 | 1 | PASS | `2eeaf793-fdd6-448b-96a9-c1185bf2d67f` | 22.38s |
| G1-10 | 1 | PASS | `0cbbdeee-022e-45f3-aaad-b1d119523b46` | 22.59s |
| G1-11 | 1 | PASS | `c7f789bc-4605-417f-9334-d341fe6751d2` | 21.95s |
| G1-12 | 1 | PASS | `f0d99e82-5b95-4697-a199-00c2a613fd5a` | 21.89s |
| G1-13 | 1 | PASS | `edb9617b-5a09-4127-a9e3-19ca2bf78d9b` | 21.41s |
| G1-14 | 1 | PASS | `6eafef48-89f7-4d81-9958-96519d016132` | 21.77s |
| G1-15 | 1 | PASS | `91c748ea-cea5-4c5c-bfe5-f882051ac2c0` | 21.66s |
| G1-16 | 1 | PASS | `c8056fa3-e947-4563-8da7-aaffd06bc907` | 21.51s |
| G1-17 | 1 | PASS | `6183ce32-2d59-47f1-a471-ce36b01a8323` | 21.26s |
| G1-18 | 1 | PASS | `445c9ef8-5924-4c11-aed3-82403a6f9216` | 37.35s |
| G1-19 | 1 | PASS | `8c0280fb-9f13-4ad7-8574-7b4d7828021e` | 21.9s |
| G1-20 | 1 | PASS | `663430b2-8686-45e6-9158-f12e5d97340b` | 21.82s |
| G2-01 | 1 | PASS | `75215912-805b-4642-8077-38c07a5aff25` | 21.69s |
| G2-02 | 1 | PASS | `a7b17f5d-bdbf-4d64-b42f-0fe3254535dd` | 21.72s |
| G2-03 | 1 | PASS | `0206cafc-e1c2-41e2-8bfc-a69122083874` | 22.32s |
| G2-04 | 1 | PASS | `66b74e0d-89a2-4dca-84f6-03bdb4514ccc` | 22.2s |
| G2-05 | 1 | PASS | `e03be45d-0e4e-4b66-b33d-9019a5236b64` | 21.79s |
| G2-06 | 1 | PASS | `1cd34cd2-99be-4cfc-9fcf-eeedf15e7448` | 23.9s |
| G2-07 | 1 | PASS | `55ef57a3-ae6a-4a39-bd35-097e0b23bec3` | 22.79s |
| G2-08 | 1 | PASS | `9f91ce5d-7117-44db-8ea1-65e6651d3bda` | 25.37s |
| G2-09 | 1 | PASS | `e88af543-8366-461e-bd34-edc41ad3165f` | 22.18s |
| G2-10 | 1 | PASS | `2c0490f3-4ee1-4c9d-b587-aa4b3dbab6de` | 21.79s |
| G2-11 | 1 | PASS | `34b06f4f-001c-4918-92a3-b690fcba8354` | 25.17s |
| G2-12 | 1 | PASS | `2d694414-2338-4171-8896-608b51494bdf` | 21.79s |
| G2-13 | 1 | PASS | `90f0462e-b4c0-4e65-993f-d3541fbc55e8` | 28.12s |
| G2-14 | 1 | PASS | `300d6fb1-4675-4336-9e2f-36eac8a51eaa` | 21.84s |
| G2-15 | 1 | PASS | `8ee6a1f3-def8-4f27-8a3a-8e1a5ca73f20` | 21.87s |
| G2-16 | 1 | PASS | `412c33aa-c3b2-4957-aa16-a6446d4fd0b7` | 22.0s |
| G2-17 | 1 | PASS | `b9d22d07-a671-476d-ba87-4519d1f23c7c` | 22.37s |
| G2-18 | 1 | BLOCKED_ENVIRONMENT | `3a17b606-716b-4a61-9284-96b52c8a1ead` | 20.07s |

Kết quả local tham chiếu: `../20261027T110447Z/results.json`. Khác ngày/code/data nên không quy mọi chênh lệch cho model.
Evidence chi tiết trong `results.json` và `evidence/`. Không chứa API key, cookies hoặc Authorization headers.
Giỏ/đơn/bảo hành so sánh fingerprint trước/sau; read-only snapshot có giới hạn phân trang của tool cũ, không phải audit toàn DB.
Business fingerprint thay đổi: UNKNOWN: snapshot failed.
