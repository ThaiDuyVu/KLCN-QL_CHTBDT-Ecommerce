# Kiểm toán thiết kế CSDL và đối chiếu nghiệp vụ đề cương

Ngày kiểm tra: **08/11/2026**. Branch `dev/vu`, HEAD `f298294`, có thay đổi chưa commit của người dùng; kết luận áp dụng cho **working tree đã đọc**, không chỉ HEAD. Mã băm các nguồn đã kiểm tra nằm trong bộ evidence kèm theo.

## 1. Kết luận chính

CSDL đã mô hình hóa được các nghiệp vụ chính trong đề cương. Có khóa ngoại, giới hạn tiền/số lượng, trạng thái, giá tại thời điểm bán, nguồn gốc serial và lịch sử đơn hàng. Tuy nhiên, **chưa nên bảo vệ bằng khẳng định “thiết kế đã hoàn chỉnh”**: có ba tình huống nghiêm trọng được tái hiện qua service/luồng nghiệp vụ thực tế trên PostgreSQL cô lập:

1. **Hàng có serial đã trả và nhập lại kho không bán lại được.** `UNIQUE(order_items.serial_id)` đang ràng buộc cả lịch sử thay vì chỉ lần cấp phát còn hiệu lực.
2. **Sửa biến thể có thể đổi danh tính hàng trên hóa đơn cũ.** Dòng đơn chỉ giữ ID và giá, nhưng tên/SKU lấy từ catalog hiện tại; service còn cho chuyển biến thể đã bán sang một sản phẩm khác.
3. **Hủy đơn đổi hàng để lại yêu cầu gốc ở trạng thái kết thúc `EXCHANGED`, không có đường xử lý tiếp khoản cấn trừ.** Đã trả hàng nhưng đơn thay thế bị hủy; hệ thống vẫn không cho hoàn tiền hoặc tạo lại yêu cầu cho số lượng đó.

Đây là lỗi khác với lỗi “yêu cầu lại hàng đã EXCHANGED” vừa sửa. Bản sửa chặn yêu cầu trùng vẫn đúng; vấn đề thứ ba là thiếu xử lý bù khi đơn thay thế thất bại.

**Chưa thấy bằng chứng dữ liệu local hiện đã hỏng**, thất thoát tiền thực tế hoặc khách hàng vượt quyền qua API. Những kịch bản dưới đây được tạo bằng fixture tổng hợp trong DB kiểm thử riêng. Thiếu ràng buộc DB được phân biệt với lỗi có thể đi qua service.

## 2. Phạm vi và phương pháp

Đề cương được đối chiếu từ `KLCN057_XayDungHeThongCuaHangMuaBanThietBiDienTu.pdf`; mã in trong tài liệu là `CNTT-KLCN054`. Phạm vi: sản phẩm/danh mục, khách hàng, đặt-hủy/bán/thanh toán/hóa đơn, giao hàng, đổi trả, lịch sử, khuyến mãi, bảo hành, phản hồi/đánh giá, chatbot và thống kê.

Giữ các quyết định đã xác nhận:

- Bảo hành chỉ áp dụng thiết bị có serial. Không coi thiếu bảo hành hàng không serial là lỗi.
- Quyền STAFF mở rộng phục vụ demo. Không tính lại thành lỗi thiết kế DB.
- VNPAY chờ nghiệm thu thực tế gần deploy. Lần này không gọi cổng thanh toán và không suy đoán nguyên nhân hết hạn.
- Không bắt buộc hệ thống tích điểm, hóa đơn điện tử/VAT, sổ cái kế toán hoặc thu nợ trả góp ngoài đề cương.

Đã đọc migration V1–V17, schema thực tế qua `pg_dump --schema-only`, code ghi/đọc dữ liệu và test liên quan. Skill `codebase-memory` hỗ trợ tìm cấu trúc; index ngày 20/10 đã cũ nên kết luận dựa vào **nguồn hiện tại và kiểm thử**, không dùng graph làm bằng chứng đầy đủ.

DB local chỉ được chạy `BEGIN READ ONLY` để đọc metadata và số lượng tổng hợp. Các thao tác sửa dữ liệu để thử lỗi chạy trong **PostgreSQL Testcontainers riêng**, không dùng DB nghiệp vụ local. Không sửa application code, migration hoặc schema. Không commit/push.

## 3. Hiện trạng được xác minh

| Nội dung | Kết quả trực tiếp |
|---|---|
| PostgreSQL / pgvector | 16.15 / 0.8.7 |
| Flyway | V1–V17 đều thành công |
| Bảng nghiệp vụ | 51; cộng `flyway_schema_history` thành 52 bảng public |
| Cột / FK / CHECK / chỉ mục nghiệp vụ | 333 / 82 / 92 / 189 |
| Catalog local | 17 sản phẩm, 38 biến thể, 80 serial |
| Đơn / yêu cầu đổi trả | 9 / 1 |
| Bảo hành / ticket / đánh giá | 6 / 5 / 0 |
| Hội thoại / tin nhắn | 1.174 / 3.802 |
| Knowledge | 11 PUBLISHED, 2 ARCHIVED; 18 chunk, đều có vector |

Kiểm tra chéo local đều trả **0 bản ghi vi phạm** đối với: tổng header đơn so với dòng; tổng hóa đơn so với đơn; chủ yêu cầu đổi trả; liên kết dòng trả và serial; chủ/serial bảo hành và ticket; chủ/sản phẩm đánh giá; chủ session/message/feedback; variant/kho của serial với dòng đơn; serial AVAILABLE còn gắn dòng đơn cũ; số lượng/reserved tồn kho so với serial; nhóm khách trùng số điện thoại; đơn thiếu kho.

Giới hạn: dữ liệu đánh giá hiện bằng 0 nên kiểm tra đánh giá là kiểm tra trên tập rỗng. Không thấy lỗi ở dữ liệu mẫu không chứng minh mọi luồng ghi đều đúng. Không đọc nội dung hội thoại hoặc thông tin cá nhân để đưa vào báo cáo.

## 4. Các lỗi đã tái hiện qua service

### DB-01 — Serial chỉ được bán một lần trong toàn bộ vòng đời

**Mức độ: HIGH, P1. Liên quan trực tiếp bán hàng + đổi trả + bảo hành.**

- Schema: [V1, dòng 666](../../backend/src/main/resources/db/migration/V1__create_full_database.sql#L666) đặt `UNIQUE(serial_id)` trên `order_items`.
- [ReturnServiceImpl, dòng 252](../../backend/src/main/java/com/example/backend/returns/service/ReturnServiceImpl.java#L252) đưa hàng RESTOCK từ SOLD về AVAILABLE, cộng lại tồn và VOIDED bảo hành cũ. Dòng bán cũ vẫn phải giữ để bảo toàn lịch sử.
- Thử: chạy luồng yêu cầu trả → duyệt → nhận RESTOCK → hoàn tiền; sau đó bán tại quầy chính serial đó qua `InStoreSaleService.sell`.
- **Actual:** lỗi `DataIntegrityViolationException`, constraint `uq_order_items_serial_id`. Toàn bộ lần bán mới rollback; serial vẫn AVAILABLE và không sinh đơn bán mới.
- Bằng chứng: probe **D01**.

**Gốc lỗi:** nhầm “mỗi thiết bị chỉ đang thuộc một giao dịch còn hiệu lực” với “mỗi thiết bị chỉ có duy nhất một dòng bán trong toàn bộ lịch sử”.

**Không thể chỉ xóa UNIQUE là xong:** `warranties.serial_id` cũng UNIQUE. [WarrantyProvisioningServiceImpl, dòng 36](../../backend/src/main/java/com/example/backend/warranty/service/WarrantyProvisioningServiceImpl.java#L36) tìm bảo hành cũ theo serial và từ chối nếu order item/customer khác, kể cả bảo hành đã VOIDED. Probe **D02** xác nhận service này từ chối một vòng bán mới. D02 là kiểm tra cục bộ phần bảo hành; chưa thể đến bước ấy trong luồng bán thật vì D01 chặn trước.

**Thiết kế cần review:** một serial có nhiều lần bán lịch sử; tách khái niệm đang cấp phát với lịch sử bán. Bảo hành gắn với lần bán/order item và khách tương ứng, giữ bảo hành VOIDED cũ. Chặn hai lần cấp phát đồng thời bằng trạng thái/khóa/ràng buộc phù hợp. PostgreSQL không thể dùng partial index có điều kiện JOIN trực tiếp sang `orders.status`; không đề xuất một index không thực thi được.

**Regression cần có:** bán A → trả RESTOCK → bán lại B → cấp bảo hành cho B; A vẫn xem được chứng từ cũ và không mở ticket cho bảo hành VOIDED; hai giao dịch không được cùng bán một serial.

### DB-02 — Danh tính hàng trên hóa đơn/lịch sử không bất biến

**Mức độ: HIGH, P1. Liên quan hóa đơn, lịch sử, thống kê sản phẩm/loại sản phẩm.**

`order_items` có snapshot giá vốn, giá bán, giảm giá nhưng **không có snapshot tên hàng/SKU/cấu hình/thời hạn bảo hành được cam kết**. [InvoiceServiceImpl, dòng 132](../../backend/src/main/java/com/example/backend/invoice/service/InvoiceServiceImpl.java#L132) JOIN catalog hiện tại để dựng tên/SKU; tên khách và chi nhánh cũng lấy hiện tại. Recipient/address của đơn đã được snapshot, đó là phần đúng.

[ProductVariantServiceImpl, dòng 171](../../backend/src/main/java/com/example/backend/product/service/ProductVariantServiceImpl.java#L171) cho thay `product_id`, SKU, RAM/storage của biến thể mà không chặn khi đã có giao dịch.

**Probe D03:** đọc hóa đơn đang chứa `Điện thoại X / DT-X`; gọi service cập nhật biến thể đã bán sang sản phẩm `Laptop KHAC`, SKU `LAPTOP-NEW`; đọc lại hóa đơn. **Actual:** cùng hóa đơn, cùng tổng tiền nhưng tên hàng/SKU đổi thành laptop mới.

**Tác động:** chứng từ/lịch sử có thể không còn mô tả món hàng đã giao. Báo cáo nhóm theo catalog hiện tại cũng có thể phân loại lại lịch sử. Không gọi đây là giá hóa đơn bị đổi: test xác nhận tổng tiền vẫn giữ nguyên.

**Hướng xử lý:** trước mắt chặn chuyển `product_id`/đổi định danh SKU của biến thể đã được tham chiếu. Khi review schema, quyết định snapshot tối thiểu cho chứng từ; hoặc cơ chế version/đóng băng master data. Không xóa FK catalog vì vẫn cần truy vết. Giá snapshot là đúng, không được thay bằng JOIN giá hiện tại.

Thời hạn bảo hành hiện được đọc từ variant khi giao hàng, xem [WarrantyProvisioningServiceImpl, dòng 47](../../backend/src/main/java/com/example/backend/warranty/service/WarrantyProvisioningServiceImpl.java#L47). Thiếu snapshot chính sách tại lúc mua là rủi ro bổ sung; chưa chạy kịch bản đổi chính sách giữa lúc đặt/giao nên không gộp thành lỗi runtime đã tái hiện.

### DB-03 — Đơn đổi bị hủy nhưng khoản cấn trừ vẫn mắc ở EXCHANGED

**Mức độ: HIGH, P1. Liên quan đổi trả + hủy đơn + xử lý tiền.**

Probe **D14** thực thi:

1. Trả hai sản phẩm, giá trị **400.000 đồng**, nhận RESTOCK.
2. Đổi sang món giá **1.000.000 đồng**. Service sinh đơn thay thế CONFIRMED, COD cần thu **600.000 đồng**; yêu cầu gốc EXCHANGED.
3. Quản lý hủy đơn thay thế qua `OrderService.status`.
4. Đọc lại yêu cầu gốc và thử tiếp các thao tác hợp lệ của hệ thống.

**Actual:** đơn thay thế CANCELLED, yêu cầu gốc vẫn EXCHANGED với khoản cấn trừ 400.000 đồng. Chuyển REFUNDED bị **409**; tạo lại yêu cầu cho số hàng đó cũng **409**. Không có trạng thái/luồng bù được thực thi cho trường hợp này.

Nguồn: [ExchangeOrderServiceImpl](../../backend/src/main/java/com/example/backend/order/service/ExchangeOrderServiceImpl.java) tạo đơn; [ReturnServiceImpl, dòng 216](../../backend/src/main/java/com/example/backend/returns/service/ReturnServiceImpl.java#L216) kết thúc EXCHANGED; [OrderServiceImpl, dòng 279](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L279) xử lý hủy chưa bù yêu cầu liên quan; `transitions` của return không có lối ra từ EXCHANGED.

**Gốc lỗi:** trạng thái “đã tạo đơn đổi” bị dùng làm kết thúc, chưa mô hình hóa thất bại/hủy của giao dịch thay thế và nghĩa vụ hoàn/cấn trừ còn lại. Có FK `exchange_order_id` không tự bảo đảm tính nhất quán trạng thái giữa hai quy trình.

**Hướng xử lý tối thiểu cần review:** chặn hủy thông thường đối với đơn thay thế cho tới khi có quy trình xử lý khoản credit; hoặc triển khai hành động bù có transaction và audit. Không tự mở lại số lượng gốc một cách đơn giản vì hàng đã được nhận về kho. Đây là lỗi dữ liệu/workflow nội bộ độc lập với việc VNPAY sandbox có hoạt động không.

### DB-04 — Quy tắc số điện thoại khách hàng không có một nơi bảo đảm thống nhất

**Mức độ: MEDIUM, P2. Liên quan quản lý khách hàng và nhận diện khách tại quầy.**

SĐT khách có tài khoản nằm ở `users.phone`; khách vãng lai ở `customers.phone`. Unique index chỉ bảo vệ cột thứ hai. `CustomerManagementServiceImpl` gọi `phoneTaken` trên cả hai bảng, nhưng [UserServiceImpl, dòng 47](../../backend/src/main/java/com/example/backend/auth/service/UserServiceImpl.java#L47) cập nhật `users.phone` không dùng cùng quy tắc.

**D15:** dùng service cập nhật tài khoản khách B thành số đang thuộc khách A. Cập nhật thành công; truy vấn thu được **hai customer cùng số**. D11 xác nhận DB cũng chấp nhận. Đây là service quản trị có quyền cập nhật, không phải chứng minh khách tự vượt quyền.

**Hướng xử lý:** chốt số điện thoại có phải khóa nhận diện khách hay cho phép dùng chung. Nếu phải duy nhất, chuẩn hóa `0…`/`+84…` và thống nhất nơi lưu/luồng ghi; chống race ở tầng DB thích hợp. Nếu được dùng chung thì không dùng phone làm định danh duy nhất tại quầy và phải sửa quy tắc `phoneTaken`/tài liệu cho nhất quán.

### DB-05 — Thông số kỹ thuật cho phép cùng khóa có giá trị mâu thuẫn

**Mức độ: MEDIUM, P2. Liên quan chi tiết sản phẩm, tìm kiếm và chatbot.**

`specifications(product_id, spec_key, spec_value)` không quy định khóa chuẩn/đơn vị/tính duy nhất. [SpecificationServiceImpl, dòng 90](../../backend/src/main/java/com/example/backend/product/service/SpecificationServiceImpl.java#L90) giữ nguyên dữ liệu.

**D16:** gọi service thêm hai dòng CPU cho cùng sản phẩm: `Intel i5` và `AMD Ryzen 7`; cả hai thành công. Bộ lọc CPU trong `ProductAdvancedSearchService` dùng EXISTS theo khóa CPU, nên với dữ liệu này hai tiêu chí khác nhau đều có thể tìm ra cùng sản phẩm. D16 xác nhận service ghi và đọc được hai dòng; chưa chạy một phiên chatbot cho dữ liệu này.

**Cần phân biệt:** thông số nhiều giá trị như cổng kết nối là hợp lệ. Không được thêm UNIQUE(product_id, spec_key) cho mọi thuộc tính mà chưa phân loại thuộc tính đơn trị/đa trị.

Ngoài ra RAM/storage là VARCHAR; truy vấn phải parse đơn vị. Ví dụ chuỗi `16` không kèm GB/TB không thỏa bộ lọc, trong khi form/service chỉ giới hạn độ dài. Cần validation/đơn vị chuẩn; chưa cần một hệ EAV phức tạp chỉ để sửa điều này. CPU hiện nằm ở mức product: nếu cho phép cùng product có các bản CPU khác nhau, cần quy ước tách product hoặc chuyển thuộc tính thích hợp về variant.

## 5. Những lỗ hổng ràng buộc DB — chưa chứng minh là lỗ hổng API

Các probe SQL dưới đây chạy trên fixture cô lập. FK hiện tại chỉ chứng minh từng ID tồn tại, **không chứng minh các ID thuộc cùng một nghiệp vụ**.

| Probe | DB chấp nhận | Điều còn thiếu | Đánh giá |
|---|---|---|---|
| D04 | Bảo hành của dòng mua A nhưng customer B | Warranty.customer = Order.customer; serial khớp dòng | MEDIUM, cần hardening; API chưa bị chứng minh vượt quyền |
| D05 | Dòng hàng gắn serial của variant khác | Serial.variant = OrderItem.variant | MEDIUM |
| D06 | Message của customer B trong session A | Cặp session/customer phải nhất quán | MEDIUM; repository hiện có ownership check |
| D07 | Header đơn tổng 1 đồng nhưng dòng hàng tổng 10.400.000 đồng | Tổng header bằng tổng dòng | MEDIUM; CHECK V15 mới kiểm công thức trong cùng header |
| D08 | Tăng quantity mà không thêm serial | Tổng inventory và tập serial phải khớp | MEDIUM; service hiện khóa/cập nhật cùng transaction |
| D09 | RETURN có status EXCHANGED, không có đơn đổi | Ràng buộc tổ hợp type/status/exchange_order_id | MEDIUM; không đồng nghĩa service cho chuyển như vậy |
| D10 | Dòng trả dùng order_item khác nhưng serial vẫn giữ dòng cũ | ReturnItem khớp item/serial và đơn gốc | MEDIUM |
| D12 | Danh mục làm cha chính nó | Không có chu kỳ cây | LOW ở DB; service đã có kiểm tra parent/visited |

Các cột dư thừa như `return_requests.customer_id`, `warranties.customer_id`, `warranty_tickets.serial_id/customer_id`, `reviews.product_id`, `chat_history.customer_id` có thể giúp truy vấn/ownership nhanh, nhưng tạo thêm **invariant phải bảo vệ**. Không kết luận cứ dư thừa là phải xóa. Cân nhắc composite FK/unique tương ứng cho liên kết phù hợp, hoặc bỏ cột dẫn xuất khi lợi ích thấp; trigger chỉ dùng khi thật sự cần. Tổng xuyên nhiều bảng cần service transaction và kiểm tra đối soát, không thể giải quyết bằng CHECK có subquery tùy ý.

Không đề xuất dùng trigger cho mọi business rule. Ví dụ quyền sở hữu của người gọi vẫn phải kiểm tra bằng authentication/service; FK không thay thế authorization.

## 6. Điểm thiết kế còn yếu hoặc cần chốt quy tắc

### 6.1 Thanh toán: schema nhiều giao dịch nhưng code có chỗ giả định một

DB cho một order có nhiều payment; comment V15 cũng mô tả nhiều lần thử. Nhưng `OrderServiceImpl` lấy `paymentRows.get(0)` để dựng response, `InvoiceServiceImpl` dùng `findFirst()` không có quy tắc chọn, VNPAY có nhánh yêu cầu chính xác một payment. Đây là **mâu thuẫn contract thiết kế đã thấy trong code**; chưa chạy fixture nhiều payment nên tác động từng endpoint ghi là chưa kiểm chứng.

Phải chọn một trong hai: một payment aggregate/đơn và bảng attempt riêng; hoặc nhiều giao dịch thực sự với quy tắc tổng hợp/chọn và khóa rõ ràng. Không thêm CHECK `payments.amount = orders.total_amount`: đơn đổi hợp lệ có payment chỉ thu chênh lệch, không bằng tổng giá hàng.

Hoàn tiền một phần hiện có giá trị/status trong return request, trong khi payment gốc có thể vẫn PAID. Điều đó **không tự nó sai**: payment vẫn phản ánh khoản đã thu ban đầu. Phần thiếu để đối soát tốt hơn là tham chiếu/xác nhận lần hoàn, người thực hiện, thời điểm và số tiền thực hoàn; hiện không thể lấy status làm bằng chứng ngân hàng đã hoàn. Đây là giới hạn đã biết, không phụ thuộc chờ bật VNPAY.

### 6.2 Giá vốn và báo cáo lợi nhuận chưa thể gọi là giá vốn thực nhập

Phiếu nhập có `unit_cost`; V16 đã nối được serial về dòng nhập. Nhưng ba luồng checkout/POS/exchange snapshot `variant.cost_price`, không lấy cost của serial/lô nhập được xuất. Confirm phiếu nhập cũng không tự thiết lập chính sách giá vốn từ `unit_cost`.

**Cần chốt nghiệp vụ:** đây là giá vốn tiêu chuẩn do quản lý cấu hình, bình quân hay đích danh? Với cách hiện tại, báo cáo là biên lợi nhuận dựa trên giá vốn cấu hình, chưa đủ bằng chứng gọi là lợi nhuận thực tế theo lô. Đề cương không bắt buộc FIFO hay kế toán kho nên không tự coi thiếu FIFO là lỗi chức năng bắt buộc. Chưa chạy fixture nhập hai lô khác giá; đây là quyết định thiết kế chưa làm rõ.

### 6.3 Thiếu lịch sử chuyển trạng thái cho đổi trả và bảo hành

Đơn hàng đã có `order_status_history`: tốt. Đổi trả chỉ có một employee hiện tại, `processed_at`, status; qua nhiều bước employee bị ghi đè. Warranty ticket có người phụ trách, ngày tạo/kết thúc và ghi chú nhưng không có lịch sử mỗi người chuyển trạng thái. Không trả lời chắc chắn được “ai duyệt, ai nhận, lúc nào, trước đó là gì?” chỉ từ các bảng này.

Đây là thiếu khả năng audit mức MEDIUM/P2, không phải chứng minh mọi nhân viên được thao tác trái phép. Có thể trình bày rõ giới hạn hoặc thiết kế nhật ký sau review; không tự tạo thêm nhiều bảng audit trong lần kiểm tra này.

### 6.4 Knowledge vector thiếu metadata model

`product_semantic_index` có `embedding_model` và `content_hash`; `knowledge_chunks` chỉ có vector/content. Search knowledge lọc số chiều và norm nhưng không biết model đã sinh vector. Indexer tái dùng embedding nếu text không đổi; CLI có `--force` sau khi đổi model.

Nếu đổi model cùng số chiều mà quên force, có thể trộn hai không gian embedding mà DB không phát hiện. **Thiếu metadata đã xác minh bằng schema/code; lỗi chất lượng thực tế chưa được tái hiện** vì không đổi model/dữ liệu trong đợt audit. Nên lưu model/version/hash hoặc một index generation có metadata và kiểm tra khi chạy. Không cần vector index HNSW để sửa tính đúng; với 18 chunk hiện tại, chưa có bằng chứng cần tối ưu ANN.

### 6.5 Lưu ASSISTANT snapshot dạng JSON trong cột TEXT

`chat_history.message` lưu text cho USER và JSON envelope cho ASSISTANT qua `StoredAssistantMessage`. Cách này đang hỗ trợ khôi phục snapshot và decode plain text cũ, **không phải mất trí nhớ sau restart**.

Đánh đổi: DB không kiểm được shape, khó query/version envelope bằng SQL; generic client SELECT message có thể thấy JSON thay vì câu trả lời. Nếu mở rộng nên review `message_text` + metadata JSONB/schema version. Chưa cần sửa chỉ để đạt thêm chuẩn hóa; không phải blocker demo.

### 6.6 Tài liệu Knowledge đi kèm chưa đồng bộ nghiệp vụ mới

File `chatbot-service/docs/knowledge/BO_TAI_LIEU_KIEN_THUC_CHINH_THUC.md` ghi phiên bản đã duyệt 04/11; KT-10, dòng 255–260, vẫn nói hệ thống chưa triển khai đổi trả/hoàn tiền và web/app chưa có yêu cầu trực tuyến. Nội dung này không còn khớp code đổi trả hiện tại. Đây là mâu thuẫn tài liệu nguồn đã xác minh, không phải lỗi cấu trúc FK hay suy đoán lỗi model. Chưa gọi chatbot để đo tác động và chưa so sánh nội dung từng document đang PUBLISHED với file. Không tự sửa chính sách/seed/index trong đợt audit; cần chủ nghiệp vụ review bản mới trước khi nạp.

## 7. Trường/bảng thừa, thiếu và dữ liệu lặp: nên giải thích thế nào?

| Thành phần | Nhận xét | Cách bảo vệ / hướng xử lý |
|---|---|---|
| Thiếu danh tính hàng snapshot trên dòng chứng từ | Đã gây lỗi D03 | Ưu tiên bảo vệ lịch sử trước khi thêm chức năng mới |
| Thiếu khái niệm nhiều vòng bán/bảo hành của một serial | Đã gây lỗi D01, D02 | Sửa cardinality và current allocation sau review |
| Thiếu xử lý nghĩa vụ credit khi đơn đổi hủy | Đã gây lỗi D14 | Chốt workflow bù, không chỉ thêm cột tùy ý |
| `subtotal`, `discount_amount`, `total_amount`, giá dòng, tổng hóa đơn | Lặp có chủ đích | Snapshot chứng từ/cache tính toán là hợp lý; cần đối soát xuyên bảng |
| `inventory.quantity` và số serial | Hai biểu diễn của cùng tồn kho tracked | Hữu ích cho hàng NONE và đọc nhanh; khóa + cập nhật atomic + reconciliation |
| `users.display_name` và `customers/employees.full_name` | Ý nghĩa dễ chồng lấn | Chốt tên hiển thị khác tên hồ sơ hay phải đồng bộ; không xóa mù quáng |
| `customers.address` và `customer_addresses` | Không nhất thiết trùng | Địa chỉ liên hệ và sổ giao hàng là hai mục đích; đơn giữ địa chỉ giao snapshot |
| `users.phone` và `customers.phone` | Phân nhánh tài khoản/vãng lai, đang bất nhất uniqueness | D04 cần quy tắc chung |
| `customers.loyalty_point` | Có trường, chưa thấy flow tích/tiêu điểm trong nguồn kiểm tra | Ngoài đề cương: ghi dự phòng, không giới thiệu là chức năng đã hoàn thiện |
| `chatbot_qna`, `chatbot_scripts` | Hiện 0 dòng, không thấy tham gia pipeline RAG hiện tại trong phạm vi nguồn đã tìm | Ghi là cấu trúc dự phòng/thiết kế cũ; không tuyên bố chatbot đang chạy từ hai bảng này |
| `chat_sessions.employee_id`, `chat_history.employee_id` | Dự phòng người hỗ trợ | Không có flow chuyển sang người thật được chứng minh trong lần này |
| `user_roles` có PK hai cột nhưng UNIQUE(user_id) | Hiện tại mỗi user tối đa một role | Đừng vẽ/giải thích là đang cho mỗi user nhiều role; join table có thể giữ để quản lý phân quyền |
| Serial unique toàn hệ thống | Giả định đơn giản hóa đã được ghi comment V15 | Phải nói rõ hệ thống yêu cầu mã serial không trùng; không khẳng định mọi hãng đảm bảo duy nhất toàn cầu |

**Không thiếu do đề cương:** không có ledger tích điểm/voucher phức tạp, VAT/e-invoice, trả góp theo từng kỳ, bảo hành hàng không serial. Không thêm những thứ này để làm DB trông lớn hơn.

### Chỉ mục thừa đã thấy trực tiếp

Ba cặp cùng tập cột và cùng định nghĩa khóa, trong đó UNIQUE đã cung cấp B-tree:

- `idx_customers_user_id` / `uq_customers_user_id`.
- `idx_employees_user_id` / `uq_employees_user_id`.
- `idx_return_requests_exchange_order_id` / `uq_return_requests_exchange_order`.

Mức LOW, chỉ tăng chi phí ghi/lưu. Không ảnh hưởng đúng-sai nghiệp vụ; chưa xóa index. Không kết luận 189 index đều thừa. Có thể review thêm index tổng hợp cho truy vấn history `(session_id, created_at, history_id)` sau khi đo; hiện chưa benchmark độ trễ nên không gọi thiếu index là lỗi hiệu năng đã xác minh.

## 8. Kiểm thử và evidence

Evidence được giữ ở [thư mục kiểm toán](kiem-toan-csdl-2026-11-08/README.md), gồm test source tổng hợp, SQL chỉ đọc, kết quả đã lược bỏ payload, metadata và hash nguồn. Raw log local nằm ở `.local/db-audit-20261108/`, không đưa vào Git vì có output hạ tầng test.

| Chạy mới | Kết quả | Ý nghĩa |
|---|---|---|
| `DatabaseDesignAuditTest#D*` | 16 chạy, 0 failure/error/skip | Probe xác nhận hành vi quan sát, bao gồm tái hiện lỗi; **không phải 16 nghiệp vụ đúng** |
| `ReturnIntegrationTest` | 7 chạy, 0 failure/error/skip | Các flow cũ vẫn đạt nhưng chưa bao phủ ba lỗi trên |
| `SchemaConstraintsIntegrationTest` | 5 chạy, 0 failure/error/skip | CHECK trạng thái/công thức, phone vãng lai, cart unique hiện có hoạt động |
| `ReportCategoryIntegrationTest` | 3 chạy, 0 failure/error/skip | Các tình huống báo cáo danh mục có sẵn đạt |

Tổng lượt test phân biệt ở các lần chạy hoàn tất: **31** (16 probe + 15 test có sẵn). Không chạy full backend/frontend/Python/mobile và không gọi model/cổng thanh toán trong đợt này.

Một lần chạy gộp có exit **1**: subclass test chẩn đoán và `ReturnIntegrationTest` dùng cùng Spring context cache nhưng lifecycle Testcontainers kế thừa làm container trước bị dừng. Sau khi 5+3+16 test đã xong, lớp kế tiếp không kết nối được; đã dừng riêng JVM test, giữ log lỗi và chạy các nhóm riêng thành công. Đây là lỗi tổ chức harness chẩn đoán, **không phải bằng chứng backend production mất kết nối**. Không che lần chạy lỗi và không sửa application để làm test đạt.

## 9. Thứ tự khắc phục đề nghị

| Ưu tiên | Việc cần review | Mục tiêu nghiệm thu |
|---|---|---|
| P1 | DB-01: vòng đời serial, quan hệ order item/warranty | Bán → trả RESTOCK → bán lại hợp lệ, lịch sử/ownership bảo hành đúng |
| P1 | DB-02: bất biến danh tính chứng từ | Sửa catalog không viết lại món hàng của hóa đơn cũ |
| P1 | DB-03: hủy/thất bại đơn thay thế | Không để credit và hàng đã trả mắc ở trạng thái kết thúc |
| P2 | Phone chung, thông số đơn trị/đa trị, contract nhiều payment | Mọi luồng ghi dùng cùng quy tắc; dữ liệu không mâu thuẫn |
| P2 | Hardening liên kết, nhật ký đổi trả/bảo hành, model metadata Knowledge | Chặn sai quan hệ; truy vết được quy trình và nguồn vector |
| P3 | Index trùng, tài liệu hóa phần dự phòng, đo query history | Giảm dư thừa có bằng chứng, không đổi schema tùy tiện |

Với P1, cần review thiết kế trước rồi mới tạo migration mới; không sửa migration cũ đã áp dụng. Có thể làm các guard tạm thời trong code để tránh phát sinh workflow lỗi, nhưng phải ghi rõ giới hạn thay vì tuyên bố đã hỗ trợ bán lại/đổi trả đầy đủ.

## 10. Những câu hỏi hội đồng có thể hỏi và câu trả lời trung thực

1. **Một serial bán rồi khách trả, cửa hàng có bán lại được không?** Hiện luồng nhập lại kho có, nhưng ràng buộc một serial/một order item chặn bán lại. Đã có D01; đây là điểm phải sửa hoặc giới hạn minh bạch, không thể giải thích lướt qua.
2. **Sản phẩm đổi tên thì hóa đơn cũ ra sao?** Giá đã snapshot, danh tính chưa đầy đủ. D03 cho thấy biến thể còn chuyển được sang sản phẩm khác. Cần đóng băng định danh hoặc snapshot chứng từ.
3. **FK đầy đủ thì sao vẫn có thể sai chủ bảo hành?** FK đơn kiểm tra từng record tồn tại, không kiểm mối quan hệ cùng đơn/cùng chủ. Service đang kiểm một phần; DB cần bổ sung invariant phù hợp, và kiểm thử chéo không được thiếu.
4. **Sao lưu tổng tiền khi có thể SUM chi tiết?** Để giữ chứng từ và đọc nhanh. Đây là phi chuẩn hóa có chủ đích; CHECK trong header chưa đủ chứng minh tổng dòng khớp header, phải có transaction/tính toán và đối soát.
5. **Lợi nhuận là giá nhập nào?** Hiện dùng snapshot giá vốn cấu hình của variant, chưa chứng minh giá vốn thực nhập từng thiết bị. Phải trình bày đúng định nghĩa, không gọi là FIFO/bình quân khi chưa có.
6. **Đơn đổi hủy thì tiền cấn trừ đi đâu?** Hiện thiếu workflow bù, đã tái hiện D14. Đây là lỗi quy trình cần giải quyết, không thể đẩy sang lỗi VNPAY.
7. **51 bảng có quá nhiều không?** Đếm bảng không đánh giá được chất lượng. Có các nhóm thiết bị, nhập kho, bán hàng, hậu mãi, phân quyền, hội thoại/RAG; phải giải thích bảng đang dùng và phần dự phòng. Những lỗi quan trọng nằm ở invariant/vòng đời, không ở số lượng bảng.

Kết luận nghiệm thu DB: nền tảng quan hệ đủ phục vụ đa số flow demo, dữ liệu local qua các kiểm tra hiện chưa thấy lệch; **chưa đạt mức toàn vẹn nghiệp vụ đầy đủ** do ba lỗi P1 đã tái hiện. Báo cáo này là một audit DB sâu hơn lần chấm chức năng trước; không nên dùng điểm chức năng trước đó làm bằng chứng mọi ràng buộc DB đã đúng.
