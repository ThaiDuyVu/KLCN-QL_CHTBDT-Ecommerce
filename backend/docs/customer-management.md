# Customer Management

Module đọc hồ sơ và lịch sử kinh doanh tại `/customers` và `/customers/:customerId`. Không CRUD hồ sơ, không hard-delete, không đổi role/status/password, không sửa điểm tích lũy, không thay đổi checkout/cart/order/warranty/installment flows. Không tạo entity Customer mới hay sửa migration/schema. Không viết test mới.

## API

Tất cả GET:
- `/api/customers?page=0&size=20&keyword=&status=`
- `/api/customers/{customerId}`
- `/api/customers/{customerId}/orders?page=0&size=20`
- `/api/customers/{customerId}/warranties?page=0&size=20`
- `/api/customers/{customerId}/installments?page=0&size=20`

Pagination zero-based, size 1–100, offset <= Integer.MAX_VALUE. Frontend list dùng size=20, lịch sử size=10. Response: content/page/size/totalElements/totalPages. Customer không tồn tại 404; tham số sai 400; lịch sử rỗng trả page rỗng, không phải 404.

Keyword trim, tối đa 255 ký tự, case-insensitive theo fullName/username/email/phone, `%`, `_`, `\` được escape để là ký tự tìm kiếm thật. Status dùng UserStatus ACTIVE/INACTIVE/LOCKED; không thêm trạng thái mới.

## Authorization

List/detail/orders/warranties yêu cầu cả:
- role authority thuộc ADMIN/MANAGER/STAFF (`@PreAuthorize`);
- ADMIN hoặc CUSTOMER_VIEW (`@RequireAnyAuthority`).

Migration V1 đã cấp CUSTOMER_VIEW cho MANAGER và STAFF, nên STAFF có quyền đọc phù hợp hiện trạng. CUSTOMER vẫn bị chặn dù một role configuration vô tình cấp CUSTOMER_VIEW. Không tạo permission mới, không sử dụng CUSTOMER_CREATE/UPDATE.

Installments chỉ ADMIN/MANAGER theo convention InstallmentController hiện tại. STAFF không gọi được endpoint này và không có tab/CTA trả góp trên frontend. Customer Management không có endpoint cập nhật installment.

Frontend route RequireAuth + RequireRole(MANAGEMENT_ROLES) hiện có. Link User Management chỉ hiển thị ADMIN, tiếp tục tới màn hình quản lý user đã có. Backend luôn là nguồn kiểm soát quyền; role navigation không thay thế CUSTOMER_VIEW.

## Hồ sơ và summary

CustomerSummaryResponse được dùng cho cả list/detail để frontend đọc profile và orderSummary nhất quán. Fields: customerId/userId/fullName/address/loyaltyPoint, username/displayName/email/phone/accountStatus/createdAt, orderSummary. createdAt là User.createdAt; Customer không có createdAt riêng.

orderSummary:
- totalOrders: COUNT mọi Order của customer.
- deliveredOrders: COUNT status DELIVERED.
- cancelledOrders: COUNT status CANCELLED.
- totalSpend: SUM(Order.totalAmount) chỉ DELIVERED, snapshot gồm shippingFee đã lưu; không đọc giá Product hiện tại, không dựa vào Payment.PAID.
- latestOrderId/code/date/status: đơn mới nhất theo orderDate DESC, orderId DESC. MAX(orderDate) + DISTINCT ON với tie-breaker ổn định.
- Chưa có đơn: count/spend = 0, latestOrder fields = null.

Address là địa chỉ Customer profile, không thay địa chỉ giao nhận snapshot của từng Order. LoyaltyPoint chỉ hiển thị giá trị đang lưu; không đưa ra rule cộng/trừ mới.

## Query strategy

CustomerManagementRepository dùng NamedParameterJdbcTemplate, projection DTO nhỏ; service riêng, controller mỏng. Không inject vào UserService, không hydrate entity graph.

- List: COUNT + LIMIT/OFFSET profile JOIN User + **một batch aggregate** Order cho IDs trên trang (≤100). Không loop customer rồi query; tổng 3 query, trang rỗng bỏ aggregate.
- Detail: 1 profile query + 1 aggregate cho customer đó.
- Order history: existence check + COUNT + page projection; không tải OrderItems, không join Payment gây fan-out. Sort orderDate/orderId DESC.
- Warranty history: existence + COUNT + projection page JOIN Serial/Variant/Product/OrderItem/Order; IMEI lấy một batch cho serial IDs của trang. Không query IMEI từng row. Sort startDate/warrantyId DESC.
- Rule hiệu lực bảo hành được tách nguyên trạng sang WarrantyStatus.effectiveOn(endDate,today) và WarrantyServiceImpl dùng lại cùng helper. Không duplicate eligibility/ticket logic; không scheduler hay mutation EXPIRED khi đọc.
- Installment history: existence + COUNT + page qua InstallmentPayment → Payment → Order.customerId, join Provider để lấy tên; không tải Order graph. Sort orderDate/installmentId DESC.
- Java chỉ ghép projection/batch đã phân trang, không count/sum toàn lịch sử trong Java.
- SQL tham số bind, không concatenate keyword/UUID do client gửi. Aggregate CTE chỉ đọc customer IDs trên trang.
- Transaction readOnly, REPEATABLE_READ, timeout 10s; JDBC template riêng Customer Management timeout 5s. Không row locks/SELECT FOR UPDATE, không writes.

## Index

Đã xác nhận trong DB hiện có: idx_orders_customer_id, idx_warranties_customer_id, uq_customers_user_id/idx_customers_user_id, idx_payments_order_id, uq_installment_payments_payment_id. Dùng các quan hệ/index này để lọc/join.

Không thêm index/migration: dataset hiện tại nhỏ, chưa có bằng chứng cần composite index. Keyword contains có thể scan profile/User khi dữ liệu lớn; cần đo EXPLAIN với dữ liệu thực trước khi đề xuất search index. Không thêm index dự phòng hàng loạt.

## Frontend

`Fontend/src/features/customers/`:
- api/customerApi.js: riêng module, reuse commerceRequest/apiClient + refresh convention.
- pages/CustomersPage.jsx: thay placeholder; search/status form áp khi submit, pagination, table, loading/error/retry/empty; mobile ẩn các cột phụ và đưa contact dưới tên.
- pages/CustomerDetailPage.jsx: profile, summary cards, latest order, tabs orders/warranties/installments theo role.
- components/CustomerProfile, CustomerHistory, CustomerPagination, CustomerStatus, customerFormat.
- customers.css: scoped management styling, panel padding, scroll tables, wrap dài, responsive cards/fields.

Không chart. Chỉ fetch history của tab đang mở, page trên URL riêng từng tab, abort request cũ; detail không reload khi chỉ đổi tab/page. Quay lại list giữ keyword/status/page từ link đã mở. Liên kết Order/Warranty/Installment detail hiện có thay vì tạo flow quản lý trùng lặp.

## Validation

- Backend: `./backend/mvnw -q -f backend/pom.xml -Dmaven.test.skip=true package` PASS.
- Frontend: `npm --prefix Fontend run lint`, `npm --prefix Fontend run build` PASS.
- Không viết/chạy test suite. Kiểm tra HTTP thật với dev account hiện có, backend tạm 8081, tất cả seed flags OFF.
- ADMIN/MANAGER/STAFF read list/detail/order/warranty; CUSTOMER 403; anonymous 401; STAFF installment 403, ADMIN/MANAGER installment 200.
- Search fullName/username/email/phone, status từng enum, pagination size=1 ổn định, no-match search, escaped `%`, invalid size/page/offset/status/keyword, absent customer 404 PASS.
- Profile và summary list/detail giống nhau, aggregate đối chiếu SQL read-only; chỉ DELIVERED góp spend; latest order deterministic.
- Dataset hiện có 2 Customer, một người chưa có Order: 0 totals, null latest order, lịch sử rỗng PASS.
- Lịch sử ba loại được đối chiếu COUNT theo customer; paging từng record không duplicate. Warranty status/IMEI đối chiếu Warranty API hiện có.

Chưa xác minh trực quan browser desktop/mobile trong phiên do browser automation chưa khả dụng. Không có endpoint CRUD Customer; thay đổi account tiếp tục qua User Management. Snapshot lịch sử Order không đồng bộ địa chỉ Customer; Customer cũng không có trường createdAt riêng. Chưa có loyalty engine hoặc return/refund-adjusted spend.
