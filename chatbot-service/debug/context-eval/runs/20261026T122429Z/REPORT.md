# Context eval — run 20261026T122429Z

Tài khoản: `seed.customer1` (seed). Lượt được chấm: 8, PASS 8, FAIL 0 (100% pass).

## Theo nhóm năng lực ngữ cảnh

| Nhóm | Lượt chấm | PASS | FAIL | Loại lỗi |
|---|---|---|---|---|
| D13_bundle_budget | 3 | 3 | 0 | — |
| D14_approximate_price | 5 | 5 | 0 | — |

## Loại lỗi

| Loại | Số lượt |
|---|---|

## Chi tiết từng lượt

### C16 — Combo điện thoại + laptop trong ngân sách (`D13_bundle_budget`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | Giờ tôi có 100tr tôi cần mua 1 điện thoại và 1 laptop | PASS | iPhone 16 (30,990,000), ASUS ROG Zephyrus G14 (52,990,000) | `{"category": "Điện thoại", "max_price": "100000000", "top_k": 20}` | tổng combo 83,980,000 |
| 2 | máy thứ hai bảo hành bao lâu | PASS | ASUS ROG Zephyrus G14 (52,990,000) | `{"targets": [{"product_id": null, "variant_id": "7c7555cd-0e26-44e6-83da-a1ce95e2f1bc", "product_name": null, "sku": null, "ram_gb": null, "storage_gb": null, "color": null}], "top_k": 1}` |  |

### C17 — Combo ngân sách thấp + đổi thứ tự loại (`D13_bundle_budget`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | ngân sách 40 triệu mua 1 laptop và 1 điện thoại | PASS | MacBook Air M2 (22,990,000), Xiaomi 14T Pro (16,990,000) | `{"category": "Laptop", "max_price": "40000000", "top_k": 20}` | tổng combo 39,980,000 |

### C18 — Giá khoảng / tầm (`D14_approximate_price`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop khoảng 25tr | PASS | MacBook Air M2 (22,990,000) | `{"category": "Laptop", "min_price": "21250000", "max_price": "28750000", "top_k": 5}` |  |
| 2 | dt tầm 20 triệu | PASS | Xiaomi 14T Pro (18,990,000), Xiaomi 15 (18,990,000), iPhone 15 (17,990,000), Samsung Galaxy S24 (18,990,000), iPhone 14 (18,990,000) | `{"category": "Điện thoại", "min_price": "17000000", "max_price": "23000000", "top_k": 5}` |  |
| 3 | rẻ hơn | PASS | Samsung Galaxy S23 (13,990,000), Xiaomi Redmi Note 13 (4,490,000), Xiaomi 14T Pro (16,990,000), Samsung Galaxy A55 (8,490,000), iPhone 14 (15,990,000) | `{"category": "Điện thoại", "max_price": "17989999.0", "top_k": 5}` |  |

### C19 — Mức giá cố định lớn hơn / rẻ hơn (`D14_approximate_price`)

| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |
|---|---|---|---|---|---|
| 1 | laptop giá lớn hơn 30tr | PASS | Dell XPS 13 (32,990,000), ASUS ROG Zephyrus G14 (39,990,000), MacBook Pro 14 M3 (39,990,000) | `{"category": "Laptop", "min_price": "30000000", "top_k": 5}` |  |
| 2 | dt rẽ hơn 15tr | PASS | Samsung Galaxy A55 (8,490,000), Samsung Galaxy S23 (13,990,000), Xiaomi Redmi Note 13 (4,490,000) | `{"category": "Điện thoại", "max_price": "14999999", "top_k": 5}` |  |

