# Chẩn đoán lỗi ghi dữ liệu trong luồng đặt hàng Flutter

Ngày: 04/10/2026. Phạm vi: đọc mã Flutter/backend/frontend và log có sẵn. **Chưa sửa code/cấu hình, chưa gửi POST/PUT/PATCH/DELETE để tái hiện và chưa tạo/xóa đơn hàng.** Báo cáo phân biệt lỗi chắc chắn trong mã với giả thuyết cần xác nhận trên request thực tế.

## 1. Kết luận

Không thể quy mọi lỗi đặt hàng về CSRF. Flutter hiện lệch hợp đồng API backend ở nhiều bước; ngay cả khi CSRF hợp lệ, thêm giỏ/cập nhật số lượng/checkout vẫn có thể thất bại.

- **Đã xác nhận bằng mã:** sai payload thêm giỏ, sai HTTP method cập nhật số lượng, thiếu bước lưu chi nhánh vào giỏ backend, gọi endpoint xóa toàn giỏ không tồn tại, cả hai luồng checkout đều gửi thiếu/sai trường bắt buộc.
- **Đã xác nhận bằng log/kiểm tra ở lượt trước:** access token trên emulator hết hạn gây 401; log có 403 do quyền truy cập CUSTOMER ở GET giỏ/đơn hàng. Những lỗi này không chứng minh CSRF thất bại.
- **CSRF:** backend bật bảo vệ và Flutter có cơ chế gửi token, nhưng triển khai có điểm yếu. Chưa có bằng chứng đủ để kết luận một request đặt hàng cụ thể bị chặn bởi CSRF. Cần đối chiếu request lỗi trước khi kết luận.

## 2. Các lỗi chắc chắn trong hợp đồng API

Nguồn: `flutter/lib/features/cart/data/cart_repository.dart`, `flutter/lib/features/cart/presentation/pages/cart_screen.dart`, `flutter/lib/features/checkout/presentation/pages/checkout_screen.dart`; đối chiếu `backend/src/main/java/com/example/backend/cart/controller/CartController.java`, các DTO trong `cart/dto` và `order/dto/CheckoutRequest.java`.

| Bước | Flutter hiện tại | Backend thực tế | Hậu quả khi đã qua bảo mật |
|---|---|---|---|
| Chọn chi nhánh | Chỉ thay đổi state Flutter; repository chưa có API chọn chi nhánh | `PUT /api/cart/warehouse`, body `{warehouseId}` | Backend chưa có chi nhánh giỏ; thêm hàng có thể trả 409 yêu cầu chọn chi nhánh |
| Thêm giỏ | `POST /api/cart/items` gửi `{productId, quantity, warehouseId}` | Body yêu cầu `{variantId, quantity}` | Thiếu `variantId`: validation 400; `warehouseId` trong payload này không thay cho API chọn chi nhánh |
| Đổi số lượng | `PUT /api/cart/items/{itemId}` | `PATCH /api/cart/items/{itemId}` với `{quantity}` | Sai method: dự kiến 405; CSRF có thể chặn trước khi lỗi này lộ ra |
| Xóa một dòng | `DELETE /api/cart/items/{itemId}` | Có cùng endpoint | Endpoint đúng; cần kiểm tra CSRF, phiên, role và dòng giỏ thuộc người dùng |
| Xóa toàn giỏ | `DELETE /api/cart` | Không có mapping DELETE toàn giỏ | Dự kiến 405 khi qua bảo mật, không phải bằng chứng lỗi CSRF |
| Checkout từ CartScreen | Gửi `{warehouseId, paymentMethod: COD}` | Bắt buộc `recipientName`, `recipientPhone`, `shippingAddress`, `paymentMethod` | Thiếu thông tin người nhận: 400; chi nhánh lấy từ giỏ backend |
| Checkout từ CheckoutScreen | Gửi `{receiverName, phone, address, note}` | Tên trường là `recipientName`, `recipientPhone`, `shippingAddress`; cần `paymentMethod` | Sai ba tên trường và thiếu phương thức thanh toán: 400 |
| Sau checkout từ CheckoutScreen | Gọi `clearCart()` rồi mới chuyển trang đơn hàng | `OrderServiceImpl` đã xóa các dòng giỏ trong transaction checkout | Đơn có thể tạo thành công nhưng DELETE tiếp theo thất bại; UI báo lỗi khiến người dùng hiểu nhầm đặt hàng chưa thành công |

`PaymentMethod` backend hiện hỗ trợ `COD`, `VNPAY`, `INSTALLMENT`. VNPay đang mặc định tắt; luồng xác minh đầu tiên nên dùng COD. Không cần đổi schema hay tạo project mới.

Trang sản phẩm vừa thiết kế đang vô hiệu hóa nút thêm giỏ theo phạm vi trước đó; bảng trên mô tả repository/luồng đặt hàng còn tồn tại, không khẳng định các thao tác này hiện đều có thể bấm từ trang sản phẩm.

## 3. Chẩn đoán CSRF cụ thể

### Backend đang yêu cầu gì?

`common/security/SecurityConfig.java` dùng `CookieCsrfTokenRepository` và `CsrfTokenRequestAttributeHandler`. `application.properties` cấu hình:

- Cookie CSRF: `XSRF-TOKEN`, path `/`.
- Header: `X-XSRF-TOKEN`.
- `GET /api/auth/csrf` tạo/lấy token, trả 204 và cookie khi cần.
- Các thao tác ghi phải gửi cặp cookie/header hợp lệ. `permitAll` cho login/refresh/logout không đồng nghĩa bỏ kiểm tra CSRF.
- Access JWT lấy từ cookie `ACCESS_TOKEN`; `JwtAuthenticationFilter` hiện không đọc Authorization Bearer.

### Flutter có những điểm yếu nào?

Nguồn: `core/network/api_client.dart`, `auth_interceptor.dart`, `csrf_manager.dart`, `core/storage/cookie_storage.dart`.

1. **Hai đường lấy CSRF không thống nhất.** `ApiClient.ensureCsrfToken()` dùng CookieManager chung, nhưng fallback trong AuthInterceptor tạo Dio riêng không gắn cookie jar. Nó lấy token từ Set-Cookie rồi tự ghép cookie/header cho request đang gửi, không lưu cookie đó vào jar chung. Request hiện tại vẫn có thể hợp lệ vì cookie và header cùng token; không được suy diễn rằng cứ thiếu CookieManager ở fallback là chắc chắn 403. Tuy nhiên state các request sau và các request đồng thời dễ không thống nhất.
2. **Fetch CSRF thất bại vẫn cho request ghi đi tiếp.** Catch trong fallback chỉ log lỗi rồi `handler.next(options)`. Nếu chưa có token thì request thiếu CSRF vẫn được gửi, có thể bị 403. Đây là đường lỗi rõ ràng trong mã; chưa thấy log đủ để xác nhận nó đã xảy ra ở lần đặt hàng của bạn.
3. **Ba nơi lưu thông tin CSRF:** cookie jar, `CsrfManager.token`, `_inMemoryCsrfToken`. Không có cơ chế reset đầy đủ khi logout/đổi tài khoản; `ignoreExpires: true` giữ cả cookie hết hạn. Rủi ro đồng bộ là có thật, nhưng cache cũ đơn độc chưa đủ chứng minh mismatch: interceptor đang tự ghép cả cookie và header từ cùng giá trị.
4. **Interceptor ghi đè toàn bộ Cookie header.** Chỉ giữ ACCESS_TOKEN và XSRF-TOKEN, làm rơi REFRESH_TOKEN có path `/api/auth`. Nếu triển khai gọi refresh bằng client này, refresh có thể thiếu cookie. Hiện Flutter chưa có luồng tự gọi refresh; đây là lỗi tiềm ẩn của hướng sửa phiên, không phải bằng chứng đã chạy refresh thất bại trên Flutter.
5. **Authorization Bearer không thay được cookie.** Flutter gắn thêm Bearer nhưng backend chỉ đọc ACCESS_TOKEN cookie. Cookie thiếu/hết hạn vẫn dẫn tới lỗi xác thực.
6. **Đọc cookie ở URI gốc hiện phù hợp path `/`.** Không có căn cứ kết luận `loadForRequest(Uri.parse(baseUrl))` là nguyên nhân hiện tại, vì CSRF cookie đang có path `/`.

Không phát hiện lệch tên cookie/header giữa hai bên. Backend đang dùng handler token thường; chưa có căn cứ cho giả thuyết Flutter gửi sai định dạng XOR token.

## 4. Bằng chứng và giới hạn

- `.local/logs/backend.log`, 12:50:23: GET `/api/cart` và GET `/api/orders/mine` tới controller rồi phát sinh AuthorizationDeniedException, trả 403 “Bạn không có quyền thực hiện thao tác này”. Đây là lỗi role; GET không phải thao tác ghi cần CSRF trong luồng này.
- 12:27:29: backend ghi nhận `/api/auth/refresh` trả 401 “Invalid or expired refresh token”. Log backend chưa đủ xác định request đó đến từ Flutter hay công cụ khác.
- Lượt chẩn đoán kết nối trước đã kiểm tra token emulator hết hạn và GET sản phẩm trả 401. TTL access token trong mã là 15 phút, refresh là 7 ngày. Flutter chưa tự refresh; sửa chuyển về đăng nhập mới chỉ áp dụng cho GET danh mục/chi nhánh/chi tiết.
- Log đọc được hiện không có dấu vết đủ của một POST/PATCH/DELETE đặt hàng lỗi kèm lý do MissingCsrfToken/InvalidCsrfToken. Vì vậy chưa xác nhận CSRF là nguyên nhân đầu tiên của thao tác cụ thể.
- Backend test pass trước đây không chứng minh Flutter gửi đúng hợp đồng hoặc quản lý cookie đúng. Nhiều test gửi CSRF bằng helper test; đó không phải cookie jar Dio thực tế.
- CORS cấu hình chưa cho header Authorization, nhưng Android Flutter native không chịu cơ chế CORS của trình duyệt. Chỉ cần xem xét điểm này nếu chạy Flutter Web hoặc client trình duyệt gửi Bearer. Không đề xuất sửa CORS để giải quyết lỗi Android hiện tại.

## 5. Hướng khắc phục đề xuất để thảo luận

**Khuyến nghị giữ cơ chế cookie + CSRF hiện có, sửa Flutter trước.** Web đã dùng cơ chế này; chưa có căn cứ cần bỏ CSRF hay đổi bảo mật backend.

Thứ tự dự kiến:

1. Thống nhất một cookie jar và một đường lấy CSRF; chia sẻ request đang fetch để tránh cạnh tranh; lấy token phù hợp cookie thực tế; nếu fetch lỗi thì dừng thao tác ghi với thông báo rõ. Không tự lọc bỏ refresh cookie; không log giá trị token.
2. Phân loại lỗi 401/403/400/405/409 và lỗi mạng. 403 không tự động coi là CSRF hoặc retry. Với phiên hết hạn, chọn chính sách đưa về đăng nhập hoặc triển khai refresh đúng cookie, một lần tại một thời điểm. Không tự gửi lại checkout khi timeout vì chưa biết backend đã tạo đơn hay chưa.
3. Sửa hợp đồng giỏ: lưu chi nhánh bằng PUT `/api/cart/warehouse`; chọn phiên bản và gửi variantId; đổi số lượng sang PATCH; giữ DELETE từng dòng. Không gọi endpoint DELETE toàn giỏ không tồn tại. Khi đổi chi nhánh có giỏ, tuân theo quy tắc hiện có của CartService.
4. Dùng một luồng CheckoutScreen với tên trường backend và paymentMethod; bỏ clearCart sau thành công vì backend đã xử lý trong transaction. Hiển thị đơn đã tạo dựa trên response thực tế.
5. Sau khi thống nhất hướng sửa, kiểm chứng từ đầu tới cuối bằng CUSTOMER/COD trên dữ liệu phát triển: CSRF hợp lệ/thiếu/sai, hết phiên, sai role, thêm variant, PATCH số lượng, DELETE dòng, checkout, đối chiếu đơn thực tế và giỏ sau checkout.

**Phương án thay thế:** tách luồng Bearer riêng cho mobile chỉ khi có yêu cầu kiến trúc rõ ràng. Hiện backend chưa hỗ trợ cách đó; sẽ cần thiết kế xác thực/lưu token/refresh riêng và giới hạn CSRF phù hợp, phạm vi lớn hơn. Không khuyến nghị tắt CSRF toàn backend vì web đang xác thực bằng cookie.

### Cách xác nhận nguyên nhân của lần lỗi cụ thể ở bước tiếp theo

Ghi lại method/path, HTTP status, response body đã loại dữ liệu nhạy cảm, thời điểm và role. Chỉ ghi việc cookie/header có tồn tại và hai giá trị CSRF có khớp hay không; không ghi giá trị token. Đối chiếu log backend xem request bị chặn trước controller hay đã vào validation/service. Chỉ tái hiện thao tác ghi sau khi bạn cho phép; báo cáo hiện tại không tạo đơn thử hay thay đổi giỏ.

Điểm cần thống nhất trước khi triển khai: giữ cookie + CSRF và sửa Flutter theo hợp đồng hiện có; phiên hết hạn đưa về đăng nhập trước hay bổ sung tự refresh trong cùng đợt sửa? Với mục tiêu đặt hàng chạy ổn sớm, đề xuất sửa giỏ/checkout và CSRF trước, sau đó bổ sung tự refresh có kiểm thử riêng.


## 6. Cập nhật sau đợt sửa đã được cho phép (04/10/2026)

Các mục phía trên là ảnh chụp chẩn đoán trước sửa. Đã sửa trong Flutter, giữ nguyên backend và schema:

- Một cookie jar cho CSRF và API; một request lấy CSRF dùng chung khi đồng thời; lấy cookie thực tế thay cho cache cũ. Giữ cookie refresh theo path; dừng request ghi nếu fetch CSRF thất bại. Bỏ Bearer không được backend sử dụng và không log giá trị CSRF.
- PUT chọn chi nhánh, xác nhận `clearItems` theo quy tắc backend; POST thêm variantId; PATCH số lượng; DELETE từng dòng. Bỏ DELETE toàn giỏ.
- CartScreen chuyển sang CheckoutScreen; gửi đúng recipientName/recipientPhone/shippingAddress/paymentMethod (COD). Bỏ xóa giỏ sau checkout; tải lại giỏ và danh sách đơn sau thành công.
- Bỏ đăng xuất hai lần; xóa cookie và reset CSRF khi đăng xuất. Cookie hết hạn không tiếp tục được gửi. Chưa bổ sung tự refresh hay tự retry checkout.

Xác minh: full backend **242/242 pass**; Flutter **13 test pass**, một integration test mặc định skip để không tạo đơn ngoài ý muốn. Integration test được chạy riêng với backend thật đã pass cookie/CSRF, thiếu CSRF trả 403, chọn chi nhánh, thêm phiên bản, PATCH, DELETE, COD checkout, giỏ tự rỗng, xem đơn và hủy đơn vừa tạo. Tài khoản `seed.customer2`; đơn mang ghi chú kiểm thử và đã hủy. Không xóa đơn hay giỏ người dùng có sẵn.

Chạy integration riêng (từ thư mục flutter, đã source scripts/env.sh):

```sh
flutter test test/backend_checkout_integration_test.dart --dart-define=RUN_BACKEND_CHECKOUT_TEST=true --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

Lệnh này có ghi dữ liệu phát triển: yêu cầu giỏ seed.customer2 trống, tạo một đơn COD rồi hủy đơn đó. Dừng nếu giỏ đang có sản phẩm. Chưa kiểm thử thanh toán VNPay/trả góp trong đợt này.
