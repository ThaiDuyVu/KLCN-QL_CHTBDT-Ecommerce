# Reporting dashboard

Route frontend: `/reports`. Chỉ ADMIN/MANAGER; navigation và RequireRole đồng bộ với `@RequireAnyAuthority` trên cả 8 API. Không thêm permission, schema/migration hay sửa nghiệp vụ bán hàng. Không viết test mới.

## API và giới hạn

Tất cả GET `/api/reports/...` nhận `from`, `to` (`YYYY-MM-DD`, inclusive) và `warehouseId` optional.

| Endpoint | Kết quả | Giới hạn |
| --- | --- | --- |
| `/summary` | Revenue, grossProfit, totalDiscount, totalOrders, completedOrders, cancelledOrders, averageOrderValue, filter đã resolve và timezone | Một DTO nhỏ, 2 aggregate queries |
| `/revenue` | `granularity` DAY/MONTH và `points` | ≤90 ngày: daily; >90 ngày: monthly, tối đa 13 tháng |
| `/order-status` | Số đơn theo status | Tối đa 20 nhóm |
| `/top-products` | productId/name, quantity, revenue | `limit=5`, range 1–20 |
| `/warehouses` | warehouseId/name, count, amount | Top 100 chi nhánh theo doanh thu |
| `/payments` | paymentMethod/status, count, amount | Tối đa 100 nhóm |
| `/goods-receipts` | count, totalAmount, warehouses | Tổng toàn phạm vi; nhóm top 100 chi nhánh |
| `/low-stock` | Inventory + SKU/product/warehouse, quantity/reserved/available | `limit=20`, range 1–100; `threshold=5`, range 0–1000 |

Mặc định `to` là ngày hiện tại, `from = to - 29 ngày`. Khoảng tối đa 366 ngày inclusive. Sai ngày/limit/threshold: 400; warehouse không tồn tại: 404. Không tự truy vấn toàn lịch sử.

Ngày dùng `ZoneId.systemDefault()` / `LocalDate.now()` theo convention backend hiện có; runtime kiểm tra là Asia/Ho_Chi_Minh. Query dùng khoảng nửa mở từ đầu ngày from đến đầu ngày sau to, có ràng buộc timezone giống khi GROUP BY. Không cast column order_date trong WHERE để giữ khả năng dùng index ngày. Không áp một timezone mới cho hệ thống.

## KPI và nguồn dữ liệu

- Revenue: SUM(Order.totalAmount) chỉ DELIVERED, có shippingFee đã snapshot.
- Gross profit: SUM((OrderItem.finalUnitPrice - costPrice) * quantity) của DELIVERED. Đây là lợi nhuận gộp, không trừ vận hành/phí ship.
- Total discount: SUM(Order.discountAmount) chỉ DELIVERED.
- Total orders: COUNT mọi status trong phạm vi; completed = DELIVERED, cancelled = CANCELLED.
- Average order value: AVG(totalAmount) chỉ DELIVERED; trả 0 khi không có đơn.
- Top products: SUM(quantity), xếp DESC và productId tie-breaker; revenue là SUM(finalUnitPrice * quantity), không bao gồm phí ship. Gộp các variants của cùng Product.
- Warehouse revenue: SUM(totalAmount), COUNT DELIVERED theo warehouse của Order.
- Payments: GROUP BY method/status, lọc theo **orderDate của Order**, không dùng paymentDate làm cohort và không suy diễn PAID là revenue.
- Goods receipts: COUNT/SUM(totalAmount) CONFIRMED, lọc receiptDate và warehouse của receipt.
- Low stock: snapshot **hiện tại** của những inventory row đã tồn tại, available = quantity - reservedQuantity <= threshold; không áp from/to lên stock. Ngày vẫn được validate nhưng không giả lập tồn kho lịch sử. Không invent reorderThreshold, không tạo dòng tồn cho SKU chưa từng nhập kho.

Order chưa có deliveredAt. Thống kê doanh thu theo **ngày đặt đơn**, không phải ngày giao thành công. Thay đổi status một Order cũ sang DELIVERED sẽ làm số liệu ngày đặt đơn đó thay đổi. Payment lifecycle COD/VNPAY/INSTALLMENT được giữ nguyên; chưa có net revenue/return/refund reporting.

## Truy vấn và an toàn

`report/repository/ReportRepository` dùng NamedParameterJdbcTemplate, SELECT column projection trực tiếp sang DTO. Không entity hydration, findAll, Java stream aggregate, vòng lặp query từng product/warehouse, row lock, SELECT FOR UPDATE hay mutation.

- Summary dùng 2 aggregate queries riêng: Order tổng và OrderItem profit. Tránh fan-out khiến tổng Order bị nhân theo số item.
- Revenue GROUP BY date_trunc tại PostgreSQL; Java chỉ điền bucket 0 bị thiếu, không đọc từng Order.
- Top product aggregate qua orders → order_items → product_variants → products, ORDER BY SUM(quantity) DESC và LIMIT ở DB.
- Warehouse/status/payment dùng một GROUP BY query mỗi endpoint.
- Goods receipt dùng 2 query: tổng và group theo warehouse. Tổng không bị cắt bởi giới hạn nhóm.
- Low stock filter/sort/LIMIT ngay tại DB, không trả toàn inventory để lọc ở Java.
- Optional warehouse predicate chỉ được thêm khi filter có warehouse, tránh OR với nullable parameter. Tất cả input dùng bind parameters; granularity là literal nội bộ được quyết định sau validation.
- Service transaction READ ONLY, REPEATABLE READ, timeout 10s để các aggregate trong một response có cùng snapshot. JDBC query timeout 5s, riêng template report, không sửa template dùng bởi flow nghiệp vụ.
- Endpoint độc lập không đảm bảo snapshot chung giữa tất cả widget; thao tác bán hàng đang diễn ra có thể khiến hai widget chênh nhẹ giữa thời điểm đọc. Không dùng lock để ép dashboard đồng bộ.
- Không cam kết zero impact với dataset lớn trên cùng database/pool; cần đo lại tải thực khi volume tăng. Query có deadline thay vì giữ kết nối vô hạn.

## Frontend

`Fontend/src/features/reports/` có API client riêng dùng commerceRequest/apiClient hiện có, page + components + CSS. Route lazy-loaded; Recharts nằm trong chunk report, không tải vào luồng mua hàng thông thường.

Đúng 3 chart: line doanh thu theo thời gian; horizontal bar top 5 sản phẩm; bar doanh thu chi nhánh. ResponsiveContainer, tooltip/format VND, không animation, accessibilityLayer, bảng số liệu mở rộng cho chart. Tham khảo API chính thức: https://recharts.github.io/api/ResponsiveContainer/.

Summary cards, order status cards, payment table, goods receipt totals/table, low stock table có loading/error/retry/empty states độc lập. Form chỉ áp filter khi bấm Áp dụng; queue tối đa 3 request báo cáo đồng thời, abort request/queue cũ, không polling hay focus-refetch. Chi nhánh picker tái sử dụng API warehouse page size=100 hiện có; chưa có search/paging picker cho hệ thống >100 chi nhánh.

## Xác minh trên dữ liệu hiện có

Backend `./backend/mvnw -q -f backend/pom.xml -Dmaven.test.skip=true package`; frontend lint + production build. Không viết/chạy test suite.

API trên backend dev riêng port 8081, tất cả seed flags OFF, dùng DB hiện có để READ báo cáo. Auth fixture được dùng để kiểm tra:
- ADMIN/MANAGER 200, STAFF/CUSTOMER 403 trên toàn bộ 8 endpoint; anonymous 401.
- Dates sai/không parse được, UUID sai, range quá dài, limit/threshold ngoài biên bị reject; warehouse không tồn tại 404.
- Aggregate được đối chiếu SQL read-only cho toàn bộ kho và từng kho: 2 DELIVERED, revenue 34.980.000 VND, grossProfit 5.380.000 VND ở thời điểm kiểm tra.
- Empty period trả zero summary/AOV, series zero buckets và empty top/warehouse/payment/receipt; low stock vẫn là snapshot hiện tại.
- 90-day daily và 366-day monthly không vượt giới hạn điểm.

EXPLAIN (ANALYZE, BUFFERS) ba query được lưu trong `reports-explain.txt`. Dataset nhỏ (~12 Orders) nên PostgreSQL chọn Seq Scan orders; top products dùng Bitmap Index Scan idx_order_items_order_id, warehouse join dùng warehouses_pkey. Các index order_date/status/warehouse_id hiện có đã được xác nhận trong pg_indexes. Không force index, không thêm index/migration dự phòng khi chưa có bằng chứng từ tải thực. Thời gian đo local: revenue ~0.20ms, top ~0.37ms, warehouse ~0.17ms (không đại diện dataset production).

Không có browser automation khả dụng trong phiên nên chưa xác minh trực quan desktop/mobile trên trình duyệt; responsive đã có CSS và build pass. Cần kiểm tra nhìn thực tế khi mở /reports bằng ADMIN/MANAGER.
