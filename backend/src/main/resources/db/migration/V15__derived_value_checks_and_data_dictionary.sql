-- =========================================================
-- V15 – Derived-value integrity and a data dictionary
-- ---------------------------------------------------------
-- 1. Stored totals are intentional snapshots (a document must not change
--    when prices or promotions change later), but until now only the
--    application kept them consistent. These CHECKs make the database
--    enforce the same formulas the services use.
-- 2. The remaining free-text status columns get the same CHECK treatment
--    as V10 (values match what the backend and chatbot-service write).
-- 3. COMMENT ON for every table and the non-obvious columns, so the
--    schema documents itself (psql \d+, DBeaver, pg_dump).
--
-- Data only gains constraints; no column is dropped or retyped. Fails and
-- rolls back as a whole if existing rows violate a rule.
-- =========================================================

-- 1. Derived values ------------------------------------------------------
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_total_formula
        CHECK (total_amount = subtotal - discount_amount + shipping_fee);

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_final_unit_price_formula
        CHECK (final_unit_price = unit_price - discount_amount);

-- A counter sale always happens at a branch; online orders may predate V4.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_in_store_branch
        CHECK (sales_channel <> 'IN_STORE' OR warehouse_id IS NOT NULL);

ALTER TABLE installment_payments
    ADD CONSTRAINT ck_installment_down_payment_not_exceed_total
        CHECK (down_payment <= total_amount);

ALTER TABLE installment_payments
    ADD CONSTRAINT ck_installment_remaining_formula
        CHECK (remaining_amount = total_amount - down_payment);

ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_exchange_difference_type
        CHECK (exchange_difference IS NULL OR request_type = 'EXCHANGE');

-- A closed ticket records when it was closed.
ALTER TABLE warranty_tickets
    ADD CONSTRAINT ck_warranty_tickets_closed_resolved_at
        CHECK (status NOT IN ('COMPLETED', 'REJECTED') OR resolved_at IS NOT NULL);

-- 2. Remaining status columns -------------------------------------------
-- (carts.status is dropped in V17: a customer has exactly one cart.)
ALTER TABLE chat_sessions
    ADD CONSTRAINT ck_chat_sessions_status CHECK (status IN ('ACTIVE', 'CLOSED'));

ALTER TABLE chat_history
    ADD CONSTRAINT ck_chat_history_sender_type CHECK (sender_type IN ('USER', 'ASSISTANT'));

ALTER TABLE knowledge_documents
    ADD CONSTRAINT ck_knowledge_documents_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

ALTER TABLE chatbot_qna
    ADD CONSTRAINT ck_chatbot_qna_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE chatbot_scripts
    ADD CONSTRAINT ck_chatbot_scripts_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- 3. Data dictionary -----------------------------------------------------
-- Accounts and access
COMMENT ON TABLE users IS 'Tài khoản đăng nhập (nhân viên và khách hàng). Mật khẩu lưu dạng băm BCrypt.';
COMMENT ON TABLE roles IS 'Vai trò / chức danh (ADMIN, MANAGER, SALES_STAFF, ...).';
COMMENT ON TABLE permissions IS 'Quyền thao tác chi tiết.';
COMMENT ON TABLE user_roles IS 'Gán vai trò cho tài khoản. Cấu trúc nhiều-nhiều để mở rộng; nghiệp vụ hiện tại giới hạn mỗi tài khoản một vai trò (uq_user_roles_user_id).';
COMMENT ON TABLE role_permissions IS 'Gán quyền cho vai trò (nhiều-nhiều).';
COMMENT ON TABLE refresh_tokens IS 'Refresh token JWT; chỉ lưu SHA-256 của token, xoay vòng qua replaced_by_token_id.';
COMMENT ON TABLE employees IS 'Hồ sơ nhân viên, liên kết 1-1 với users.';
COMMENT ON TABLE customers IS 'Hồ sơ khách hàng. Có tài khoản (user_id) hoặc là khách vãng lai tại quầy (chỉ có phone).';
COMMENT ON TABLE customer_addresses IS 'Sổ địa chỉ giao hàng của khách; tối đa một địa chỉ mặc định.';

COMMENT ON COLUMN users.status IS 'ACTIVE | INACTIVE | LOCKED.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 (hex) của refresh token; token gốc không được lưu.';
COMMENT ON COLUMN refresh_tokens.replaced_by_token_id IS 'Token mới thay thế khi xoay vòng; dùng để phát hiện token bị dùng lại.';
COMMENT ON COLUMN employees.warehouse_id IS 'Chi nhánh làm việc; NULL = không gắn chi nhánh (quản trị, quản lý toàn cửa hàng) và xem được mọi chi nhánh.';
COMMENT ON COLUMN employees.position IS 'Chức danh hiển thị; quyền thực tế lấy từ user_roles.';
COMMENT ON COLUMN customers.user_id IS 'Tài khoản đăng nhập; NULL với khách vãng lai.';
COMMENT ON COLUMN customers.phone IS 'SĐT nhận diện khách vãng lai (duy nhất). Khách có tài khoản dùng users.phone.';
COMMENT ON COLUMN customers.address IS 'Địa chỉ liên hệ trên hồ sơ (do nhân viên quản lý). Địa chỉ giao hàng nằm ở customer_addresses.';
COMMENT ON COLUMN customers.loyalty_point IS 'Điểm tích lũy, không âm.';

-- Catalogue
COMMENT ON TABLE categories IS 'Danh mục sản phẩm dạng cây (parent_id); lọc theo danh mục cha bao gồm mọi danh mục con.';
COMMENT ON TABLE brands IS 'Thương hiệu.';
COMMENT ON TABLE products IS 'Sản phẩm (mức mẫu mã); giá và tồn kho nằm ở biến thể.';
COMMENT ON TABLE product_variants IS 'Biến thể bán được (SKU): màu, dung lượng, RAM, giá, cách theo dõi thiết bị, thời hạn bảo hành.';
COMMENT ON TABLE product_images IS 'Ảnh sản phẩm; tối đa một ảnh chính.';
COMMENT ON TABLE specifications IS 'Thông số kỹ thuật dạng khóa - giá trị.';
COMMENT ON TABLE product_semantic_index IS 'Vector ngữ nghĩa của sản phẩm cho tư vấn bằng chatbot (pgvector).';

COMMENT ON COLUMN categories.parent_id IS 'Danh mục cha; NULL = danh mục gốc.';
COMMENT ON COLUMN product_variants.cost_price IS 'Giá vốn hiện tại; mỗi dòng đơn hàng lưu lại giá vốn tại thời điểm bán.';
COMMENT ON COLUMN product_variants.tracking_type IS 'NONE: chỉ đếm số lượng | SERIAL: quản lý từng số serial | IMEI: serial kèm IMEI.';
COMMENT ON COLUMN product_variants.warranty_months IS 'Thời hạn bảo hành (tháng) áp cho mỗi thiết bị bán ra; 0 = không bảo hành.';
COMMENT ON COLUMN product_semantic_index.content_hash IS 'Băm nội dung đã nhúng; khác hash thì cần nhúng lại.';
COMMENT ON COLUMN product_semantic_index.embedding_vector IS 'Không cố định số chiều để đổi được model embedding; truy vấn lọc theo vector_dims và embedding_model.';

-- Warehouse and goods receipts
COMMENT ON TABLE warehouses IS 'Chi nhánh cửa hàng; mỗi chi nhánh có một kho riêng. Đơn hàng, giỏ hàng, nhân viên và tồn kho gắn theo chi nhánh.';
COMMENT ON TABLE suppliers IS 'Nhà cung cấp.';
COMMENT ON TABLE inventory IS 'Tồn kho tổng hợp theo chi nhánh và biến thể. Với hàng có serial, số lượng luôn bằng số serial AVAILABLE + RESERVED và được cập nhật trong cùng giao dịch.';
COMMENT ON TABLE serial_numbers IS 'Từng thiết bị vật lý (nguồn gốc của tồn kho hàng có serial) và trạng thái của nó.';
COMMENT ON TABLE imei IS 'Số IMEI của thiết bị (máy hai SIM có hai IMEI).';
COMMENT ON TABLE goods_receipts IS 'Phiếu nhập hàng từ nhà cung cấp vào chi nhánh.';
COMMENT ON TABLE goods_receipt_items IS 'Dòng hàng của phiếu nhập.';
COMMENT ON TABLE goods_receipt_item_devices IS 'Serial khai báo trên phiếu nhập; thành serial_numbers khi phiếu được xác nhận.';
COMMENT ON TABLE goods_receipt_item_device_imeis IS 'IMEI khai báo kèm từng serial trên phiếu nhập.';

COMMENT ON COLUMN inventory.reserved_quantity IS 'Số lượng đang giữ cho đơn chưa hoàn tất; không vượt quá quantity.';
COMMENT ON COLUMN serial_numbers.status IS 'AVAILABLE | RESERVED | SOLD | RETURNED | DEFECTIVE.';
COMMENT ON COLUMN serial_numbers.serial_number IS 'Duy nhất toàn hệ thống (giả định đơn giản hóa; hãng chỉ đảm bảo duy nhất trong phạm vi của hãng).';
COMMENT ON COLUMN goods_receipts.status IS 'DRAFT | CONFIRMED | CANCELLED; chỉ khi CONFIRMED mới cộng tồn kho.';
COMMENT ON COLUMN goods_receipts.total_amount IS 'Tổng giá trị phiếu = tổng quantity * unit_cost.';

-- Sales and payments
COMMENT ON TABLE cart_items IS 'Dòng giỏ hàng; unit_price chỉ để hiển thị, giá được tính lại khi đặt hàng.';
COMMENT ON TABLE orders IS 'Đơn hàng (online, tại quầy, đơn đổi hàng). Thông tin người nhận và tiền là bản chụp tại lúc đặt.';
COMMENT ON TABLE order_items IS 'Dòng đơn hàng; lưu giá bán, khuyến mãi và giá vốn tại thời điểm bán. Hàng có serial: mỗi thiết bị một dòng.';
COMMENT ON TABLE order_status_history IS 'Lịch sử chuyển trạng thái đơn hàng: ai, khi nào, ghi chú.';
COMMENT ON TABLE payments IS 'Giao dịch thanh toán của đơn (một đơn có thể có nhiều lần thanh toán, ví dụ VNPAY thất bại rồi thử lại).';
COMMENT ON TABLE installment_providers IS 'Đơn vị cho vay trả góp.';
COMMENT ON TABLE installment_payments IS 'Hồ sơ trả góp gắn với một giao dịch thanh toán.';
COMMENT ON TABLE invoices IS 'Hóa đơn bán hàng; một hóa đơn cho mỗi đơn.';
COMMENT ON TABLE shipments IS 'Giao hàng do nhân viên giao hàng của cửa hàng thực hiện.';
COMMENT ON TABLE promotions IS 'Chương trình khuyến mãi theo phần trăm hoặc số tiền cố định.';
COMMENT ON TABLE promotion_products IS 'Sản phẩm áp dụng khuyến mãi.';

COMMENT ON COLUMN orders.subtotal IS 'Tổng giá gốc các dòng (unit_price * quantity).';
COMMENT ON COLUMN orders.discount_amount IS 'Tổng giảm giá các dòng; không vượt quá subtotal.';
COMMENT ON COLUMN orders.total_amount IS 'Bắt buộc = subtotal - discount_amount + shipping_fee (ck_orders_total_formula).';
COMMENT ON COLUMN orders.warehouse_id IS 'Chi nhánh xử lý đơn; bắt buộc với đơn tại quầy.';
COMMENT ON COLUMN orders.sales_channel IS 'ONLINE | IN_STORE.';
COMMENT ON COLUMN orders.shipping_address IS 'Bản chụp địa chỉ giao tại lúc đặt; không đổi khi khách sửa sổ địa chỉ.';
COMMENT ON COLUMN orders.cancel_reason IS 'Lý do hủy (khách, nhân viên, giao thất bại, hết hạn VNPAY).';
COMMENT ON COLUMN order_items.serial_id IS 'Thiết bị cụ thể đã bán; khi có thì quantity = 1.';
COMMENT ON COLUMN order_items.final_unit_price IS 'Bắt buộc = unit_price - discount_amount.';
COMMENT ON COLUMN order_items.cost_price IS 'Giá vốn tại lúc bán, dùng tính lợi nhuận.';
COMMENT ON COLUMN order_status_history.changed_by_user_id IS 'Người thực hiện; NULL = hệ thống (ví dụ hết hạn thanh toán VNPAY).';
COMMENT ON COLUMN payments.payment_method IS 'COD | VNPAY | INSTALLMENT | CASH.';
COMMENT ON COLUMN payments.transaction_code IS 'Mã giao dịch từ cổng thanh toán (VNPAY); duy nhất.';
COMMENT ON COLUMN installment_payments.remaining_amount IS 'Bắt buộc = total_amount - down_payment.';
COMMENT ON COLUMN invoices.total_amount IS 'Bản chụp tổng tiền đơn tại lúc xuất hóa đơn.';

-- After-sales
COMMENT ON TABLE return_requests IS 'Yêu cầu đổi / trả hàng của một đơn.';
COMMENT ON TABLE return_items IS 'Dòng hàng khách trả lại.';
COMMENT ON TABLE return_exchange_items IS 'Sản phẩm khách muốn nhận khi đổi hàng.';
COMMENT ON TABLE warranties IS 'Bảo hành của từng thiết bị đã bán (một bảo hành cho mỗi serial).';
COMMENT ON TABLE warranty_tickets IS 'Phiếu tiếp nhận bảo hành; mỗi thiết bị chỉ có một phiếu chưa xử lý xong tại một thời điểm.';
COMMENT ON TABLE reviews IS 'Đánh giá sản phẩm; chỉ khách đã mua, mỗi dòng đơn một đánh giá.';

COMMENT ON COLUMN return_requests.request_type IS 'RETURN: hoàn tiền | EXCHANGE: đổi sang sản phẩm khác.';
COMMENT ON COLUMN return_requests.refund_amount IS 'Giá trị hàng trả; với EXCHANGE là số tiền được cấn trừ vào đơn đổi.';
COMMENT ON COLUMN return_requests.exchange_order_id IS 'Đơn hàng mới tạo khi đổi hàng.';
COMMENT ON COLUMN return_requests.exchange_difference IS 'Tổng đơn đổi - refund_amount: > 0 khách trả thêm, < 0 cửa hàng hoàn lại.';
COMMENT ON COLUMN return_requests.customer_id IS 'Lặp lại từ orders để kiểm tra quyền sở hữu nhanh.';
COMMENT ON COLUMN return_items.received_condition IS 'Kết quả kiểm hàng khi nhận lại: RESTOCK (nhập lại kho) | DEFECTIVE (lỗi).';
COMMENT ON COLUMN warranties.end_date IS 'Ngày giao hàng (start_date) + product_variants.warranty_months.';
COMMENT ON COLUMN warranties.status IS 'ACTIVE | EXPIRED | VOIDED (thiết bị đã trả lại).';
COMMENT ON COLUMN warranty_tickets.serial_id IS 'Lặp lại từ warranties để tra cứu theo thiết bị.';
COMMENT ON COLUMN warranty_tickets.customer_id IS 'Lặp lại từ warranties để kiểm tra quyền sở hữu nhanh.';
COMMENT ON COLUMN reviews.product_id IS 'Lặp lại từ order_items -> product_variants để lọc đánh giá theo sản phẩm.';

-- Chatbot and RAG (written by chatbot-service)
COMMENT ON TABLE chat_sessions IS 'Phiên hội thoại chatbot của khách hàng.';
COMMENT ON TABLE chat_history IS 'Tin nhắn trong phiên chatbot.';
COMMENT ON TABLE message_feedback IS 'Đánh giá của khách cho một câu trả lời (1-5 sao).';
COMMENT ON TABLE chat_message_retrievals IS 'Đoạn tri thức đã dùng để trả lời một tin nhắn (truy vết nguồn RAG).';
COMMENT ON TABLE chatbot_qna IS 'Cặp hỏi - đáp soạn sẵn.';
COMMENT ON TABLE chatbot_scripts IS 'Kịch bản phản hồi theo từ khóa kích hoạt.';
COMMENT ON TABLE knowledge_documents IS 'Tài liệu tri thức chính thức cho chatbot (chính sách, hướng dẫn); chỉ PUBLISHED được truy xuất.';
COMMENT ON TABLE knowledge_chunks IS 'Đoạn cắt từ tài liệu tri thức kèm vector nhúng (pgvector).';

COMMENT ON COLUMN chat_history.sender_type IS 'USER | ASSISTANT.';
COMMENT ON COLUMN chat_history.customer_id IS 'Lặp lại từ chat_sessions để kiểm tra quyền sở hữu nhanh.';
COMMENT ON COLUMN knowledge_documents.status IS 'DRAFT | PUBLISHED | ARCHIVED (ngừng xuất bản, giữ lại vì lịch sử chat có thể tham chiếu).';
COMMENT ON COLUMN knowledge_chunks.embedding_vector IS 'Không cố định số chiều để đổi được model embedding; truy vấn lọc theo vector_dims.';
