# Context eval — run 20261021T122456Z

Tài khoản: `seed.customer1` (seed). Lượt được chấm: 11, PASS 11, FAIL 0 (100% pass).

## Theo nhóm năng lực ngữ cảnh

| Nhóm | Lượt chấm | PASS | FAIL | Loại lỗi |
|---|---|---|---|---|
| D4_ordinal_reference | 3 | 3 | 0 | — |
| D4_pronoun_reference | 3 | 3 | 0 | — |
| D6_compare_follow_up | 3 | 3 | 0 | — |
| D8_long_distance | 2 | 2 | 0 | — |

## Loại lỗi

| Loại | Số lượt |
|---|---|

## Chi tiết từng lượt

### C05 — Tham chiếu thứ tự trên nhiều kết quả (`D4_ordinal_reference`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý 3 laptop | PASS | Dell XPS 13 (32,990,000), MacBook Pro 14 M3 (39,990,000), ASUS ROG Zephyrus G14 (39,990,000) | `{"category": "Laptop", "top_k": 3}` |  |
| 2 | máy thứ hai giá bao nhiêu | PASS | MacBook Pro 14 M3 (39,990,000) | `{"targets": [{"product_id": null, "variant_id": "54ace4fd-a0f5-4d2f-8b00-ae5228bd3b05", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | máy đầu tiên bảo hành bao lâu | PASS | Dell XPS 13 (32,990,000) | `{"targets": [{"product_id": null, "variant_id": "a141cdf6-1383-4e94-b002-7c982af08a83", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### C06 — Đại từ 'nó / máy này' cho 1 sản phẩm (`D4_pronoun_reference`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | MacBook Air M2 giá bao nhiêu | PASS | MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": null, "product_name": "MacBook Air M2", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 2 | nó còn hàng không | PASS | MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": "9d7885da-2374-4e10-91c6-e6b968fb2fb0", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | máy này bảo hành mấy tháng | PASS | MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": "9d7885da-2374-4e10-91c6-e6b968fb2fb0", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### C08 — Hỏi tiếp trong phạm vi 2 sản phẩm đang so sánh (`D6_compare_follow_up`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | so sánh Dell XPS 13 và MacBook Air M2 | PASS | Dell XPS 13 (32,990,000), MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": null, "product_name": "Dell XPS 13", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}, {"product_id": null, "variant_id": null, "product_name": "MacBook Air M2", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 2}` |  |
| 2 | cái nào rẻ hơn | PASS | MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": "9d7885da-2374-4e10-91c6-e6b968fb2fb0", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | cái nào RAM nhiều hơn | PASS | Dell XPS 13 (32,990,000) | `{"targets": [{"product_id": null, "variant_id": "a141cdf6-1383-4e94-b002-7c982af08a83", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### C10 — Tham chiếu sản phẩm cách 3 lượt (`D8_long_distance`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | điện thoại Samsung RAM 12GB | PASS | Samsung Galaxy A55 (10,490,000), Samsung Galaxy Z Flip6 (24,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "min_ram_gb": 12, "top_k": 5}` |  |
| 2 | xin chào | INFO | _Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hà_ | `—` | không chấm (lượt chen ngang) |
| 3 | bạn có thể làm gì | INFO | _Bạn có thể hỏi về sản phẩm, chính sách cửa hàng hoặc thông tin đơn hàn_ | `—` | không chấm (lượt chen ngang) |
| 4 | cái đầu tiên lúc nãy giá bao nhiêu | PASS | Samsung Galaxy A55 (10,490,000) | `{"targets": [{"product_id": null, "variant_id": "16bfe6b0-f0bc-46a6-9443-0251887c66be", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

