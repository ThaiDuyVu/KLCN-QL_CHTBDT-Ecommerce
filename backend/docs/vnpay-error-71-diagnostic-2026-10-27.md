# Chẩn đoán VNPAY Sandbox code=71 — 27/10/2026

## Kết luận và giới hạn

Ảnh lỗi lúc 18:48:28 ngày 27/10/2026 hiển thị `Payment/Error.html?code=71`, “Website này chưa được phê duyệt”. Đây là từ chối ở gateway về trạng thái terminal/website, trước màn hình thanh toán. Chưa có bằng chứng lỗi này bắt nguồn từ phép tính amount, chữ ký hoặc xử lý callback backend.

Đã GET trực tiếp trang lỗi chính thức (không gửi merchant credentials, không tạo giao dịch): HTTP 200, nội dung “The terminal (website) not approved”. GET trang lỗi tĩnh xác minh nghĩa thông báo, **không xác minh merchant cụ thể đang active hay không**. Cần đối chiếu terminal thực tế trong trang quản trị/email cấp cấu hình VNPAY Sandbox để phân biệt dùng nhầm terminal và terminal chưa được duyệt/không còn hoạt động.

## Kiểm tra thực hiện

- Backend runtime: đăng nhập CUSTOMER development theo repo, GET `/api/payments/vnpay/config` trả HTTP 200, `enabled=true`.
- `backend/.env`: VNPAY bật; TmnCode có mặt và đúng hình thức tám ký tự chữ/số; HashSecret có mặt. Không ghi giá trị credentials. Đúng hình thức/không rỗng không chứng minh credentials đúng merchant hoặc terminal được duyệt.
- Return URL local: `http://localhost:8080/api/payments/vnpay/return`; frontend result URL: `http://localhost:5173/payments/vnpay/result`.
- `VnpayServiceImpl.paymentUrl`: đọc TmnCode từ configuration, dùng sandbox endpoint; số tiền nhân 100, timestamp Việt Nam, hết hạn 15 phút; ký query đã sắp xếp bằng HMAC-SHA512.
- `VnpayProperties.isConfigurationValid`: chỉ kiểm tra hình thức credentials và URL; không gọi VNPAY kiểm tra trạng thái duyệt merchant. Vì vậy `enabled=true` không có nghĩa terminal đã được VNPAY duyệt.
- `notifyPayment`: kiểm tra signature/merchant/reference/amount trước cập nhật payment. `returnRedirect` có fallback QueryDr khi return thành công. Code=71 ở gateway khác các mã ACK IPN do backend trả.
- Tìm trong `.local/logs/backend.log` chưa có dòng VNPAY xác định riêng request lúc 18:48:28 ngày 27/10. Không suy diễn absence callback là lỗi callback hoặc xác nhận trạng thái payment của đơn trong ảnh.
- Có tài liệu cũ `backend/docs/vnpay-sandbox.md` mô tả lỗi 71 tương tự và lần đổi credentials trước đó; đây là lịch sử, không dùng làm bằng chứng trạng thái merchant hôm nay.
- Codebase-memory đã kiểm tra index/coverage các file liên quan; các file VNPAY có metadata_match. Đọc source trực tiếp để kết luận; không dựa vào kết quả graph search rỗng để nói chức năng không tồn tại.

## Vị trí xử lý

| File | Vai trò |
|---|---|
| `src/main/resources/application.properties:24–28` | Mapping biến VNPAY từ môi trường |
| `src/main/java/com/example/backend/order/payment/config/VnpayProperties.java:12` | Cấu hình/validation; sandbox URL dòng 18 |
| `src/main/java/com/example/backend/order/payment/service/VnpayServiceImpl.java:49` | Tạo URL; merchant dòng 60, return URL dòng 64, ký URL dòng 68 |
| `src/main/java/com/example/backend/order/payment/service/VnpaySignature.java` | Encode, canonical query, HMAC-SHA512 |
| `src/main/java/com/example/backend/order/payment/controller/VnpayController.java` | Config, mở URL cho đơn thuộc customer, đối soát |
| `src/main/java/com/example/backend/order/payment/controller/VnpayCallbackController.java` | Endpoint return và IPN |

## Cách khắc phục đúng phạm vi

1. Đối chiếu `VNPAY_TMN_CODE` tại `backend/.env` với terminal Sandbox được cấp và trạng thái phê duyệt trong tài khoản VNPAY. Nếu terminal chưa duyệt, yêu cầu VNPAY kích hoạt; nếu dùng nhầm, thay bằng cặp TmnCode/HashSecret Sandbox của cùng terminal đã duyệt.
2. Restart backend sau khi cập nhật cấu hình; không cần đổi thuật toán chữ ký để xử lý code=71.
3. Mở thanh toán lại qua ứng dụng với phiên hợp lệ theo quy tắc hiện có. Nếu phiên hết hạn, thao tác theo quy trình đơn chưa thanh toán của ứng dụng; không chỉnh DB hoặc giả lập PAID.
4. Sau khi vào được màn hình thanh toán, mới kiểm tra tiếp return/IPN/QueryDr. Localhost return dùng được trên trình duyệt cùng máy; IPN từ VNPAY cần địa chỉ backend công khai phù hợp và đăng ký đúng. Việc sửa IPN không thay thế bước duyệt terminal.

Không thay credentials, code, trạng thái đơn/payment hoặc tồn kho. Không tạo giao dịch mới, không gọi sync cho đơn thật. Không chạy test suite vì chỉ chẩn đoán/read-only, không thay implementation. Chưa thực hiện thanh toán Sandbox end-to-end nên chưa kết luận payment flow hoàn toàn đúng.

## Nguồn

- [Trang lỗi 71 chính thức](https://sandbox.vnpayment.vn/paymentv2/Payment/Error.html?code=71) — GET trực tiếp bằng HTTP client trả nội dung nêu trên.
- [Hướng dẫn tích hợp PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) — định nghĩa TmnCode/ReturnUrl, số tiền nhân 100 và checksum.

## Kiểm tra bổ sung bằng credentials thật trong .env

Theo yêu cầu cho phép đọc `.env`, đã kiểm tra trực tiếp cấu hình ngày 27/10/2026; không xuất giá trị credentials ra log/báo cáo:

- `backend/.env`: enabled=true, TmnCode 8 ký tự, HashSecret 32 ký tự, không phải placeholder theo các dấu hiệu thông thường. Root `.env` không khai báo VNPAY. Chưa xác minh được các biến môi trường có ghi đè cấu hình bên trong JVM hay không.
- Thời điểm sửa backend `.env`: 18:07:48; tiến trình backend đang lắng nghe 8080 khởi động 18:34:12, cùng ngày, profile dev và cwd là backend. File đã có trước lúc khởi động; không có dấu hiệu file này được đổi sau khi backend khởi động.
- POST API tra danh sách ngân hàng Sandbox với TmnCode hiện tại trả HTTP 200, 75 mục. Đây là API tra cứu; không được dùng kết quả này làm bằng chứng terminal đã được duyệt.
- Tạo URL Sandbox có chữ ký bằng đúng TmnCode/HashSecret trong file, sắp xếp/encode tham số và HMAC-SHA512 tương ứng code backend. Dùng reference UUID ngẫu nhiên không liên kết DB, amount 10.000 VND, create/expire hiện tại theo giờ Việt Nam. Chỉ mở URL tới gateway, không nhập thông tin ngân hàng, không submit thanh toán, không tạo order/payment trong business DB. Đây là probe cấu hình độc lập, không phải checkout E2E qua Spring service.
- **Kết quả URL có credentials: HTTP 302 → `/paymentv2/Payment/Error.html?code=71` → HTTP 200, “chưa được phê duyệt”.** Khác với lần GET trang lỗi tĩnh ở trên, probe này xác nhận bộ cấu hình local thực sự gặp gateway refusal 71.

Không thể khắc phục trạng thái phê duyệt terminal bằng thay đổi code local. Cần chủ tài khoản xác nhận/cấp terminal Sandbox đã được VNPAY duyệt và cặp credentials tương ứng. Chưa có bộ credentials thay thế được xác minh nên giữ nguyên `.env`; không tự dùng merchant demo hoặc credentials của dự án khác. Gateway có thể kiểm tra trạng thái terminal trước chữ ký, nên lỗi 71 cũng chưa chứng minh HashSecret là đúng cặp.

Không restart backend khi cấu hình không thay đổi; không tự ghi PAID hoặc bỏ kiểm tra callback. Phần chưa hoàn tất là kích hoạt terminal/nhận credentials đã duyệt và chạy lại kiểm thử sau đó, không phải một bản sửa code đã được triển khai.

### Kiểm tra lại sau khi người dùng sửa .env

- File `backend/.env` có mtime 27/10/2026 18:55:04 +07:00, mới hơn tiến trình backend trước đó (18:34:12). Không công khai giá trị cấu hình.
- Dùng credentials mới trong file để tạo một URL probe độc lập, reference ngẫu nhiên không gắn business order, không submit thanh toán: gateway vẫn HTTP 302 → Error.html?code=71 → HTTP 200, thông báo “chưa được phê duyệt”; chưa tới form chọn ngân hàng.
- Đã dừng đúng tiến trình Java thuộc backend project và khởi động lại để nạp `.env` mới. Các dịch vụ khác giữ chạy. Xác minh đăng nhập development và GET `/api/payments/vnpay/config`: HTTP 200, enabled=true.
- Kết luận: đã nạp lại backend; lỗi terminal vẫn tái hiện với credentials mới. Chưa xác minh approval trong tài khoản merchant và chưa chứng minh HashSecret đúng cặp vì gateway từ chối terminal trước. Không tạo đơn/payment hoặc chỉnh dữ liệu để thử thanh toán.

### Đối chiếu email cấp tài khoản người dùng cung cấp

- Cặp TmnCode/HashSecret trong email khác giá trị local trước lần đối chiếu này. Đã cập nhật đúng hai biến vào `backend/.env`, giữ enabled=true. Xác minh `.env` được Git ignore; không lưu credentials vào báo cáo/source.
- Probe signed URL độc lập bằng cặp credentials email vẫn chuyển HTTP 302 tới Error.html?code=71, HTTP 200 và thông báo “chưa được phê duyệt”. Không tạo business order/payment và không submit thanh toán.
- Đã restart riêng backend để nạp cấu hình email, các dịch vụ còn lại giữ chạy.
- GET local `/api/payments/vnpay/ipn` không kèm chữ ký trả HTTP 200, `RspCode=97`, `Message=Invalid signature`: route hoạt động và chặn notification không có chữ ký. Probe này không chứng minh callback hợp lệ có thể đến từ VNPAY hay payment cập nhật thành công.
- IPN path cần đăng ký là `/api/payments/vnpay/ipn` trên một hostname HTTPS công khai truy cập được. `localhost:8080` chỉ dùng kiểm tra local. Repo có script `backend/scripts/vnpay_ipn_tunnel.py` để mở riêng route IPN khi cần; chưa khởi động tunnel/chưa đăng ký URL thay người dùng.
- Email cấp thông tin kết nối chưa đủ chứng minh trạng thái terminal được duyệt. Cần gửi lỗi 71 cho VNPAY kèm TmnCode và mã tra cứu/thời điểm lỗi; không gửi HashSecret.

### Kiểm tra hai .env và binding Spring thực tế

- Root `.env` và `backend/.env` hiện đều có năm khóa VNPAY, giá trị tương ứng giống nhau hoàn toàn. Không in giá trị.
- Tiến trình Java 8080 chạy từ thư mục backend, không phát hiện VNPAY_* trong môi trường tiến trình ghi đè file.
- Tạo harness Java riêng tại `.local/vnpay-env-binding-check/EnvBindingProbe.java`, không thêm endpoint hoặc sửa code ứng dụng. Khởi động Spring context tối thiểu, dùng thư viện dotenv đóng trong JAR và enable chính VnpayProperties. Không bật datasource/autoconfig/seed.
- Chạy harness từ cả root và backend. Bean VnpayProperties khớp backend `.env` ở toàn bộ năm field trong cả hai lần; property source là `dotenvPropertySource-systemEnvironment`.
- Kiểm tra bytes của VnpayProperties/VnpayServiceImpl/VnpaySignature dùng bởi harness trùng classes trong JAR đang chạy. Gọi chính `VnpayServiceImpl.paymentUrl` với Order/Payment giả chỉ trong RAM; không có DB repositories và không lưu dữ liệu.
- Dùng URL do hàm Java tạo để gọi gateway: cả hai thư mục đều HTTP 200 sau redirect tới Error.html?code=71, thông báo chưa được phê duyệt. Đây là kiểm tra service/config độc lập, không phải checkout HTTP end-to-end hoặc đọc bean của JVM backend đang chạy.
- Lần gọi HTTP ban đầu bằng urllib gặp URLError; chạy lại cùng probe qua httpx thành công và lấy được lỗi 71. Không gọi URLError là kết quả thanh toán.
- Evidence local đã loại secrets: `.local/vnpay-env-binding-check/actual-service-results.json`. Không lưu URL có chữ ký hay credentials. Harness Java không chứa credentials.

Kết luận giới hạn: ở cấu hình file hiện tại, chọn root `.env` hay backend `.env` đều cho cùng cấu hình VNPAY và cùng lỗi. Đã loại trừ giả thuyết lỗi do hai file này có giá trị VNPAY khác nhau và kiểm chứng Spring binding mới. Điều này không chứng minh mọi logic backend đều đúng hoặc merchant bị khóa; lỗi gateway 71 với bộ cấu hình hiện tại vẫn cần đối chiếu trạng thái/cấu hình terminal tại VNPAY.
