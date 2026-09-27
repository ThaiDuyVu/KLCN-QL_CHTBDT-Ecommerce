# Cart → Checkout COD → Order (phase hiện tại)

> Tài liệu này ghi lại phase COD ban đầu. Checkout hiện đã bổ sung trả góp nội bộ và VNPAY Sandbox; phần VNPAY/configuration/IPN xem [vnpay-sandbox.md](vnpay-sandbox.md). Các đoạn “chỉ COD” hoặc “Coming soon” bên dưới là mô tả phase cũ.

Không thêm/chỉnh migration. Các entity ánh xạ carts, cart_items, orders,
order_items, payments và inventory hiện có; không dựng lại Auth.

## API và authorization

| API | Role / ownership |
| --- | --- |
| GET /api/cart | CUSTOMER, customer lấy từ principal |
| POST /api/cart/items | CUSTOMER; {variantId, quantity}; cộng vào item cùng variant |
| PATCH /api/cart/items/{itemId} | CUSTOMER; {quantity}; chỉ item trong cart của mình |
| DELETE /api/cart/items/{itemId} | CUSTOMER; chỉ item trong cart của mình |
| POST /api/orders/checkout | CUSTOMER; checkout cart hiện tại |
| GET /api/orders/mine | CUSTOMER; chỉ đơn của mình, page/size |
| GET /api/orders/mine/{id} | CUSTOMER; chỉ đơn của mình |
| POST /api/orders/mine/{id}/cancel | CUSTOMER; chỉ PENDING, chưa PAID |
| GET /api/orders | STAFF/MANAGER/ADMIN; page/size |
| GET /api/orders/{id} | STAFF/MANAGER/ADMIN |
| PATCH /api/orders/{id}/status | STAFF/MANAGER/ADMIN; {status}, state machine |

Migration hiện chỉ seed permission User/Employee/Customer; **chưa có permission
Cart/Order**. Phase này dùng RequireAnyAuthority theo convention Catalog với role
được yêu cầu, không giả lập authority permission chưa có. Nếu muốn cấu hình quyền
chi tiết trên Roles & Permissions, cần thống nhất permission đọc/quản lý đơn và
mapping role trước khi bổ sung migration dữ liệu; task này không thay migration.

Mọi mutation dùng CSRF/cookie Auth hiện có. CUSTOMER không truy cập API quản trị
đơn. Truy cập đơn/item của customer khác trả 404. Customer chưa có profile trả 403.
List mặc định page=0, size=20 (1–100), sort orderDate DESC/orderId DESC, DTO page.
List là thông tin tổng quan; detail tải items/payment/variant bằng truy vấn theo
order và batch variant, không gọi detail riêng cho từng đơn trong list.

## Checkout contract

```json
{
  "recipientName": "Khách hàng thử",
  "recipientPhone": "0901234567",
  "shippingAddress": "TP. Hồ Chí Minh",
  "note": null,
  "paymentMethod": "COD"
}
```

Tên/phone/địa chỉ bắt buộc và trim; tên tối đa 255, phone tối đa 30 theo schema.
Note tùy chọn. COD được hỗ trợ; enum contract có VNPAY/INSTALLMENT nhưng checkout
hai phương thức này trả 400. Không nhận giá/tổng tiền/customerId làm nguồn tính.
Cart rỗng, variant/product INACTIVE hoặc stock thiếu trả lỗi và giữ nguyên cart.
Quantity phải nguyên dương. Giới hạn tổng tiền theo NUMERIC(15,2) của migration.

Backend đọc giá ProductVariant hiện tại một lần để snapshot unitPrice và
costPrice vào OrderItem; finalUnitPrice=unitPrice, item discountAmount=0,
subtotal=sum(finalUnitPrice*quantity), order discountAmount=0, shippingFee=0,
totalAmount=subtotal. Không dùng unit_price cũ trong cart để tính checkout.
Tên sản phẩm/SKU trả từ Catalog hiện tại vì schema OrderItem không có snapshot
các trường này; giá/giá vốn đã snapshot không đổi khi Catalog đổi giá.

Checkout tạo đúng một Payment COD, amount=totalAmount, status=PENDING,
transactionCode=null. Không tạo VNPAY/INSTALLMENT record song song. DTO không lộ
giá vốn cho customer. Không có API thu tiền/đánh dấu PAID trong phase này;
DELIVERED không tự khẳng định đã thu COD. Payment của đơn hủy vẫn giữ record
PENDING hiện có; payment collection/refund/status mở rộng cần flow riêng.

Reserve stock, tạo order/items/payment và clear cart items trong **một transaction**.
Cart ACTIVE được giữ lại và updated_at cập nhật. Khóa row Customer cho mọi thao tác
cart/checkout: không tạo hai active cart qua service và không checkout cùng cart
hai lần. Request thất bại giữa chừng rollback cả reserve/order/payment/cart.
Khi request mạng bị gián đoạn sau commit, kiểm tra My Orders trước khi đặt lại.

## State machine và inventory

- PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED.
- PENDING → CANCELLED; CONFIRMED → CANCELLED chỉ quản trị.
- Customer chỉ cancel PENDING; mọi role đều không cancel khi payment PAID.
- PROCESSING/SHIPPED/DELIVERED không cancel; Return/Refund ngoài phạm vi.
- Terminal DELIVERED/CANCELLED không xử lý lại, tránh trừ/release stock hai lần.

Checkout reserve quantity, giữ quantity thật. Cancel giảm reserved_quantity,
giữ quantity thật. Delivered giảm cả quantity và reserved_quantity đúng số lượng.
Khóa pessimistic row Order khi chuyển trạng thái, Payment khi kiểm tra PAID và
các row inventory theo variant. Tất cả thao tác stock khóa variant theo cùng
thứ tự UUID Java, row inventory theo inventoryId, để tránh deadlock giữa nhiều SKU.
Available=sum(quantity-reserved_quantity); đủ stock mới reserve, tránh oversell.

**Giới hạn schema:** Order/OrderItem chưa lưu warehouse/allocation. Phase này xử lý
reservation theo **pool tổng variant**, phân bổ và giải phóng trên các row inventory
theo thứ tự cố định; không cam kết giữ cùng kho cho một order. Không trộn pool này
với reservation dành riêng cho nghiệp vụ khác. Kho cấp phát cố định, shipment kho,
serial/IMEI và return/refund cần contract/allocation riêng trước khi mở rộng. serial_id
của OrderItem để null hợp lệ theo migration. Không tạo shipment/serial giả.

## Frontend và kiểm tra

CUSTOMER: Product Detail có nút thêm variant; /cart, /checkout, /my-orders,
/my-orders/:orderId. Quản trị: /orders, /orders/:orderId. Form chỉ gửi địa chỉ,
phương thức và note; VNPAY/Trả góp disabled Coming soon. Sau checkout chuyển sang
chi tiết order với kết quả thành công; list/detail/status đều dùng API thật.

Cần customer profile, Product/ProductVariant ACTIVE và inventory có available
stock. Production nhập tồn qua Goods Receipt. Môi trường dev có thể bật
`--app.seed.inventory.enabled=true`; seed này chạy sau ProductVariant seed, chỉ
tạo tồn ban đầu cho các SKU `DEV-*` còn thiếu và không ghi đè tồn hiện có. Không
seed cart/order/payment.

```bash
cd backend
./mvnw -o -Dtest=OrderWorkflowTest,OrderCheckoutIntegrationTest test
cd ../Fontend
npm run test -- src/features/orders/Commerce.test.jsx --pool=threads --maxWorkers=1
npm run lint
npm run build
```

Integration dùng container pgvector/pgvector:pg16 tạm chạy migration thật và
validate entity; cần Docker. Bảng truncate chỉ trong database container kiểm thử.
Kiểm tra concurrent hai customer/same customer, rollback nhiều SKU, snapshots,
Payment COD, state transitions, ownership và HTTP authorization/validation.
Không coi test integration bị skip vì thiếu Docker là đã xác minh concurrency.
