# Context eval — run 20261026T123230Z-holdout

Tài khoản: `seed.customer1` (seed). Lượt được chấm: 18, PASS 18, FAIL 0 (100% pass).

## Theo nhóm năng lực ngữ cảnh

| Nhóm | Lượt chấm | PASS | FAIL | Loại lỗi |
|---|---|---|---|---|
| D12_stale_slot_reset | 2 | 2 | 0 | — |
| D1_price_comparative | 3 | 3 | 0 | — |
| D2_refine_carry_over | 3 | 3 | 0 | — |
| D3_slot_override | 2 | 2 | 0 | — |
| D4_pronoun_reference | 3 | 3 | 0 | — |
| D6_compare_follow_up | 3 | 3 | 0 | — |
| D9_more_results | 2 | 2 | 0 | — |

## Loại lỗi

| Loại | Số lượt |
|---|---|

## Chi tiết từng lượt

### H01 — Diễn đạt khác của đắt hơn / rẻ hơn (`D1_price_comparative`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | cho mình xem điện thoại Samsung | PASS | Samsung Galaxy S23 (13,990,000), Samsung Galaxy A55 (8,490,000), Samsung Galaxy S24 (18,990,000), Samsung Galaxy Z Flip6 (24,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "top_k": 5}` |  |
| 2 | có loại nào đắt hơn chút không | PASS | Samsung Galaxy Z Flip6 (28,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "min_price": "24990001.0", "top_k": 5}` |  |
| 3 | có cái nào giá thấp hơn không | PASS | Samsung Galaxy S23 (13,990,000), Samsung Galaxy A55 (8,490,000), Samsung Galaxy Z Flip6 (24,990,000), Samsung Galaxy S24 (18,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "max_price": "28989999.0", "top_k": 5}` |  |

### H02 — Thu hẹp bằng câu cụt (`D2_refine_carry_over`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | mình cần laptop để làm việc | PASS | MacBook Air M2 (22,990,000), Dell XPS 13 (32,990,000), MacBook Pro 14 M3 (39,990,000), Dell Inspiron 15 (15,990,000), ASUS Vivobook 15 (12,990,000) | `{"category": "Laptop", "top_k": 5}` |  |
| 2 | RAM 16GB trở lên | PASS | ASUS ROG Zephyrus G14 (39,990,000), MacBook Pro 14 M3 (44,990,000), Dell XPS 13 (32,990,000), Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "min_ram_gb": 16, "top_k": 5}` |  |
| 3 | tối đa 40 triệu thôi | PASS | Dell XPS 13 (32,990,000), ASUS ROG Zephyrus G14 (39,990,000), Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "max_price": "40000000", "min_ram_gb": 16, "top_k": 5}` |  |

### H03 — Đổi hãng trên điện thoại (`D3_slot_override`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | điện thoại Xiaomi dưới 20 triệu | PASS | Xiaomi 15 (18,990,000), Xiaomi 14T Pro (16,990,000), Xiaomi Redmi Note 13 (4,490,000) | `{"category": "Điện thoại", "brands": ["Xiaomi"], "max_price": "20000000", "top_k": 5}` |  |
| 2 | thế còn Samsung | PASS | Samsung Galaxy S23 (13,990,000), Samsung Galaxy A55 (8,490,000), Samsung Galaxy S24 (18,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "max_price": "20000000", "top_k": 5}` |  |

### H04 — Đại từ sau khi hỏi tên sản phẩm (`D4_pronoun_reference`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | Dell XPS 13 có mấy phiên bản | PASS | Dell XPS 13 (32,990,000) | `{"targets": [{"product_id": null, "variant_id": null, "product_name": "Dell XPS 13", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 2 | con đó giá bao nhiêu | PASS | Dell XPS 13 (32,990,000) | `{"targets": [{"product_id": null, "variant_id": "a141cdf6-1383-4e94-b002-7c982af08a83", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | bản 1TB của nó thì sao | PASS | Dell XPS 13 (36,990,000) | `{"targets": [{"product_id": "de24f73c-c12f-43e6-ba87-13308ab4980c", "variant_id": null, "product_name": null, "sku": null, "ram_gb": null, "storage_gb": 1024, "color": null}], "top_k": 1}` |  |

### H05 — Chọn trong danh sách vừa gợi ý (`D6_compare_follow_up`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý vài điện thoại Apple | PASS | iPhone 16 (21,990,000), iPhone 14 (15,990,000), iPhone 15 (17,990,000) | `{"category": "Điện thoại", "brands": ["Apple"], "top_k": 5}` |  |
| 2 | máy nào đắt nhất | PASS | iPhone 16 (21,990,000) | `{"targets": [{"product_id": null, "variant_id": "26526f0a-d1ee-4ecd-83c1-e1d8708c07f3", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | cái thứ hai còn hàng không | PASS | iPhone 14 (15,990,000) | `{"targets": [{"product_id": null, "variant_id": "c4225eb2-6bf0-46be-8779-8f90ef28a923", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### H06 — Xin thêm gợi ý (`D9_more_results`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý 2 laptop | PASS | MacBook Air M2 (22,990,000), ASUS ROG Zephyrus G14 (39,990,000) | `{"category": "Laptop", "top_k": 2}` |  |
| 2 | có mẫu khác không | PASS | MacBook Pro 14 M3 (39,990,000), Dell XPS 13 (32,990,000) | `{"category": "Laptop", "top_k": 4}` |  |

### H07 — Câu mới đủ ý không kế thừa điều kiện cũ (`D12_stale_slot_reset`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop ASUS RAM 32GB | PASS | ASUS ROG Zephyrus G14 (49,990,000) | `{"category": "Laptop", "brands": ["ASUS"], "min_ram_gb": 32, "top_k": 5}` |  |
| 2 | tìm giúp mình điện thoại dưới 10 triệu | PASS | Xiaomi Redmi Note 13 (4,490,000), Samsung Galaxy A55 (8,490,000) | `{"category": "Điện thoại", "max_price": "10000000", "top_k": 5}` |  |

