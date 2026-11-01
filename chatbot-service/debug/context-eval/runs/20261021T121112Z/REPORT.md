# Context eval — run 20261021T121112Z

Tài khoản: `seed.customer1` (seed). Lượt được chấm: 42, PASS 22, FAIL 20 (52% pass).

## Theo nhóm năng lực ngữ cảnh

| Nhóm | Lượt chấm | PASS | FAIL | Loại lỗi |
|---|---|---|---|---|
| D10_correction | 3 | 2 | 1 | MISSED |
| D11_colloquial | 3 | 2 | 1 | CONTEXT_LOST |
| D12_stale_slot_reset | 4 | 3 | 1 | MISSED |
| D1_price_comparative | 4 | 2 | 2 | MISSED |
| D2_budget_change | 3 | 1 | 2 | CONTEXT_LOST, WRONG_FILTER |
| D2_refine_carry_over | 4 | 0 | 4 | CONTEXT_LOST, MISSED, WRONG_FILTER |
| D3_slot_override | 3 | 2 | 1 | WRONG_FILTER |
| D4_ordinal_reference | 3 | 2 | 1 | WRONG_REFERENT |
| D4_pronoun_reference | 3 | 1 | 2 | CONTEXT_LOST, MISSED, TEXT_MISSING |
| D5_variant_follow_up | 3 | 2 | 1 | WRONG_FILTER |
| D6_compare_follow_up | 3 | 0 | 3 | CONTEXT_LOST, MISSED, TEXT_MISSING, WRONG_REFERENT |
| D7_topic_switch_return | 2 | 2 | 0 | — |
| D8_long_distance | 2 | 2 | 0 | — |
| D9_more_results | 2 | 1 | 1 | CONTEXT_LOST |

## Loại lỗi

| Loại | Số lượt |
|---|---|
| MISSED | 8 |
| CONTEXT_LOST | 7 |
| WRONG_FILTER | 4 |
| TEXT_MISSING | 3 |
| WRONG_REFERENT | 2 |

## Chi tiết từng lượt

### C01 — Chuỗi rẻ hơn / mắc hơn (tái hiện UAT) (`D1_price_comparative`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | Tôi cần tìm 1 laptop ram 16G | PASS | Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "min_ram_gb": 16, "top_k": 1}` |  |
| 2 | có sản phẩm nào mắc hơn xíu | PASS | Dell XPS 13 (32,990,000) | `{"category": "Laptop", "min_price": "15990001.0", "min_ram_gb": 16, "top_k": 1}` |  |
| 3 | rẻ hơn chút | FAIL MISSED | _Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại._ | `{"category": "Laptop", "min_price": "15990001.0", "max_price": "32989999.0", "min_ram_gb": 16, "top_k": 1}` | catalog có 1 sản phẩm phù hợp, vd Dell Inspiron 15 |
| 4 | rẻ hơn nữa | FAIL MISSED | _Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại._ | `{"category": "Laptop", "min_price": "15990001.0", "max_price": "32989999.0", "min_ram_gb": 16, "top_k": 1}` | catalog có 1 sản phẩm phù hợp, vd Dell Inspiron 15 |

### C02 — Thu hẹp dần điều kiện, giữ điều kiện cũ (`D2_refine_carry_over`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý laptop RAM 16GB | FAIL MISSED | _Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong ca_ | `—` | catalog có 4 sản phẩm phù hợp, vd Dell Inspiron 15 |
| 2 | chỉ lấy hãng Dell thôi | FAIL MISSED | _Chưa xác minh được tên sản phẩm trong catalog. Bạn vui lòng xác nhận t_ | `—` | catalog có 2 sản phẩm phù hợp, vd Dell Inspiron 15 |
| 3 | dưới 35 triệu | FAIL WRONG_FILTER | Xiaomi 15 (18,990,000), Samsung Galaxy S23 (13,990,000), Samsung Galaxy A55 (8,490,000), Xiaomi 14T Pro (16,990,000), Xiaomi Redmi Note 13 (4,490,000) | `{"max_price": "35000000", "top_k": 5}` | Xiaomi 15 12GB/256GB 18,990,000 sai: category, brand, min_ram; Samsung Galaxy S23 8GB/128GB 13,990,000 sai: category, brand, min_ram; Samsung Galaxy A55 8GB/128GB 8,490,000 sai: category, brand, min_ram; Xiaomi 14T Pro 12GB/256GB 16,990,000 sai: category, brand, min_ram; Xiaomi Redmi Note 13 6GB/128GB 4,490,000 sai: category, brand, min_ram |
| 4 | bỏ điều kiện giá, có bản 1TB không | FAIL CONTEXT_LOST | _Có nhiều sản phẩm vừa được gợi ý. Bạn muốn hỏi máy nào? Hãy nêu tên ho_ | `—` |  |

### C03 — Đổi hãng nhưng giữ danh mục (`D3_slot_override`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop Dell nào đang có | PASS | Dell XPS 13 (32,990,000), Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "brands": ["Dell"], "top_k": 5}` |  |
| 2 | còn của ASUS thì sao | PASS | ASUS ROG Zephyrus G14 (39,990,000), ASUS Vivobook 15 (12,990,000) | `{"brands": ["ASUS"], "top_k": 5}` |  |
| 3 | Apple thì có mẫu nào | FAIL WRONG_FILTER | iPhone 16 (21,990,000), iPhone 14 (15,990,000), MacBook Pro 14 M3 (39,990,000), iPhone 15 (17,990,000), MacBook Air M2 (22,990,000) | `{"brands": ["Apple"], "top_k": 5}` | iPhone 16 8GB/128GB 21,990,000 sai: category; iPhone 14 6GB/128GB 15,990,000 sai: category; iPhone 15 6GB/128GB 17,990,000 sai: category |

### C04 — Đổi danh mục phải bỏ điều kiện không còn phù hợp (`D12_stale_slot_reset`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop RAM 32GB | PASS | ASUS ROG Zephyrus G14 (49,990,000), Dell XPS 13 (41,990,000) | `{"category": "Laptop", "min_ram_gb": 32, "top_k": 5}` |  |
| 2 | giờ tôi muốn xem điện thoại Samsung | FAIL MISSED | _Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong ca_ | `—` | catalog có 4 sản phẩm phù hợp, vd Samsung Galaxy S24 |

### C05 — Tham chiếu thứ tự trên nhiều kết quả (`D4_ordinal_reference`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý 3 laptop | PASS | Dell XPS 13 (32,990,000), MacBook Pro 14 M3 (39,990,000), ASUS ROG Zephyrus G14 (39,990,000) | `{"category": "Laptop", "top_k": 3}` |  |
| 2 | máy thứ hai giá bao nhiêu | PASS | MacBook Pro 14 M3 (39,990,000) | `{"targets": [{"product_id": null, "variant_id": "54ace4fd-a0f5-4d2f-8b00-ae5228bd3b05", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 3 | máy đầu tiên bảo hành bao lâu | FAIL WRONG_REFERENT | MacBook Pro 14 M3 (39,990,000) | `{"targets": [{"product_id": null, "variant_id": "54ace4fd-a0f5-4d2f-8b00-ae5228bd3b05", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` | mong đợi Dell XPS 13, nhận ['MacBook Pro 14 M3'] |

### C06 — Đại từ 'nó / máy này' cho 1 sản phẩm (`D4_pronoun_reference`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | MacBook Air M2 giá bao nhiêu | PASS | MacBook Air M2 (22,990,000) | `{"targets": [{"product_id": null, "variant_id": null, "product_name": "MacBook Air M2", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 2 | nó còn hàng không | FAIL CONTEXT_LOST,TEXT_MISSING | _Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?_ | `—` | câu trả lời không nhắc ['còn', 'hết hàng', 'tồn'] |
| 3 | máy này bảo hành mấy tháng | FAIL MISSED | _Backend chưa trả về bản ghi bảo hành nào thuộc tài khoản của bạn._ | `—` | catalog có 1 sản phẩm phù hợp, vd MacBook Air M2 |

### C07 — Hỏi tiếp phiên bản của sản phẩm vừa nói (`D5_variant_follow_up`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | Xiaomi 15 có những bản nào | PASS | Xiaomi 15 (18,990,000) | `{"brands": ["Xiaomi"], "targets": [{"product_id": null, "variant_id": null, "product_name": "Xiaomi 15", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |
| 2 | bản 512GB giá bao nhiêu | PASS | Xiaomi 15 (20,990,000) | `{"targets": [{"product_id": "169710dd-d072-4e83-9fd0-e618d706ee26", "variant_id": null, "product_name": null, "sku": null, "ram_gb": null, "storage_gb": 512, "color": null}], "top_k": 1}` |  |
| 3 | còn bản RAM 16GB thì sao | FAIL WRONG_FILTER | Xiaomi 15 (21,990,000), ASUS ROG Zephyrus G14 (39,990,000), Dell Inspiron 15 (15,990,000), Xiaomi 14T Pro (19,990,000), Dell XPS 13 (32,990,000) | `{"min_ram_gb": 16, "top_k": 5}` | ASUS ROG Zephyrus G14 16GB/512GB 39,990,000 sai: product; Dell Inspiron 15 16GB/512GB 15,990,000 sai: product; Xiaomi 14T Pro 16GB/512GB 19,990,000 sai: product; Dell XPS 13 16GB/512GB 32,990,000 sai: product |

### C08 — Hỏi tiếp trong phạm vi 2 sản phẩm đang so sánh (`D6_compare_follow_up`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | so sánh Dell XPS 13 và MacBook Air M2 | FAIL MISSED,WRONG_REFERENT | _Sản phẩm tồn tại nhưng chưa có biến thể đang bán phù hợp cấu hình bạn _ | `{"brands": ["Dell"], "targets": [{"product_id": null, "variant_id": null, "product_name": "Dell XPS 13", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}, {"product_id": null, "variant_id": null, "product_name": "MacBook Air M2", "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 2}` | catalog có 16 sản phẩm phù hợp, vd iPhone 15; mong đợi đúng ['Dell XPS 13', 'MacBook Air M2'], nhận [] |
| 2 | cái nào rẻ hơn | FAIL CONTEXT_LOST,TEXT_MISSING | _Mình chưa có sản phẩm tham chiếu trong phiên này. Bạn vui lòng nêu tên_ | `—` | câu trả lời không nhắc ['MacBook Air M2'] |
| 3 | cái nào RAM nhiều hơn | FAIL CONTEXT_LOST,TEXT_MISSING | _Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?_ | `—` | câu trả lời không nhắc ['Dell XPS 13'] |

### C09 — Chen câu hỏi giỏ hàng rồi quay lại sản phẩm (`D7_topic_switch_return`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop dưới 20 triệu | PASS | ASUS Vivobook 15 (12,990,000), Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "max_price": "20000000", "top_k": 5}` |  |
| 2 | giỏ hàng của tôi có gì | INFO | _Xiaomi 14T Pro (DEV-X14TP-BLU-512): 1 sản phẩm; giá mỗi sản phẩm 18990_ | `—` | không chấm (lượt chen ngang) |
| 3 | quay lại laptop lúc nãy, có máy nào rẻ hơn không | PASS | _Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong ca_ | `—` | catalog không có sản phẩm phù hợp; trả lời rỗng là đúng |

### C10 — Tham chiếu sản phẩm cách 3 lượt (`D8_long_distance`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | điện thoại Samsung RAM 12GB | PASS | Samsung Galaxy A55 (10,490,000), Samsung Galaxy Z Flip6 (24,990,000) | `{"category": "Điện thoại", "brands": ["Samsung"], "min_ram_gb": 12, "top_k": 5}` |  |
| 2 | xin chào | INFO | _Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hà_ | `—` | không chấm (lượt chen ngang) |
| 3 | bạn có thể làm gì | INFO | _Bạn có thể hỏi về sản phẩm, chính sách cửa hàng hoặc thông tin đơn hàn_ | `—` | không chấm (lượt chen ngang) |
| 4 | cái đầu tiên lúc nãy giá bao nhiêu | PASS | Samsung Galaxy A55 (10,490,000) | `{"targets": [{"product_id": null, "variant_id": "16bfe6b0-f0bc-46a6-9443-0251887c66be", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### C11 — Xin thêm lựa chọn khác (`D9_more_results`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | gợi ý 1 điện thoại Apple | PASS | iPhone 16 (21,990,000) | `{"category": "Điện thoại", "brands": ["Apple"], "top_k": 1}` |  |
| 2 | còn mẫu nào khác không | FAIL CONTEXT_LOST | _Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?_ | `—` |  |

### C12 — Người dùng sửa lại điều kiện vừa nói (`D10_correction`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop RAM 8GB | PASS | MacBook Air M2 (22,990,000), ASUS ROG Zephyrus G14 (39,990,000), Dell XPS 13 (32,990,000), MacBook Pro 14 M3 (39,990,000), ASUS Vivobook 15 (12,990,000) | `{"category": "Laptop", "min_ram_gb": 8, "top_k": 5}` |  |
| 2 | à không, ý tôi là RAM 32GB | PASS | ASUS ROG Zephyrus G14 (49,990,000), Dell XPS 13 (41,990,000) | `{"min_ram_gb": 32, "top_k": 5}` |  |
| 3 | của ASUS | FAIL MISSED | _Mình chưa đủ dữ liệu để trả lời chính xác yêu cầu này._ | `—` | catalog có 1 sản phẩm phù hợp, vd ASUS ROG Zephyrus G14 |

### C13 — Văn nói: 'con này', 'con nào rẻ nhất' (`D11_colloquial`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | mấy con điện thoại Xiaomi | PASS | Xiaomi 15 (18,990,000), Xiaomi 14T Pro (16,990,000), Xiaomi Redmi Note 13 (4,490,000) | `{"category": "Điện thoại", "brands": ["Xiaomi"], "top_k": 5}` |  |
| 2 | con nào rẻ nhất | FAIL CONTEXT_LOST | _Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?_ | `—` |  |
| 3 | có con nào mắc hơn xíu không | PASS | Xiaomi 14T Pro (19,990,000), Xiaomi 15 (20,990,000) | `{"category": "Điện thoại", "brands": ["Xiaomi"], "min_price": "18990001.0", "top_k": 5}` |  |

### C14 — Đổi ngân sách tương đối (`D2_budget_change`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | điện thoại dưới 20 triệu | PASS | Samsung Galaxy S23 (13,990,000), Xiaomi 15 (18,990,000), Samsung Galaxy A55 (8,490,000), Xiaomi 14T Pro (16,990,000), Samsung Galaxy S24 (18,990,000) | `{"category": "Điện thoại", "max_price": "20000000", "top_k": 5}` |  |
| 2 | tăng ngân sách lên 25 triệu | FAIL CONTEXT_LOST | _Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?_ | `—` |  |
| 3 | chỉ Apple thôi | FAIL WRONG_FILTER | MacBook Pro 14 M3 (39,990,000), iPhone 14 (15,990,000), MacBook Air M2 (22,990,000), iPhone 16 (21,990,000), iPhone 15 (17,990,000) | `{"brands": ["Apple"], "top_k": 5}` | MacBook Pro 14 M3 8GB/512GB 39,990,000 sai: category, max_price; MacBook Air M2 8GB/256GB 22,990,000 sai: category |

### C15 — Yêu cầu mới không được kế thừa hãng cũ (`D12_stale_slot_reset`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop Dell dưới 20 triệu | PASS | Dell Inspiron 15 (15,990,000) | `{"category": "Laptop", "brands": ["Dell"], "max_price": "20000000", "top_k": 5}` |  |
| 2 | tìm điện thoại | PASS | Samsung Galaxy S23 (13,990,000), Samsung Galaxy A55 (8,490,000), Xiaomi 15 (18,990,000), Xiaomi 14T Pro (16,990,000), Xiaomi Redmi Note 13 (4,490,000) | `{"category": "Điện thoại", "top_k": 5}` |  |

