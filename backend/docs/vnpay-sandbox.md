# Hướng dẫn chạy và xử lý vướng mắc VNPAY Sandbox

## Cập nhật: gateway thành công nhưng website vẫn chờ thanh toán

Ngày 26/09/2026, sau khi đổi credentials, người dùng đã thanh toán được trên gateway. Kiểm tra giao dịch mới nhất qua QueryDr: responseCode=00, transactionStatus=00, chữ ký phản hồi hợp lệ và đúng payment reference. Tuy nhiên DB vẫn PENDING, chưa có transactionCode. UI đang đọc đúng DB; chưa xác nhận nguyên nhân IPN chưa cập nhật (chưa đăng ký URL cho merchant mới, chưa nhận callback hoặc callback bị từ chối).

Đã bổ sung đối soát qua API QueryDr của VNPAY:

- Return hợp lệ và thành công sẽ yêu cầu backend truy vấn trực tiếp VNPAY. Không ghi PAID từ query trên browser.
- Chỉ phản hồi QueryDr có chữ ký hợp lệ, đúng merchant/reference/amount, transactionType=01 và transactionStatus=00 mới cập nhật PAID bằng transaction/locking hiện có.
- POST /api/payments/vnpay/orders/{orderId}/sync: CUSTOMER đối soát đơn của mình, có CSRF/ownership. Đơn người khác trả 404.
- Trang chi tiết đơn có nút “Kiểm tra kết quả thanh toán”; trang kết quả có nút kiểm tra lại. UI tải lại Order API để hiển thị trạng thái thực tế.
- Không gọi QueryDr theo mỗi lần poll: trang kết quả chỉ poll DB; QueryDr được gọi từ return hoặc thao tác kiểm tra lại.
- VNPAY có thể trả mã 94 khi truy vấn lặp trong thời gian giới hạn. Khi đó giữ nguyên Payment, hiển thị yêu cầu chờ vài phút rồi kiểm tra lại. Không thanh toán lại nếu đã trừ tiền.
- IPN vẫn là đường cập nhật chính và phải đăng ký với merchant mới. Backend log RspCode để phân biệt chưa nhận callback với bị từ chối; không log secret/chữ ký.
- Không đổi OrderStatus, Inventory, Serial, Warranty hoặc schema.

Sau khi chạy backend mới, mở đơn đã thanh toán và bấm “Kiểm tra kết quả thanh toán”. Nếu chưa PAID, xem thông báo/log; không tự sửa DB để giả lập thành công.

[Đặc tả QueryDr và giới hạn mã 94](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

### Kết quả xác minh sau sửa

Đã chạy backend mới tạm trên port 8081, tắt toàn bộ seed flags, và đối soát chính giao dịch Sandbox mới nhất qua API được bảo vệ. Kết quả:

- CUSTOMER khác gọi sync trả 404.
- CUSTOMER sở hữu đơn gọi sync thành công: HTTP 204, Order API trả Payment=PAID và có transactionCode; Order vẫn PENDING.
- Gọi sync khi đã PAID không cần truy vấn lại gateway.
- Tổng quantity/reservedQuantity trong Inventory không thay đổi.
- Backend package thành công; frontend 42 tests pass, lint và production build thành công.

Backend chính đang chạy cần restart để nạp code mới. Đơn đã đối soát được lưu trong database chung nên reload chi tiết đơn sẽ đọc được PAID. Vẫn cần kiểm tra đăng ký IPN cho merchant mới; QueryDr là fallback, không thay việc đăng ký callback.

### API sync trả 404 sau khi sửa code

Lần kiểm tra tiếp theo xác định backend port 8080 vẫn là tiến trình khởi động trước khi thêm route `/sync`, nên frontend đã có nút mới nhưng server trả 404. Đã khởi động lại backend chính trên 8080 bằng JAR vừa build, profile dev, tắt seed trong lần chạy này. Đối soát đúng đơn `325b17dd-e777-46dc-aba7-53942ad2af0a`: POST sync trả 204, Order API trả Payment=PAID, transactionCode đã ghi nhận, Order=PENDING và customer không còn allowedStatuses CANCELLED.

Sau khi sửa Java controller/service phải restart backend; Vite hot reload chỉ cập nhật frontend. Nếu gặp lại 404, kiểm tra request dùng POST đúng route, tiến trình backend đang chạy bản mới và proxy frontend trỏ đúng backend trước khi đổi logic thanh toán.

## Vướng mắc trước đó: lỗi 71

Tình trạng kiểm tra ngày 26/09/2026: checkout chuyển sang VNPAY nhưng cổng thanh toán báo **code=71 — “The terminal (website) not approved”**. Merchant/terminal đang dùng chưa được VNPAY duyệt. Chưa hoàn tất được thanh toán end-to-end và chưa xác nhận Payment được cập nhật PAID từ giao dịch Sandbox thật.

Đây là blocker phía VNPAY. Thay đổi code checkout, chữ ký hoặc URL IPN không làm terminal được duyệt. Cần kiểm tra mã merchant đúng môi trường Sandbox và yêu cầu VNPAY duyệt/kích hoạt terminal.

| Phần | Kết quả đã kiểm tra |
| --- | --- |
| Cấu hình local | Đã có VNPAY_ENABLED=true, merchant code và hash secret trong backend/.env |
| Nạp cấu hình | Trước đó backend trả enabled=false vì khởi động trước khi sửa .env; phải restart sau mỗi lần thay cấu hình |
| Chuyển sang gateway | Người dùng đã tới gateway, hiện bị chặn bởi lỗi 71 |
| IPN công khai | Đã kiểm tra HTTP 200, RspCode=97 với request thiếu chữ ký; các API khác qua tunnel trả 404 |
| Merchant được duyệt | Chưa xác nhận; gateway đang báo chưa được duyệt |
| Đăng ký IPN tại VNPAY | Chưa xác nhận đã đăng ký cho merchant |
| Payment PAID trong DB từ giao dịch thật | Chưa xác nhận |

Credentials có mặt/đúng độ dài không chứng minh merchant đã được duyệt hay secret hợp lệ với merchant đó.

## Việc cần làm tiếp theo

1. Đối chiếu VNPAY_TMN_CODE với mã merchant Sandbox VNPAY đã cấp. Hash secret phải thuộc cùng merchant, cùng môi trường.
2. Yêu cầu VNPAY kiểm tra/duyệt terminal đang báo lỗi 71. Nếu chưa có merchant Sandbox hợp lệ, đăng ký tại [VNPAY Sandbox](https://sandbox.vnpayment.vn/devreg/).
3. Đăng ký IPN URL công khai bên dưới với merchant đó. IPN được cấu hình phía VNPAY, không phải qua VNPAY_RETURN_URL.
4. Nếu được cấp lại mã/secret, cập nhật backend/.env và khởi động lại backend.
5. Sau khi terminal được duyệt và IPN được đăng ký, thực hiện giao dịch Sandbox mới theo mục kiểm thử bên dưới.

Không gửi hash secret trong trao đổi hỗ trợ hoặc commit vào Git. Có thể cung cấp mã terminal, thời gian lỗi, ảnh lỗi và mã tra cứu gateway để VNPAY kiểm tra.

## IPN công khai hiện tại

URL đã mở và kiểm tra trong phiên làm việc:

```text
https://kennedy-motion-extraction-serial.trycloudflare.com/api/payments/vnpay/ipn
```

Đây là URL tạm thời. Chỉ dùng nếu tunnel vẫn đang chạy; khởi động tunnel lại sẽ có hostname mới và cần đăng ký lại với VNPAY. Giữ backend và tunnel chạy trong suốt giao dịch.

Kiểm tra URL hiện tại:

```bash
curl --max-time 20 -i 'https://kennedy-motion-extraction-serial.trycloudflare.com/api/payments/vnpay/ipn'
```

Kết quả mong đợi cho request KHÔNG có chữ ký:

```json
{"RspCode":"97","Message":"Invalid signature"}
```

HTTP 200 + mã 97 chỉ chứng minh endpoint truy cập được và từ chối request không hợp lệ; không chứng minh thanh toán thành công. Không tự tạo callback thành công để thay cho giao dịch VNPAY thật.

## Cấu hình local

Giữ nguyên các biến database/auth hiện có trong backend/.env. Bổ sung hoặc cập nhật các biến sau bằng credentials của bạn:

```dotenv
VNPAY_ENABLED=true
VNPAY_TMN_CODE=<merchant Sandbox được cấp>
VNPAY_HASH_SECRET=<secret của cùng merchant>
VNPAY_RETURN_URL=http://localhost:8080/api/payments/vnpay/return
VNPAY_FRONTEND_RESULT_URL=http://localhost:5173/payments/vnpay/result
```

Hai URL localhost phù hợp khi trình duyệt kiểm thử chạy trên chính máy này. Không thay VNPAY_RETURN_URL bằng đường dẫn IPN; hai endpoint có nhiệm vụ khác nhau. File .env.vnpay.example chỉ là mẫu, không được nạp tự động.

Gateway hiện cố định ở Sandbox, không phải production:

```text
https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
```

## Lệnh chạy

### Terminal 1 — Backend

Dừng backend cũ bằng Ctrl+C trước khi chạy lại để nạp .env mới:

```bash
cd /Users/thaivu/Documents/LapTrinh/HUIT-YEAR4/KLTN/QL_CHTBDT_Ecommerce/backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### Terminal 2 — Frontend

```bash
cd /Users/thaivu/Documents/LapTrinh/HUIT-YEAR4/KLTN/QL_CHTBDT_Ecommerce/Fontend
npm run dev
```

### Terminal 3 — Tunnel IPN

Nếu tunnel hiện tại vẫn chạy thì giữ nguyên, không chạy thêm trên cùng port. Nếu cần mở lại:

```bash
cd /Users/thaivu/Documents/LapTrinh/HUIT-YEAR4/KLTN/QL_CHTBDT_Ecommerce/backend
python3 scripts/vnpay_ipn_tunnel.py
```

Máy cần có cloudflared; nếu chưa cài:

```bash
brew install cloudflared
```

Lấy hostname HTTPS được in ra, nối thêm /api/payments/vnpay/ipn rồi đăng ký URL mới với VNPAY. Script chỉ mở GET endpoint IPN ra ngoài, không mở toàn bộ backend. Dừng tunnel bằng Ctrl+C. Muốn URL ổn định cần named tunnel/domain hoặc backend được deploy; Quick Tunnel chỉ phục vụ kiểm thử tạm thời.

## Kiểm thử sau khi VNPAY duyệt terminal

1. Đăng nhập CUSTOMER. Trong Network của trình duyệt, GET /api/payments/vnpay/config phải trả {"enabled":true}. Endpoint yêu cầu đăng nhập; truy cập ẩn danh trả 401 là đúng.
2. Chọn chi nhánh có tồn kho, thêm variant vào cart, nhập đủ địa chỉ và chọn VNPAY ở checkout.
3. Checkout thành công phải tạo Order=PENDING, một Payment=VNPAY/PENDING, reserve tồn đúng warehouse và clear cart. Backend tự tính giá.
4. Chuyển sang gateway phải hiển thị màn hình thanh toán, không còn lỗi 71. Dùng thông tin kiểm thử Sandbox do VNPAY cung cấp; không dùng thẻ thật.
5. Hoàn tất giao dịch, trình duyệt quay về trang kết quả. VNPAY đồng thời gửi IPN tới URL đã đăng ký.
6. Xem chi tiết đơn: Payment phải PAID, có transactionCode và dữ liệu tương ứng trong DB. Order vẫn PENDING; callback không tự CONFIRM Order.
7. STAFF/MANAGER/ADMIN mới tiếp tục CONFIRM theo state machine hiện tại. VNPAY chưa PAID phải bị chặn CONFIRM.

Nếu browser báo thành công nhưng Payment vẫn PENDING, kiểm tra tunnel còn chạy, URL IPN đã đăng ký đúng merchant và backend có nhận callback không. Browser return không tự ghi PAID từ tham số trả về; backend có thể đối soát bằng phản hồi QueryDr đã xác thực như phần cập nhật ở đầu tài liệu.

## Các lỗi cần phân biệt

| Hiện tượng | Việc cần kiểm tra |
| --- | --- |
| Gateway Error.html?code=71 | Terminal chưa được duyệt: đối chiếu merchant Sandbox và liên hệ VNPAY |
| Frontend không chọn được VNPAY | Backend runtime enabled, restart sau khi sửa .env |
| IPN probe trả 97 | Bình thường khi không có chữ ký; nếu là callback thật thì đối chiếu secret/merchant và dữ liệu ký |
| IPN thật trả 04 | Amount callback không khớp Payment; không tự sửa tổng tiền hay bỏ kiểm tra |
| IPN thật trả 99 | Notification không hợp lệ hoặc cập nhật lỗi; cần xem lỗi backend, không giả lập PAID |
| Return thành công nhưng Payment PENDING | IPN chưa tới/chưa xử lý: kiểm tra đăng ký URL, tunnel và callback |
| Phiên thanh toán hết hạn | Phiên hiện có thời hạn 15 phút, mở lại không gia hạn; với đơn chưa thanh toán và đủ điều kiện, hủy qua UI để release stock rồi đặt lại |

Gateway code=71 khác với RspCode mà backend trả cho IPN. Đơn đã PAID hoặc giao dịch cần đối soát không được xử lý như đơn chưa thanh toán. Flow hiện chưa có refund tự động.

## Contract giữ nguyên

- IPN xác thực chữ ký HMAC-SHA512, merchant, payment reference và amount trước khi cập nhật Payment.
- Payment thành công chỉ chuyển PAID. IPN không đổi OrderStatus, inventory, serial hay warranty.
- Order vẫn theo PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED và các quy tắc cancel hiện có.
- Không có migration mới cho VNPAY; không sử dụng secret từ project tham khảo.

## Nguồn tham khảo

- [Trang lỗi 71 của gateway](https://sandbox.vnpayment.vn/paymentv2/Payment/Error.html?code=71).
- [Hướng dẫn tích hợp PAY: payment URL, Return và IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- [Bảng mã phản hồi thanh toán](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/).
- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/).
