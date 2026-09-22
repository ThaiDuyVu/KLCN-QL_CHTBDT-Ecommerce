# Ảnh Product seed

Backend chỉ lưu các đường dẫn giả dưới đây. Chưa có file ảnh và không tải ảnh từ Internet.
Bạn bổ sung `main.jpg` và `detail.jpg` vào từng thư mục trong `Fontend/public/images/products/`:

| Product | Thư mục | URL ảnh chính | URL ảnh chi tiết |
| --- | --- | --- | --- |
| iPhone 15 | `iphone-15/` | `/images/products/iphone-15/main.jpg` | `/images/products/iphone-15/detail.jpg` |
| Samsung Galaxy S24 | `samsung-galaxy-s24/` | `/images/products/samsung-galaxy-s24/main.jpg` | `/images/products/samsung-galaxy-s24/detail.jpg` |
| Xiaomi Redmi Note 13 | `xiaomi-redmi-note-13/` | `/images/products/xiaomi-redmi-note-13/main.jpg` | `/images/products/xiaomi-redmi-note-13/detail.jpg` |
| MacBook Air M2 | `macbook-air-m2/` | `/images/products/macbook-air-m2/main.jpg` | `/images/products/macbook-air-m2/detail.jpg` |
| Dell Inspiron 15 | `dell-inspiron-15/` | `/images/products/dell-inspiron-15/main.jpg` | `/images/products/dell-inspiron-15/detail.jpg` |
| ASUS Vivobook 15 | `asus-vivobook-15/` | `/images/products/asus-vivobook-15/main.jpg` | `/images/products/asus-vivobook-15/detail.jpg` |

Ví dụ: `Fontend/public/images/products/iphone-15/main.jpg` được frontend phục vụ tại
`/images/products/iphone-15/main.jpg`. Dùng `imageUrl` trực tiếp trong thuộc tính `src` của ảnh
trên frontend; không thêm địa chỉ backend vào các đường dẫn bắt đầu bằng `/`.

## Bật seed

Chạy backend với profile `dev`, bật `app.seed.product-image.enabled=true`.
Nếu chưa có Product mẫu, bật thêm `app.seed.category.enabled=true`,
`app.seed.brand.enabled=true` và `app.seed.product.enabled=true`.
Seed chỉ bổ sung đường dẫn chưa tồn tại và giữ ảnh chính đang có.

## API

CRUD tại `/api/v1/products/{productId}/images`; GET/PUT/DELETE một ảnh thêm `/{imageId}`.
Danh sách ảnh của một Product trả về mảng đầy đủ, không phân trang.

POST/PUT nhận JSON:

```json
{
  "imageUrl": "/images/products/iphone-15/main.jpg",
  "isPrimary": true
}
```

`imageUrl` là bắt buộc, tối đa 1000 ký tự; chấp nhận URL HTTP/HTTPS hoặc đường dẫn frontend
bắt đầu bằng `/`. Backend không kiểm tra file ảnh có tồn tại hay không.
`isPrimary` mặc định false khi tạo, giữ giá trị cũ khi cập nhật nếu bỏ qua hoặc null.
Chọn true sẽ hạ ảnh chính cũ trong cùng transaction. Chọn false hoặc xóa ảnh chính
có thể để Product không có ảnh chính; không tự chọn ảnh khác.
