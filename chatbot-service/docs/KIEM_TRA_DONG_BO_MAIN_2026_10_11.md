# Kiểm tra tổng thể sau đồng bộ main — 11/10/2026

## Phạm vi và trạng thái Git

Nhánh local `dev/thai-duy-vu` đồng bộ `origin/main` bằng fast-forward từ `78ec0dc` đến `15131c9`, không có xung đột. Giữ lại các tính năng chatbot và phòng thử trước đó. Chưa push thay đổi của lần kiểm tra này.

## Lỗi đã sửa

1. `scripts/env.sh` mới trên main dùng đường dẫn Windows cố định và ép kết nối cơ sở dữ liệu từ xa. Trên máy Mac, Java/Node/Flutter không được cấu hình đúng. Script hiện chọn cấu hình theo hệ điều hành, sử dụng công cụ local trên Mac, giữ môi trường do người dùng cấp trên Windows/Linux. Bỏ thông tin kết nối và JWT ghi trực tiếp trong script; cấu hình ứng dụng tiếp tục theo cơ chế hiện có. Không truy cập cơ sở dữ liệu từ xa để kiểm thử.
2. Checkout Flutter bọc `RadioListTile` trong nền `DecoratedBox`, che lớp Material và gây assertion trong test. Thêm Material trong suốt sát RadioListTile để vẽ hiệu ứng đúng. Test checkout gửi COD một lần, không xóa giỏ hàng, đã pass.

## Kết quả kiểm tra

| Thành phần | Kết quả |
| --- | --- |
| Backend Java | Build JAR thành công; 242 test pass, 0 fail/error/skip, gồm PostgreSQL Testcontainers |
| Frontend React | 46 test pass; ESLint và production build pass |
| Chatbot Python | 149 test pass; chạy riêng test PostgreSQL còn lại pass: tổng 150 |
| Flutter | 15 test pass, 1 test checkout backend opt-in không chạy |
| Flutter analyze | 60 vấn đề: 1 warning unused_element và 59 info; không có lỗi biên dịch được analyzer báo |
| Runtime | PostgreSQL healthy; frontend 5173, backend 8080, chatbot 8090 và Ollama 11434 hoạt động |

Lệnh backend trên Docker Desktop hiện tại: `source scripts/env.sh && cd backend && ./mvnw -Dapi.version=1.44 test`. Chạy không có tham số API version bị Testcontainers 1.20.6 trả lỗi Docker HTTP 400; chỉ định version khắc phục trên máy này. Chưa thay đổi dependency dự án để giải quyết tương thích dài hạn.

Frontend: `npm --prefix Fontend test`, `npm --prefix Fontend run lint`, `npm --prefix Fontend run build` sau khi source script môi trường. Flutter: `flutter test`, `flutter analyze --no-pub`. Chatbot: pytest, test PostgreSQL dùng DSN local cấp qua môi trường, không ghi giá trị vào tài liệu.

## Kiểm tra API thật

Đăng nhập qua frontend proxy, cookie và CSRF hiện có. API địa chỉ khách hàng, giỏ hàng, đơn hàng, bảo hành, danh sách SKU có keyword và phiếu nhập đều HTTP 200; SKU và phiếu nhập kiểm tra với tài khoản seed nhân viên.

12 kịch bản chatbot HTTP 200: chào hỏi, tìm laptop RAM 16GB dưới 20 triệu, hỏi loại rẻ hơn, sản phẩm còn hàng, chính sách DEMO, giỏ hàng, đơn hàng, thanh toán, bảo hành, phiếu bảo hành, yêu cầu thông tin không được phép và yêu cầu giao dịch. Feedback chào hỏi HTTP 200.

Đối chiếu độc lập với backend xác nhận: giá và variant trong product card đúng; RAM đạt điều kiện; tồn kho dương; SKU giỏ hàng, mã đơn và trạng thái thanh toán đúng; nguồn DEMO đúng. Laptop phù hợp là Dell Inspiron 15 giá 15.990.000đ. Hỏi rẻ hơn trả không có sản phẩm phù hợp. Chính sách DEMO trả 7 ngày và nguồn DEMO; không phải chính sách cửa hàng chính thức. Giỏ hàng phản ánh Xiaomi 14T Pro 18.990.000đ; thanh toán PENDING. Yêu cầu đặt hàng/thanh toán trả thông báo không thực hiện giao dịch.

## Điểm còn tồn tại và giới hạn

- Thông tin kết nối/JWT đã được commit trong lịch sử main. Bỏ khỏi script hiện tại không xóa lịch sử: chủ sở hữu cần đổi mật khẩu và secret đã lộ; không chép lại chúng vào báo cáo hoặc log.
- Flutter analyze chưa sạch: có warning hàm `_confirmWarehouseChange` không dùng và các info về print, BuildContext sau await, API deprecated và phong cách mã. Chưa sửa rộng ngoài lỗi checkout được xác nhận.
- Nội dung sinh bởi AI đôi lúc thiếu tên sản phẩm và ghi thương hiệu không xác định; product card đúng. Chưa coi văn bản AI là đảm bảo đầy đủ mọi thông tin.
- Knowledge vẫn trả thêm chunk DEMO thanh toán khi hỏi đổi trả; chưa có ngưỡng relevance loại nguồn không liên quan.
- Bảo hành/phiếu bảo hành tài khoản test không có dữ liệu: trả chưa đủ dữ liệu đúng, nhưng chưa xác minh E2E trường hợp có hồ sơ thật.
- Chưa chạy test Flutter checkout backend opt-in, chưa thử thanh toán thật hoặc build APK/iOS; kết quả không chứng minh các luồng này đã pass.
- Main có thư mục node_modules được theo dõi trong Git; chưa dọn vì không cần thiết cho đồng bộ/kiểm tra lần này.
- Không chạy lại toàn bộ thao tác UI trên trình duyệt; frontend được xác minh bằng unit/component test, build và API qua proxy.

## Trạng thái cuối

Các dịch vụ local đang chạy để kiểm tra trực tiếp: http://localhost:5173 và http://localhost:5173/chatbot-lab. Dừng bằng `bash scripts/stop.sh`. Log kiểm tra nằm trong `.local/logs/main-sync-*` (không commit). Không chỉnh sửa schema/migration backend và không push remote trong lần này.
