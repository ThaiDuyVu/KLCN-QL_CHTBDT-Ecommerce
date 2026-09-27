# Tạo Product và upload ảnh

## Sử dụng

ADMIN/MANAGER vào **Sản phẩm → Tạo sản phẩm** (`/products/new`). Điền tên, danh mục, thương hiệu, mô tả và trạng thái. Chọn ảnh từ thư mục máy, xem trước, chọn ảnh chính, bỏ ảnh nếu cần rồi bấm Tạo sản phẩm. Thành công chuyển đến chi tiết sản phẩm.

Danh mục và thương hiệu lấy từ API thật; thương hiệu được tải phân trang và giữ lựa chọn khi đổi trang. STAFF/CUSTOMER không thấy nút tạo và không truy cập route/API tạo.

## API

- `POST /api/v1/products/with-images`: multipart/form-data, ADMIN/MANAGER, cookie auth và CSRF hiện có.
- Part `product`: application/json theo ProductRequest hiện có (`productName`, `description`, `categoryId`, `brandId`, `status`).
- Part `specifications`: application/json tùy chọn, `{ "specifications": [{ "specKey": "RAM", "specValue": "16 GB" }] }`. Dùng validation hiện có: tên tối đa 100 ký tự, giá trị tối đa 1000 ký tự; không giới hạn tên thông số hoặc bắt buộc bộ mẫu. Form có thêm/xóa dòng và gợi ý CPU, RAM, Màn hình, Pin.
- Part `images`: nhiều file cùng tên part; tùy chọn.
- Field `primaryImageIndex`: index zero-based trong danh sách file, mặc định 0.
- Response 201: ProductResponse hiện có, có primaryImageUrl nếu chọn ảnh.
- `GET /api/product-media/{filename}`: đọc ảnh công khai, content-type JPG/PNG và nosniff.
- API JSON `POST /api/v1/products` và các API ảnh theo URL cũ được giữ nguyên.

## Lưu trữ

Ảnh JPG/PNG, tối đa 10 file, 5 MB/file, 20 megapixel/file. Backend đọc và encode lại nội dung ảnh; không dùng tên file gốc làm đường dẫn. Tên file UUID, URL `/api/product-media/<UUID>.jpg|png`. Frontend chỉ gửi FormData bằng apiClient, không ghi ảnh vào public hoặc giữ base64 trong database.

File được lưu tại `PRODUCT_IMAGES_DIRECTORY`, mặc định `./uploads/products` tương đối với thư mục chạy backend. Nên đặt biến này thành đường dẫn tuyệt đối cố định hoặc mount persistent volume khi triển khai; backup thư mục cùng database. Thư mục upload được gitignore và không nằm trong frontend build. Reverse proxy hiện có cần chuyển tiếp `/api` đến backend để phục vụ cả API và ảnh.

Tạo Product, ProductImage và Specification trong cùng transaction; khi lỗi/rollback, file đã ghi được dọn, Product/Image không được lưu dở dang. Không có migration. File ảnh không tự xóa khi xóa Product bằng API cũ (cần giữ/dọn storage theo chính sách triển khai).

Tạo Product không tự sinh SKU/biến thể hoặc tăng tồn kho. Giá, tracking và bảo hành thuộc ProductVariant; tồn kho qua Goods Receipt hiện có.

## Xác minh

Backend compile/package và frontend lint/build đạt. Kiểm tra API thật trên PostgreSQL tạm: tạo 2 ảnh với 1 ảnh chính, đọc ảnh công khai; rollback Product/Image/file khi ảnh sau lỗi; từ chối index ảnh chính sai; tạo không ảnh; chặn CUSTOMER/STAFF; API JSON cũ vẫn hoạt động. Database và file tạm đã dọn, dữ liệu chính không thay đổi. Không viết test hoặc chạy test backend.

Restart backend để nạp endpoint upload và cấu hình multipart mới.

### Bổ sung thông số kỹ thuật

Đã kiểm tra trên database riêng: tạo Product kèm ảnh và CPU/RAM/Màn hình/Pin; detail trả đủ giá trị; tên >100 ký tự, giá trị >1000 ký tự, phần tử/giá trị null nhận 400; request lỗi không tạo Product/file; ảnh lỗi rollback; request không có part thông số vẫn hoạt động; tên thông số tùy ý và lặp vẫn được phép theo schema cũ. Không có migration hoặc thay đổi API Specification riêng. Backend chính được khởi động lại với bản mới, không chạy lại seed.
