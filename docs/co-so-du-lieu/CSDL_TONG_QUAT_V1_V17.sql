-- =====================================================================
-- CƠ SỞ DỮ LIỆU TỔNG QUÁT – HỆ THỐNG QUẢN LÝ CỬA HÀNG MUA BÁN THIẾT BỊ ĐIỆN TỬ
-- ---------------------------------------------------------------------
-- Sinh tự động ngày 07/11/2026 từ toàn bộ migration Flyway
-- backend/src/main/resources/db/migration (V1 → V17):
--   V1  Tạo toàn bộ CSDL (người dùng, sản phẩm, kho, đơn hàng, hậu mãi, chatbot, RAG)
--   V2  Bảng refresh_tokens
--   V3  Theo dõi Serial/IMEI trên biến thể và thiết bị trong phiếu nhập
--   V4  Chi nhánh (kho) cho giỏ hàng và đơn hàng
--   V5  Bổ sung chi nhánh cho dữ liệu cũ (chỉ cập nhật dữ liệu)
--   V6  Thời hạn bảo hành theo biến thể
--   V7  Chỉ mục ngữ nghĩa sản phẩm (pgvector)
--   V8  Sổ địa chỉ khách hàng
--   V9  Chức danh nhân viên (chỉ thêm dữ liệu vai trò)
--   V10 Ràng buộc CHECK cho trạng thái / kiểu liệt kê
--   V11 Lịch sử trạng thái đơn hàng, lý do hủy / từ chối / tình trạng hàng trả
--   V12 Nhân viên thuộc chi nhánh
--   V13 Bán hàng tại quầy (khách vãng lai, kênh bán, thanh toán tiền mặt)
--   V14 Đổi hàng (đổi trả sang sản phẩm khác)
--   V15 CHECK cho giá trị dẫn xuất (tổng tiền, trả góp, đơn tại quầy), trạng thái còn lại;
--       từ điển dữ liệu COMMENT ON cho 51 bảng và 50 cột
--   V16 Truy xuất nguồn gốc serial: serial_numbers.receipt_device_id → thiết bị khai báo trên
--       phiếu nhập (1-1), trigger kiểm tra cùng biến thể và cùng số serial
--   V17 Mỗi khách hàng đúng một giỏ hàng (customers 1-1 carts), bỏ cột carts.status
--
-- Kết quả: 51 bảng, 333 cột, 82 khóa ngoại, 92 ràng buộc CHECK, 189 chỉ mục, 1 trigger, 103 chú thích;
-- extension vector 0.8.7. Đã đối chiếu: V1–V14 trùng khớp 100% với CSDL dev (pg_dump
-- --schema-only, diff rỗng); V15–V17 đã chạy thử trên dữ liệu dev: không dòng nào vi phạm,
-- 80/80 serial nối được về phiếu nhập.
-- Dữ liệu nền kèm theo (do migration tạo): 8 vai trò, 16 quyền, 31 dòng gán quyền.
-- Không chứa dữ liệu nghiệp vụ, tài khoản hay mật khẩu.
--
-- Nhóm bảng:
--   Tài khoản & phân quyền : users, roles, permissions, user_roles, role_permissions,
--                            refresh_tokens, employees, customers, customer_addresses
--   Danh mục & sản phẩm    : categories, brands, products, product_variants,
--                            product_images, specifications, product_semantic_index
--   Kho & nhập hàng        : warehouses, suppliers, inventory, serial_numbers, imei,
--                            goods_receipts, goods_receipt_items,
--                            goods_receipt_item_devices, goods_receipt_item_device_imeis
--   Bán hàng & thanh toán  : carts, cart_items, orders, order_items, order_status_history,
--                            payments, installment_providers, installment_payments,
--                            invoices, shipments, promotions, promotion_products
--   Hậu mãi                : return_requests, return_items, return_exchange_items,
--                            warranties, warranty_tickets, reviews
--   Chatbot & RAG          : chat_sessions, chat_history, message_feedback,
--                            chat_message_retrievals, chatbot_qna, chatbot_scripts,
--                            knowledge_documents, knowledge_chunks
--
-- Cách dùng: chạy trên một CSDL PostgreSQL 16 trống, có sẵn extension pgvector
-- (ví dụ image pgvector/pgvector:pg16):  psql -d <ten_csdl> -f CSDL_TONG_QUAT_V1_V17.sql
--
-- LƯU Ý: đây là tài liệu tham khảo / để dựng nhanh CSDL. Ứng dụng backend vẫn tạo
-- CSDL bằng Flyway; không dùng file này cho CSDL do Flyway quản lý (Flyway sẽ báo
-- thiếu lịch sử migration). Khi thêm migration mới, sinh lại file này.
-- =====================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


--
-- Name: fn_serial_numbers_match_receipt_device(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_serial_numbers_match_receipt_device() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM goods_receipt_item_devices d
        JOIN goods_receipt_items i ON i.receipt_item_id = d.receipt_item_id
        WHERE d.receipt_device_id = NEW.receipt_device_id
          AND d.serial_number = NEW.serial_number
          AND i.variant_id = NEW.variant_id
    ) THEN
        RAISE EXCEPTION 'Serial % không khớp thiết bị đã khai báo trên phiếu nhập (biến thể hoặc số serial khác)',
            NEW.serial_number
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: FUNCTION fn_serial_numbers_match_receipt_device(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.fn_serial_numbers_match_receipt_device() IS 'Chặn serial trỏ tới thiết bị khai báo khác biến thể hoặc khác số serial.';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: brands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.brands (
    brand_id uuid DEFAULT gen_random_uuid() NOT NULL,
    brand_name character varying(255) NOT NULL,
    description text,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_brands_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE brands; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.brands IS 'Thương hiệu.';


--
-- Name: cart_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cart_items (
    cart_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    cart_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    CONSTRAINT ck_cart_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_cart_items_unit_price CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: TABLE cart_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.cart_items IS 'Dòng giỏ hàng; unit_price chỉ để hiển thị, giá được tính lại khi đặt hàng.';


--
-- Name: carts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carts (
    cart_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    warehouse_id uuid
);


--
-- Name: TABLE carts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.carts IS 'Giỏ hàng của khách (1-1 với customers): tạo một lần, được làm trống sau khi đặt hàng, gắn với chi nhánh đang mua.';


--
-- Name: COLUMN carts.warehouse_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.carts.warehouse_id IS 'Chi nhánh đang mua; đổi chi nhánh thì làm trống giỏ rồi gắn sang chi nhánh mới (tồn kho và giá tính theo chi nhánh).';


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    category_id uuid DEFAULT gen_random_uuid() NOT NULL,
    parent_id uuid,
    category_name character varying(255) NOT NULL,
    description text,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_categories_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.categories IS 'Danh mục sản phẩm dạng cây (parent_id); lọc theo danh mục cha bao gồm mọi danh mục con.';


--
-- Name: COLUMN categories.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.categories.parent_id IS 'Danh mục cha; NULL = danh mục gốc.';


--
-- Name: chat_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_history (
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    session_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    employee_id uuid,
    sender_type character varying(30) NOT NULL,
    message text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_chat_history_sender_type CHECK (((sender_type)::text = ANY ((ARRAY['USER'::character varying, 'ASSISTANT'::character varying])::text[])))
);


--
-- Name: TABLE chat_history; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_history IS 'Tin nhắn trong phiên chatbot.';


--
-- Name: COLUMN chat_history.customer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.customer_id IS 'Lặp lại từ chat_sessions để kiểm tra quyền sở hữu nhanh.';


--
-- Name: COLUMN chat_history.sender_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.sender_type IS 'USER | ASSISTANT.';


--
-- Name: chat_message_retrievals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_message_retrievals (
    retrieval_id uuid DEFAULT gen_random_uuid() NOT NULL,
    history_id uuid NOT NULL,
    chunk_id uuid NOT NULL,
    similarity_score numeric(10,8) NOT NULL,
    retrieved_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE chat_message_retrievals; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_message_retrievals IS 'Đoạn tri thức đã dùng để trả lời một tin nhắn (truy vết nguồn RAG).';


--
-- Name: chat_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_sessions (
    session_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    employee_id uuid,
    started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ended_at timestamp with time zone,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_chat_sessions_end_date CHECK (((ended_at IS NULL) OR (ended_at >= started_at))),
    CONSTRAINT ck_chat_sessions_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: TABLE chat_sessions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_sessions IS 'Phiên hội thoại chatbot của khách hàng.';


--
-- Name: chatbot_qna; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chatbot_qna (
    qna_id uuid DEFAULT gen_random_uuid() NOT NULL,
    question text NOT NULL,
    answer text NOT NULL,
    category character varying(255),
    keywords text,
    status character varying(30) NOT NULL,
    employee_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_chatbot_qna_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE chatbot_qna; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chatbot_qna IS 'Cặp hỏi - đáp soạn sẵn.';


--
-- Name: chatbot_scripts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chatbot_scripts (
    script_id uuid DEFAULT gen_random_uuid() NOT NULL,
    script_name character varying(255) NOT NULL,
    trigger_key character varying(255) NOT NULL,
    response_template text NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    status character varying(30) NOT NULL,
    employee_id uuid NOT NULL,
    CONSTRAINT ck_chatbot_scripts_priority CHECK ((priority >= 0)),
    CONSTRAINT ck_chatbot_scripts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE chatbot_scripts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chatbot_scripts IS 'Kịch bản phản hồi theo từ khóa kích hoạt.';


--
-- Name: customer_addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_addresses (
    address_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    label character varying(100),
    recipient_name character varying(255) NOT NULL,
    recipient_phone character varying(30) NOT NULL,
    address_line character varying(500) NOT NULL,
    ward character varying(150),
    district character varying(150),
    province character varying(150),
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE customer_addresses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customer_addresses IS 'Sổ địa chỉ giao hàng của khách; tối đa một địa chỉ mặc định.';


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    customer_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    full_name character varying(255) NOT NULL,
    address character varying(500),
    loyalty_point integer DEFAULT 0 NOT NULL,
    phone character varying(30),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_customers_account_or_phone CHECK (((user_id IS NOT NULL) OR (phone IS NOT NULL))),
    CONSTRAINT ck_customers_loyalty_point CHECK ((loyalty_point >= 0))
);


--
-- Name: TABLE customers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customers IS 'Hồ sơ khách hàng. Có tài khoản (user_id) hoặc là khách vãng lai tại quầy (chỉ có phone).';


--
-- Name: COLUMN customers.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.user_id IS 'Tài khoản đăng nhập; NULL với khách vãng lai.';


--
-- Name: COLUMN customers.address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.address IS 'Địa chỉ liên hệ trên hồ sơ (do nhân viên quản lý). Địa chỉ giao hàng nằm ở customer_addresses.';


--
-- Name: COLUMN customers.loyalty_point; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.loyalty_point IS 'Điểm tích lũy, không âm.';


--
-- Name: COLUMN customers.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customers.phone IS 'SĐT nhận diện khách vãng lai (duy nhất). Khách có tài khoản dùng users.phone.';


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employees (
    employee_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    employee_code character varying(50) NOT NULL,
    full_name character varying(255) NOT NULL,
    "position" character varying(100),
    warehouse_id uuid
);


--
-- Name: TABLE employees; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.employees IS 'Hồ sơ nhân viên, liên kết 1-1 với users.';


--
-- Name: COLUMN employees."position"; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.employees."position" IS 'Chức danh hiển thị; quyền thực tế lấy từ user_roles.';


--
-- Name: COLUMN employees.warehouse_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.employees.warehouse_id IS 'Chi nhánh làm việc; NULL = không gắn chi nhánh (quản trị, quản lý toàn cửa hàng) và xem được mọi chi nhánh.';


--
-- Name: goods_receipt_item_device_imeis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipt_item_device_imeis (
    receipt_device_imei_id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_device_id uuid NOT NULL,
    imei_number character varying(50) NOT NULL,
    CONSTRAINT ck_receipt_device_imeis_number_not_blank CHECK ((btrim((imei_number)::text) <> ''::text))
);


--
-- Name: TABLE goods_receipt_item_device_imeis; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goods_receipt_item_device_imeis IS 'IMEI khai báo kèm từng serial trên phiếu nhập.';


--
-- Name: goods_receipt_item_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipt_item_devices (
    receipt_device_id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_item_id uuid NOT NULL,
    serial_number character varying(255) NOT NULL,
    CONSTRAINT ck_receipt_item_devices_serial_not_blank CHECK ((btrim((serial_number)::text) <> ''::text))
);


--
-- Name: TABLE goods_receipt_item_devices; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goods_receipt_item_devices IS 'Serial khai báo trên phiếu nhập; thành serial_numbers khi phiếu được xác nhận.';


--
-- Name: goods_receipt_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipt_items (
    receipt_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_cost numeric(15,2) NOT NULL,
    CONSTRAINT ck_goods_receipt_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_goods_receipt_items_unit_cost CHECK ((unit_cost >= (0)::numeric))
);


--
-- Name: TABLE goods_receipt_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goods_receipt_items IS 'Dòng hàng của phiếu nhập.';


--
-- Name: goods_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipts (
    receipt_id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_code character varying(50) NOT NULL,
    supplier_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    receipt_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total_amount numeric(15,2) DEFAULT 0 NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_goods_receipts_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_goods_receipts_total_amount CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: TABLE goods_receipts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goods_receipts IS 'Phiếu nhập hàng từ nhà cung cấp vào chi nhánh.';


--
-- Name: COLUMN goods_receipts.total_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.goods_receipts.total_amount IS 'Tổng giá trị phiếu = tổng quantity * unit_cost.';


--
-- Name: COLUMN goods_receipts.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.goods_receipts.status IS 'DRAFT | CONFIRMED | CANCELLED; chỉ khi CONFIRMED mới cộng tồn kho.';


--
-- Name: imei; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imei (
    imei_id uuid DEFAULT gen_random_uuid() NOT NULL,
    serial_id uuid NOT NULL,
    imei_number character varying(50) NOT NULL
);


--
-- Name: TABLE imei; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.imei IS 'Số IMEI của thiết bị (máy hai SIM có hai IMEI).';


--
-- Name: installment_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.installment_payments (
    installment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    payment_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    down_payment numeric(15,2) NOT NULL,
    remaining_amount numeric(15,2) NOT NULL,
    term_months integer NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_installment_down_payment CHECK ((down_payment >= (0)::numeric)),
    CONSTRAINT ck_installment_down_payment_not_exceed_total CHECK ((down_payment <= total_amount)),
    CONSTRAINT ck_installment_payments_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_installment_remaining_amount CHECK ((remaining_amount >= (0)::numeric)),
    CONSTRAINT ck_installment_remaining_formula CHECK ((remaining_amount = (total_amount - down_payment))),
    CONSTRAINT ck_installment_term_months CHECK ((term_months > 0)),
    CONSTRAINT ck_installment_total_amount CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: TABLE installment_payments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.installment_payments IS 'Hồ sơ trả góp gắn với một giao dịch thanh toán.';


--
-- Name: COLUMN installment_payments.remaining_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.installment_payments.remaining_amount IS 'Bắt buộc = total_amount - down_payment.';


--
-- Name: installment_providers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.installment_providers (
    provider_id uuid DEFAULT gen_random_uuid() NOT NULL,
    provider_name character varying(255) NOT NULL,
    provider_code character varying(100) NOT NULL,
    contact_phone character varying(30),
    contact_email character varying(255),
    status character varying(30) NOT NULL,
    CONSTRAINT ck_installment_providers_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE installment_providers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.installment_providers IS 'Đơn vị cho vay trả góp.';


--
-- Name: inventory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory (
    inventory_id uuid DEFAULT gen_random_uuid() NOT NULL,
    warehouse_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    reserved_quantity integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_inventory_quantity CHECK ((quantity >= 0)),
    CONSTRAINT ck_inventory_reserved_not_exceed_quantity CHECK ((reserved_quantity <= quantity)),
    CONSTRAINT ck_inventory_reserved_quantity CHECK ((reserved_quantity >= 0))
);


--
-- Name: TABLE inventory; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inventory IS 'Tồn kho tổng hợp theo chi nhánh và biến thể. Với hàng có serial, số lượng luôn bằng số serial AVAILABLE + RESERVED và được cập nhật trong cùng giao dịch.';


--
-- Name: COLUMN inventory.reserved_quantity; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inventory.reserved_quantity IS 'Số lượng đang giữ cho đơn chưa hoàn tất; không vượt quá quantity.';


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    invoice_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    invoice_number character varying(100) NOT NULL,
    issued_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_invoices_status CHECK (((status)::text = ANY ((ARRAY['ISSUED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_invoices_total_amount CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: TABLE invoices; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.invoices IS 'Hóa đơn bán hàng; một hóa đơn cho mỗi đơn.';


--
-- Name: COLUMN invoices.total_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.invoices.total_amount IS 'Bản chụp tổng tiền đơn tại lúc xuất hóa đơn.';


--
-- Name: knowledge_chunks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_chunks (
    chunk_id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    embedding_vector public.vector,
    CONSTRAINT ck_knowledge_chunks_chunk_index CHECK ((chunk_index >= 0))
);


--
-- Name: TABLE knowledge_chunks; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.knowledge_chunks IS 'Đoạn cắt từ tài liệu tri thức kèm vector nhúng (pgvector).';


--
-- Name: COLUMN knowledge_chunks.embedding_vector; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.knowledge_chunks.embedding_vector IS 'Không cố định số chiều để đổi được model embedding; truy vấn lọc theo vector_dims.';


--
-- Name: knowledge_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_documents (
    document_id uuid DEFAULT gen_random_uuid() NOT NULL,
    title character varying(500) NOT NULL,
    source character varying(1000),
    document_type character varying(100),
    content text NOT NULL,
    status character varying(30) NOT NULL,
    employee_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_knowledge_documents_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: TABLE knowledge_documents; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.knowledge_documents IS 'Tài liệu tri thức chính thức cho chatbot (chính sách, hướng dẫn); chỉ PUBLISHED được truy xuất.';


--
-- Name: COLUMN knowledge_documents.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.knowledge_documents.status IS 'DRAFT | PUBLISHED | ARCHIVED (ngừng xuất bản, giữ lại vì lịch sử chat có thể tham chiếu).';


--
-- Name: message_feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.message_feedback (
    feedback_id uuid DEFAULT gen_random_uuid() NOT NULL,
    history_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    rating integer NOT NULL,
    feedback_text text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_message_feedback_rating CHECK (((rating >= 1) AND (rating <= 5)))
);


--
-- Name: TABLE message_feedback; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.message_feedback IS 'Đánh giá của khách cho một câu trả lời (1-5 sao).';


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    order_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    serial_id uuid,
    quantity integer NOT NULL,
    cost_price numeric(15,2) NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    discount_amount numeric(15,2) DEFAULT 0 NOT NULL,
    final_unit_price numeric(15,2) NOT NULL,
    CONSTRAINT ck_order_items_cost_price CHECK ((cost_price >= (0)::numeric)),
    CONSTRAINT ck_order_items_discount_amount CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT ck_order_items_final_unit_price CHECK ((final_unit_price >= (0)::numeric)),
    CONSTRAINT ck_order_items_final_unit_price_formula CHECK ((final_unit_price = (unit_price - discount_amount))),
    CONSTRAINT ck_order_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_order_items_serial_quantity CHECK (((serial_id IS NULL) OR (quantity = 1))),
    CONSTRAINT ck_order_items_unit_price CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: TABLE order_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_items IS 'Dòng đơn hàng; lưu giá bán, khuyến mãi và giá vốn tại thời điểm bán. Hàng có serial: mỗi thiết bị một dòng.';


--
-- Name: COLUMN order_items.serial_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.order_items.serial_id IS 'Thiết bị cụ thể đã bán; khi có thì quantity = 1.';


--
-- Name: COLUMN order_items.cost_price; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.order_items.cost_price IS 'Giá vốn tại lúc bán, dùng tính lợi nhuận.';


--
-- Name: COLUMN order_items.final_unit_price; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.order_items.final_unit_price IS 'Bắt buộc = unit_price - discount_amount.';


--
-- Name: order_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_status_history (
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    changed_by_user_id uuid,
    note text,
    changed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_order_status_history_from CHECK (((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))),
    CONSTRAINT ck_order_status_history_to CHECK (((to_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: TABLE order_status_history; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_status_history IS 'Lịch sử chuyển trạng thái đơn hàng: ai, khi nào, ghi chú.';


--
-- Name: COLUMN order_status_history.changed_by_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.order_status_history.changed_by_user_id IS 'Người thực hiện; NULL = hệ thống (ví dụ hết hạn thanh toán VNPAY).';


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    order_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    order_code character varying(100) NOT NULL,
    order_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    recipient_name character varying(255) NOT NULL,
    recipient_phone character varying(30) NOT NULL,
    shipping_address text NOT NULL,
    subtotal numeric(15,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(15,2) DEFAULT 0 NOT NULL,
    shipping_fee numeric(15,2) DEFAULT 0 NOT NULL,
    total_amount numeric(15,2) DEFAULT 0 NOT NULL,
    status character varying(30) NOT NULL,
    note text,
    warehouse_id uuid,
    cancel_reason text,
    sales_channel character varying(20) DEFAULT 'ONLINE'::character varying NOT NULL,
    CONSTRAINT ck_orders_discount_amount CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT ck_orders_discount_not_exceed_subtotal CHECK ((discount_amount <= subtotal)),
    CONSTRAINT ck_orders_in_store_branch CHECK ((((sales_channel)::text <> 'IN_STORE'::text) OR (warehouse_id IS NOT NULL))),
    CONSTRAINT ck_orders_sales_channel CHECK (((sales_channel)::text = ANY ((ARRAY['ONLINE'::character varying, 'IN_STORE'::character varying])::text[]))),
    CONSTRAINT ck_orders_shipping_fee CHECK ((shipping_fee >= (0)::numeric)),
    CONSTRAINT ck_orders_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_orders_subtotal CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT ck_orders_total_amount CHECK ((total_amount >= (0)::numeric)),
    CONSTRAINT ck_orders_total_formula CHECK ((total_amount = ((subtotal - discount_amount) + shipping_fee)))
);


--
-- Name: TABLE orders; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orders IS 'Đơn hàng (online, tại quầy, đơn đổi hàng). Thông tin người nhận và tiền là bản chụp tại lúc đặt.';


--
-- Name: COLUMN orders.shipping_address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.shipping_address IS 'Bản chụp địa chỉ giao tại lúc đặt; không đổi khi khách sửa sổ địa chỉ.';


--
-- Name: COLUMN orders.subtotal; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.subtotal IS 'Tổng giá gốc các dòng (unit_price * quantity).';


--
-- Name: COLUMN orders.discount_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.discount_amount IS 'Tổng giảm giá các dòng; không vượt quá subtotal.';


--
-- Name: COLUMN orders.total_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.total_amount IS 'Bắt buộc = subtotal - discount_amount + shipping_fee (ck_orders_total_formula).';


--
-- Name: COLUMN orders.warehouse_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.warehouse_id IS 'Chi nhánh xử lý đơn; bắt buộc với đơn tại quầy.';


--
-- Name: COLUMN orders.cancel_reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.cancel_reason IS 'Lý do hủy (khách, nhân viên, giao thất bại, hết hạn VNPAY).';


--
-- Name: COLUMN orders.sales_channel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.orders.sales_channel IS 'ONLINE | IN_STORE.';


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    payment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    payment_method character varying(50) NOT NULL,
    transaction_code character varying(255),
    amount numeric(15,2) NOT NULL,
    payment_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_payments_amount CHECK ((amount >= (0)::numeric)),
    CONSTRAINT ck_payments_payment_method CHECK (((payment_method)::text = ANY ((ARRAY['COD'::character varying, 'VNPAY'::character varying, 'INSTALLMENT'::character varying, 'CASH'::character varying])::text[]))),
    CONSTRAINT ck_payments_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: TABLE payments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.payments IS 'Giao dịch thanh toán của đơn (một đơn có thể có nhiều lần thanh toán, ví dụ VNPAY thất bại rồi thử lại).';


--
-- Name: COLUMN payments.payment_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.payments.payment_method IS 'COD | VNPAY | INSTALLMENT | CASH.';


--
-- Name: COLUMN payments.transaction_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.payments.transaction_code IS 'Mã giao dịch từ cổng thanh toán (VNPAY); duy nhất.';


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id uuid DEFAULT gen_random_uuid() NOT NULL,
    permission_name character varying(150) NOT NULL,
    description character varying(255)
);


--
-- Name: TABLE permissions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permissions IS 'Quyền thao tác chi tiết.';


--
-- Name: product_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_images (
    image_id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    image_url character varying(1000) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE product_images; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.product_images IS 'Ảnh sản phẩm; tối đa một ảnh chính.';


--
-- Name: product_semantic_index; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_semantic_index (
    product_id uuid NOT NULL,
    semantic_content text NOT NULL,
    content_hash character(64) NOT NULL,
    embedding_model character varying(255) NOT NULL,
    embedding_vector public.vector NOT NULL,
    indexed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE product_semantic_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.product_semantic_index IS 'Vector ngữ nghĩa của sản phẩm cho tư vấn bằng chatbot (pgvector).';


--
-- Name: COLUMN product_semantic_index.content_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_semantic_index.content_hash IS 'Băm nội dung đã nhúng; khác hash thì cần nhúng lại.';


--
-- Name: COLUMN product_semantic_index.embedding_vector; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_semantic_index.embedding_vector IS 'Không cố định số chiều để đổi được model embedding; truy vấn lọc theo vector_dims và embedding_model.';


--
-- Name: product_variants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_variants (
    variant_id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    sku character varying(100) NOT NULL,
    price numeric(15,2) NOT NULL,
    cost_price numeric(15,2) NOT NULL,
    color character varying(100),
    storage character varying(100),
    ram character varying(100),
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    tracking_type character varying(30) DEFAULT 'NONE'::character varying NOT NULL,
    warranty_months integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_product_variants_cost_price CHECK ((cost_price >= (0)::numeric)),
    CONSTRAINT ck_product_variants_price CHECK ((price >= (0)::numeric)),
    CONSTRAINT ck_product_variants_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))),
    CONSTRAINT ck_product_variants_tracking_type CHECK (((tracking_type)::text = ANY ((ARRAY['NONE'::character varying, 'SERIAL'::character varying, 'IMEI'::character varying])::text[]))),
    CONSTRAINT ck_product_variants_warranty_months CHECK ((warranty_months >= 0))
);


--
-- Name: TABLE product_variants; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.product_variants IS 'Biến thể bán được (SKU): màu, dung lượng, RAM, giá, cách theo dõi thiết bị, thời hạn bảo hành.';


--
-- Name: COLUMN product_variants.cost_price; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_variants.cost_price IS 'Giá vốn hiện tại; mỗi dòng đơn hàng lưu lại giá vốn tại thời điểm bán.';


--
-- Name: COLUMN product_variants.tracking_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_variants.tracking_type IS 'NONE: chỉ đếm số lượng | SERIAL: quản lý từng số serial | IMEI: serial kèm IMEI.';


--
-- Name: COLUMN product_variants.warranty_months; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.product_variants.warranty_months IS 'Thời hạn bảo hành (tháng) áp cho mỗi thiết bị bán ra; 0 = không bảo hành.';


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    product_id uuid DEFAULT gen_random_uuid() NOT NULL,
    category_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    product_name character varying(255) NOT NULL,
    description text,
    status character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_products_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE products; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.products IS 'Sản phẩm (mức mẫu mã); giá và tồn kho nằm ở biến thể.';


--
-- Name: promotion_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotion_products (
    promotion_id uuid NOT NULL,
    product_id uuid NOT NULL
);


--
-- Name: TABLE promotion_products; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.promotion_products IS 'Sản phẩm áp dụng khuyến mãi.';


--
-- Name: promotions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotions (
    promotion_id uuid DEFAULT gen_random_uuid() NOT NULL,
    promotion_name character varying(255) NOT NULL,
    description text,
    discount_type character varying(30) NOT NULL,
    discount_value numeric(15,2) NOT NULL,
    start_date timestamp with time zone NOT NULL,
    end_date timestamp with time zone NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_promotions_date_range CHECK ((end_date >= start_date)),
    CONSTRAINT ck_promotions_discount_type CHECK (((discount_type)::text = ANY ((ARRAY['PERCENTAGE'::character varying, 'FIXED_AMOUNT'::character varying])::text[]))),
    CONSTRAINT ck_promotions_discount_value CHECK ((discount_value >= (0)::numeric)),
    CONSTRAINT ck_promotions_percentage_range CHECK ((((discount_type)::text <> 'PERCENTAGE'::text) OR (discount_value <= (100)::numeric))),
    CONSTRAINT ck_promotions_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE promotions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.promotions IS 'Chương trình khuyến mãi theo phần trăm hoặc số tiền cố định.';


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    refresh_token_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    replaced_by_token_id uuid,
    CONSTRAINT ck_refresh_tokens_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_refresh_tokens_revoked_at CHECK (((revoked_at IS NULL) OR (revoked_at >= created_at)))
);


--
-- Name: TABLE refresh_tokens; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.refresh_tokens IS 'Refresh token JWT; chỉ lưu SHA-256 của token, xoay vòng qua replaced_by_token_id.';


--
-- Name: COLUMN refresh_tokens.token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_tokens.token_hash IS 'SHA-256 (hex) của refresh token; token gốc không được lưu.';


--
-- Name: COLUMN refresh_tokens.replaced_by_token_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_tokens.replaced_by_token_id IS 'Token mới thay thế khi xoay vòng; dùng để phát hiện token bị dùng lại.';


--
-- Name: return_exchange_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_exchange_items (
    exchange_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    return_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT ck_return_exchange_items_quantity CHECK ((quantity > 0))
);


--
-- Name: TABLE return_exchange_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.return_exchange_items IS 'Sản phẩm khách muốn nhận khi đổi hàng.';


--
-- Name: return_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_items (
    return_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    return_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    serial_id uuid,
    quantity integer NOT NULL,
    reason character varying(500),
    received_condition character varying(20),
    CONSTRAINT ck_return_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_return_items_received_condition CHECK (((received_condition IS NULL) OR ((received_condition)::text = ANY ((ARRAY['RESTOCK'::character varying, 'DEFECTIVE'::character varying])::text[])))),
    CONSTRAINT ck_return_items_serial_quantity CHECK (((serial_id IS NULL) OR (quantity = 1)))
);


--
-- Name: TABLE return_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.return_items IS 'Dòng hàng khách trả lại.';


--
-- Name: COLUMN return_items.received_condition; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_items.received_condition IS 'Kết quả kiểm hàng khi nhận lại: RESTOCK (nhập lại kho) | DEFECTIVE (lỗi).';


--
-- Name: return_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_requests (
    return_id uuid DEFAULT gen_random_uuid() NOT NULL,
    return_code character varying(50) NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    employee_id uuid,
    reason text NOT NULL,
    request_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at timestamp with time zone,
    refund_amount numeric(15,2) DEFAULT 0 NOT NULL,
    status character varying(30) NOT NULL,
    rejection_reason text,
    request_type character varying(20) DEFAULT 'RETURN'::character varying NOT NULL,
    exchange_order_id uuid,
    exchange_difference numeric(15,2),
    CONSTRAINT ck_return_requests_exchange_difference_type CHECK (((exchange_difference IS NULL) OR ((request_type)::text = 'EXCHANGE'::text))),
    CONSTRAINT ck_return_requests_exchange_order_type CHECK (((exchange_order_id IS NULL) OR ((request_type)::text = 'EXCHANGE'::text))),
    CONSTRAINT ck_return_requests_processed_date CHECK (((processed_at IS NULL) OR (processed_at >= request_date))),
    CONSTRAINT ck_return_requests_refund_amount CHECK ((refund_amount >= (0)::numeric)),
    CONSTRAINT ck_return_requests_request_type CHECK (((request_type)::text = ANY ((ARRAY['RETURN'::character varying, 'EXCHANGE'::character varying])::text[]))),
    CONSTRAINT ck_return_requests_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'RECEIVED'::character varying, 'REFUNDED'::character varying, 'EXCHANGED'::character varying])::text[])))
);


--
-- Name: TABLE return_requests; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.return_requests IS 'Yêu cầu đổi / trả hàng của một đơn.';


--
-- Name: COLUMN return_requests.customer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_requests.customer_id IS 'Lặp lại từ orders để kiểm tra quyền sở hữu nhanh.';


--
-- Name: COLUMN return_requests.refund_amount; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_requests.refund_amount IS 'Giá trị hàng trả; với EXCHANGE là số tiền được cấn trừ vào đơn đổi.';


--
-- Name: COLUMN return_requests.request_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_requests.request_type IS 'RETURN: hoàn tiền | EXCHANGE: đổi sang sản phẩm khác.';


--
-- Name: COLUMN return_requests.exchange_order_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_requests.exchange_order_id IS 'Đơn hàng mới tạo khi đổi hàng.';


--
-- Name: COLUMN return_requests.exchange_difference; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.return_requests.exchange_difference IS 'Tổng đơn đổi - refund_amount: > 0 khách trả thêm, < 0 cửa hàng hoàn lại.';


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    review_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    product_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    employee_id uuid,
    rating integer NOT NULL,
    review_text text,
    reply_text text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    replied_at timestamp with time zone,
    status character varying(30) DEFAULT 'VISIBLE'::character varying NOT NULL,
    CONSTRAINT ck_reviews_rating CHECK (((rating >= 1) AND (rating <= 5))),
    CONSTRAINT ck_reviews_replied_date CHECK (((replied_at IS NULL) OR (replied_at >= created_at))),
    CONSTRAINT ck_reviews_status CHECK (((status)::text = ANY ((ARRAY['VISIBLE'::character varying, 'HIDDEN'::character varying])::text[])))
);


--
-- Name: TABLE reviews; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.reviews IS 'Đánh giá sản phẩm; chỉ khách đã mua, mỗi dòng đơn một đánh giá.';


--
-- Name: COLUMN reviews.product_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reviews.product_id IS 'Lặp lại từ order_items -> product_variants để lọc đánh giá theo sản phẩm.';


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);


--
-- Name: TABLE role_permissions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.role_permissions IS 'Gán quyền cho vai trò (nhiều-nhiều).';


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id uuid DEFAULT gen_random_uuid() NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(255)
);


--
-- Name: TABLE roles; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.roles IS 'Vai trò / chức danh (ADMIN, MANAGER, SALES_STAFF, ...).';


--
-- Name: serial_numbers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.serial_numbers (
    serial_id uuid DEFAULT gen_random_uuid() NOT NULL,
    variant_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    serial_number character varying(255) NOT NULL,
    status character varying(30) NOT NULL,
    receipt_device_id uuid,
    CONSTRAINT ck_serial_numbers_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RESERVED'::character varying, 'SOLD'::character varying, 'RETURNED'::character varying, 'DEFECTIVE'::character varying])::text[])))
);


--
-- Name: TABLE serial_numbers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.serial_numbers IS 'Từng thiết bị vật lý (nguồn gốc của tồn kho hàng có serial) và trạng thái của nó.';


--
-- Name: COLUMN serial_numbers.serial_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.serial_numbers.serial_number IS 'Duy nhất toàn hệ thống (giả định đơn giản hóa; hãng chỉ đảm bảo duy nhất trong phạm vi của hãng).';


--
-- Name: COLUMN serial_numbers.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.serial_numbers.status IS 'AVAILABLE | RESERVED | SOLD | RETURNED | DEFECTIVE.';


--
-- Name: COLUMN serial_numbers.receipt_device_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.serial_numbers.receipt_device_id IS 'Thiết bị đã khai báo trên phiếu nhập sinh ra serial này (1-1); qua đó truy được phiếu nhập, nhà cung cấp, ngày nhập, giá vốn. Trigger đảm bảo cùng biến thể và cùng số serial.';


--
-- Name: shipments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipments (
    shipment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    tracking_number character varying(255),
    shipping_provider character varying(255),
    shipped_date timestamp with time zone,
    delivered_date timestamp with time zone,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_shipments_delivery_date CHECK (((delivered_date IS NULL) OR (shipped_date IS NULL) OR (delivered_date >= shipped_date))),
    CONSTRAINT ck_shipments_status CHECK (((status)::text = ANY ((ARRAY['SHIPPING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE shipments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.shipments IS 'Giao hàng do nhân viên giao hàng của cửa hàng thực hiện.';


--
-- Name: specifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.specifications (
    specification_id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    spec_key character varying(100) NOT NULL,
    spec_value character varying(1000) NOT NULL
);


--
-- Name: TABLE specifications; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.specifications IS 'Thông số kỹ thuật dạng khóa - giá trị.';


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    supplier_id uuid DEFAULT gen_random_uuid() NOT NULL,
    supplier_code character varying(50) NOT NULL,
    supplier_name character varying(255) NOT NULL,
    phone character varying(30),
    email character varying(255),
    address character varying(500),
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_suppliers_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE suppliers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.suppliers IS 'Nhà cung cấp.';


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL
);


--
-- Name: TABLE user_roles; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_roles IS 'Gán vai trò cho tài khoản. Cấu trúc nhiều-nhiều để mở rộng; nghiệp vụ hiện tại giới hạn mỗi tài khoản một vai trò (uq_user_roles_user_id).';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id uuid DEFAULT gen_random_uuid() NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    display_name character varying(255),
    phone character varying(30),
    status character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.users IS 'Tài khoản đăng nhập (nhân viên và khách hàng). Mật khẩu lưu dạng băm BCrypt.';


--
-- Name: COLUMN users.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.status IS 'ACTIVE | INACTIVE | LOCKED.';


--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    warehouse_id uuid DEFAULT gen_random_uuid() NOT NULL,
    warehouse_name character varying(255) NOT NULL,
    address character varying(500),
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_warehouses_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: TABLE warehouses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.warehouses IS 'Chi nhánh cửa hàng; mỗi chi nhánh có một kho riêng. Đơn hàng, giỏ hàng, nhân viên và tồn kho gắn theo chi nhánh.';


--
-- Name: warranties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warranties (
    warranty_id uuid DEFAULT gen_random_uuid() NOT NULL,
    serial_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_warranties_date_range CHECK ((end_date >= start_date)),
    CONSTRAINT ck_warranties_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'VOIDED'::character varying])::text[])))
);


--
-- Name: TABLE warranties; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.warranties IS 'Bảo hành của từng thiết bị đã bán (một bảo hành cho mỗi serial).';


--
-- Name: COLUMN warranties.end_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.warranties.end_date IS 'Ngày giao hàng (start_date) + product_variants.warranty_months.';


--
-- Name: COLUMN warranties.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.warranties.status IS 'ACTIVE | EXPIRED | VOIDED (thiết bị đã trả lại).';


--
-- Name: warranty_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warranty_tickets (
    ticket_id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_code character varying(50) NOT NULL,
    warranty_id uuid NOT NULL,
    serial_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    employee_id uuid,
    issue_description text NOT NULL,
    resolution_note text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp with time zone,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_warranty_tickets_closed_resolved_at CHECK ((((status)::text <> ALL ((ARRAY['COMPLETED'::character varying, 'REJECTED'::character varying])::text[])) OR (resolved_at IS NOT NULL))),
    CONSTRAINT ck_warranty_tickets_resolved_date CHECK (((resolved_at IS NULL) OR (resolved_at >= created_at))),
    CONSTRAINT ck_warranty_tickets_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: TABLE warranty_tickets; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.warranty_tickets IS 'Phiếu tiếp nhận bảo hành; mỗi thiết bị chỉ có một phiếu chưa xử lý xong tại một thời điểm.';


--
-- Name: COLUMN warranty_tickets.serial_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.warranty_tickets.serial_id IS 'Lặp lại từ warranties để tra cứu theo thiết bị.';


--
-- Name: COLUMN warranty_tickets.customer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.warranty_tickets.customer_id IS 'Lặp lại từ warranties để kiểm tra quyền sở hữu nhanh.';


--
-- Data for Name: brands; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.brands (brand_id, brand_name, description, status) FROM stdin;
\.


--
-- Data for Name: cart_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.cart_items (cart_item_id, cart_id, variant_id, quantity, unit_price) FROM stdin;
\.


--
-- Data for Name: carts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.carts (cart_id, customer_id, created_at, updated_at, warehouse_id) FROM stdin;
\.


--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.categories (category_id, parent_id, category_name, description, status) FROM stdin;
\.


--
-- Data for Name: chat_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_history (history_id, session_id, customer_id, employee_id, sender_type, message, created_at) FROM stdin;
\.


--
-- Data for Name: chat_message_retrievals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_message_retrievals (retrieval_id, history_id, chunk_id, similarity_score, retrieved_at) FROM stdin;
\.


--
-- Data for Name: chat_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_sessions (session_id, customer_id, employee_id, started_at, ended_at, status) FROM stdin;
\.


--
-- Data for Name: chatbot_qna; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chatbot_qna (qna_id, question, answer, category, keywords, status, employee_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: chatbot_scripts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chatbot_scripts (script_id, script_name, trigger_key, response_template, priority, status, employee_id) FROM stdin;
\.


--
-- Data for Name: customer_addresses; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.customer_addresses (address_id, customer_id, label, recipient_name, recipient_phone, address_line, ward, district, province, is_default, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.customers (customer_id, user_id, full_name, address, loyalty_point, phone, created_at) FROM stdin;
\.


--
-- Data for Name: employees; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.employees (employee_id, user_id, employee_code, full_name, "position", warehouse_id) FROM stdin;
\.


--
-- Data for Name: goods_receipt_item_device_imeis; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.goods_receipt_item_device_imeis (receipt_device_imei_id, receipt_device_id, imei_number) FROM stdin;
\.


--
-- Data for Name: goods_receipt_item_devices; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.goods_receipt_item_devices (receipt_device_id, receipt_item_id, serial_number) FROM stdin;
\.


--
-- Data for Name: goods_receipt_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.goods_receipt_items (receipt_item_id, receipt_id, variant_id, quantity, unit_cost) FROM stdin;
\.


--
-- Data for Name: goods_receipts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.goods_receipts (receipt_id, receipt_code, supplier_id, warehouse_id, employee_id, receipt_date, total_amount, status) FROM stdin;
\.


--
-- Data for Name: imei; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.imei (imei_id, serial_id, imei_number) FROM stdin;
\.


--
-- Data for Name: installment_payments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.installment_payments (installment_id, payment_id, provider_id, total_amount, down_payment, remaining_amount, term_months, status) FROM stdin;
\.


--
-- Data for Name: installment_providers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.installment_providers (provider_id, provider_name, provider_code, contact_phone, contact_email, status) FROM stdin;
\.


--
-- Data for Name: inventory; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.inventory (inventory_id, warehouse_id, variant_id, quantity, reserved_quantity, updated_at) FROM stdin;
\.


--
-- Data for Name: invoices; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.invoices (invoice_id, order_id, invoice_number, issued_date, total_amount, status) FROM stdin;
\.


--
-- Data for Name: knowledge_chunks; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.knowledge_chunks (chunk_id, document_id, chunk_index, content, embedding_vector) FROM stdin;
\.


--
-- Data for Name: knowledge_documents; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.knowledge_documents (document_id, title, source, document_type, content, status, employee_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: message_feedback; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.message_feedback (feedback_id, history_id, customer_id, rating, feedback_text, created_at) FROM stdin;
\.


--
-- Data for Name: order_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.order_items (order_item_id, order_id, variant_id, serial_id, quantity, cost_price, unit_price, discount_amount, final_unit_price) FROM stdin;
\.


--
-- Data for Name: order_status_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.order_status_history (history_id, order_id, from_status, to_status, changed_by_user_id, note, changed_at) FROM stdin;
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.orders (order_id, customer_id, order_code, order_date, recipient_name, recipient_phone, shipping_address, subtotal, discount_amount, shipping_fee, total_amount, status, note, warehouse_id, cancel_reason, sales_channel) FROM stdin;
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.payments (payment_id, order_id, payment_method, transaction_code, amount, payment_date, status) FROM stdin;
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permissions (permission_id, permission_name, description) FROM stdin;
c7a178cf-d225-4862-8c7a-2f2cfd4c611f	USER_VIEW	Xem thông tin người dùng
4933d070-ea10-4583-b856-c84b4d73122b	USER_CREATE	Tạo người dùng
d94fe6bf-d4b7-4ebe-b4bd-81e1de118808	USER_UPDATE	Cập nhật thông tin người dùng
c4bdfc0e-8f80-4ea6-b989-e0074dc39247	USER_DISABLE	Vô hiệu hóa người dùng
331ab1df-f907-48e8-98ba-34e6764a27e5	USER_ROLE_VIEW	Xem vai trò được gán cho người dùng
bc56af31-9a57-4481-89a3-65e0415f9310	USER_ROLE_ASSIGN	Gán vai trò cho người dùng
6d8b8ccf-5713-47e9-95f5-3909eb242da4	USER_ROLE_REMOVE	Gỡ vai trò khỏi người dùng
d2e2a415-4ade-4e0f-9b61-92013492b683	ROLE_PERMISSION_VIEW	Xem quyền của vai trò
804f8eb6-225b-462d-b49c-b5bc31ded44c	ROLE_PERMISSION_ASSIGN	Gán quyền cho vai trò
fdfe9414-87ee-4056-b5ef-4ad16240f635	ROLE_PERMISSION_REMOVE	Gỡ quyền khỏi vai trò
951aa5e9-f880-4565-87d5-ba0539cc2307	EMPLOYEE_VIEW	Xem thông tin nhân viên
25f3f9b4-ac49-4aed-b0db-3e15c25d3df5	EMPLOYEE_CREATE	Tạo hồ sơ nhân viên
fd4cc0d4-aa9f-4132-930e-6a9f56f2ff66	EMPLOYEE_UPDATE	Cập nhật hồ sơ nhân viên
f9201b9e-8cf4-4b86-be34-cf81e94ccbda	CUSTOMER_VIEW	Xem thông tin khách hàng
9d6ead05-9634-40ad-b6e0-552a4c1cbee4	CUSTOMER_CREATE	Tạo hồ sơ khách hàng
376570c0-1f35-49b2-a8c1-dd3c253058ce	CUSTOMER_UPDATE	Cập nhật thông tin khách hàng
\.


--
-- Data for Name: product_images; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.product_images (image_id, product_id, image_url, is_primary) FROM stdin;
\.


--
-- Data for Name: product_semantic_index; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.product_semantic_index (product_id, semantic_content, content_hash, embedding_model, embedding_vector, indexed_at) FROM stdin;
\.


--
-- Data for Name: product_variants; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.product_variants (variant_id, product_id, sku, price, cost_price, color, storage, ram, status, tracking_type, warranty_months) FROM stdin;
\.


--
-- Data for Name: products; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.products (product_id, category_id, brand_id, product_name, description, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: promotion_products; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.promotion_products (promotion_id, product_id) FROM stdin;
\.


--
-- Data for Name: promotions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.promotions (promotion_id, promotion_name, description, discount_type, discount_value, start_date, end_date, status) FROM stdin;
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.refresh_tokens (refresh_token_id, user_id, token_hash, created_at, expires_at, revoked_at, replaced_by_token_id) FROM stdin;
\.


--
-- Data for Name: return_exchange_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.return_exchange_items (exchange_item_id, return_id, variant_id, quantity) FROM stdin;
\.


--
-- Data for Name: return_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.return_items (return_item_id, return_id, order_item_id, serial_id, quantity, reason, received_condition) FROM stdin;
\.


--
-- Data for Name: return_requests; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.return_requests (return_id, return_code, order_id, customer_id, employee_id, reason, request_date, processed_at, refund_amount, status, rejection_reason, request_type, exchange_order_id, exchange_difference) FROM stdin;
\.


--
-- Data for Name: reviews; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.reviews (review_id, customer_id, product_id, order_item_id, employee_id, rating, review_text, reply_text, created_at, replied_at, status) FROM stdin;
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_permissions (role_id, permission_id) FROM stdin;
f486ff81-580d-4702-87a8-50d26acce7c9	c7a178cf-d225-4862-8c7a-2f2cfd4c611f
f486ff81-580d-4702-87a8-50d26acce7c9	4933d070-ea10-4583-b856-c84b4d73122b
f486ff81-580d-4702-87a8-50d26acce7c9	d94fe6bf-d4b7-4ebe-b4bd-81e1de118808
f486ff81-580d-4702-87a8-50d26acce7c9	c4bdfc0e-8f80-4ea6-b989-e0074dc39247
f486ff81-580d-4702-87a8-50d26acce7c9	331ab1df-f907-48e8-98ba-34e6764a27e5
f486ff81-580d-4702-87a8-50d26acce7c9	bc56af31-9a57-4481-89a3-65e0415f9310
f486ff81-580d-4702-87a8-50d26acce7c9	6d8b8ccf-5713-47e9-95f5-3909eb242da4
f486ff81-580d-4702-87a8-50d26acce7c9	d2e2a415-4ade-4e0f-9b61-92013492b683
f486ff81-580d-4702-87a8-50d26acce7c9	804f8eb6-225b-462d-b49c-b5bc31ded44c
f486ff81-580d-4702-87a8-50d26acce7c9	fdfe9414-87ee-4056-b5ef-4ad16240f635
f486ff81-580d-4702-87a8-50d26acce7c9	951aa5e9-f880-4565-87d5-ba0539cc2307
f486ff81-580d-4702-87a8-50d26acce7c9	25f3f9b4-ac49-4aed-b0db-3e15c25d3df5
f486ff81-580d-4702-87a8-50d26acce7c9	fd4cc0d4-aa9f-4132-930e-6a9f56f2ff66
f486ff81-580d-4702-87a8-50d26acce7c9	f9201b9e-8cf4-4b86-be34-cf81e94ccbda
f486ff81-580d-4702-87a8-50d26acce7c9	9d6ead05-9634-40ad-b6e0-552a4c1cbee4
f486ff81-580d-4702-87a8-50d26acce7c9	376570c0-1f35-49b2-a8c1-dd3c253058ce
c2ee15f4-0e56-4001-b66b-1eaa470a5388	c7a178cf-d225-4862-8c7a-2f2cfd4c611f
c2ee15f4-0e56-4001-b66b-1eaa470a5388	951aa5e9-f880-4565-87d5-ba0539cc2307
c2ee15f4-0e56-4001-b66b-1eaa470a5388	25f3f9b4-ac49-4aed-b0db-3e15c25d3df5
c2ee15f4-0e56-4001-b66b-1eaa470a5388	fd4cc0d4-aa9f-4132-930e-6a9f56f2ff66
c2ee15f4-0e56-4001-b66b-1eaa470a5388	f9201b9e-8cf4-4b86-be34-cf81e94ccbda
c2ee15f4-0e56-4001-b66b-1eaa470a5388	9d6ead05-9634-40ad-b6e0-552a4c1cbee4
c2ee15f4-0e56-4001-b66b-1eaa470a5388	376570c0-1f35-49b2-a8c1-dd3c253058ce
aa0e7ff2-bebf-4b6e-a39b-55814a3c62f1	951aa5e9-f880-4565-87d5-ba0539cc2307
aa0e7ff2-bebf-4b6e-a39b-55814a3c62f1	f9201b9e-8cf4-4b86-be34-cf81e94ccbda
4b686a10-0b77-4bd8-8984-c2ad713c5748	951aa5e9-f880-4565-87d5-ba0539cc2307
3371dd35-c741-4cf0-885c-2ce531dba33e	951aa5e9-f880-4565-87d5-ba0539cc2307
2089228b-adc8-430d-9810-05ee91191862	951aa5e9-f880-4565-87d5-ba0539cc2307
af24b0d9-a2af-476b-b213-94054737acac	951aa5e9-f880-4565-87d5-ba0539cc2307
4b686a10-0b77-4bd8-8984-c2ad713c5748	f9201b9e-8cf4-4b86-be34-cf81e94ccbda
2089228b-adc8-430d-9810-05ee91191862	f9201b9e-8cf4-4b86-be34-cf81e94ccbda
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (role_id, role_name, description) FROM stdin;
f486ff81-580d-4702-87a8-50d26acce7c9	ADMIN	Quản trị viên hệ thống
c2ee15f4-0e56-4001-b66b-1eaa470a5388	MANAGER	Quản lý cửa hàng
aa0e7ff2-bebf-4b6e-a39b-55814a3c62f1	STAFF	Nhân viên cửa hàng
404f8208-8b97-4704-a06a-b8e57bf8de65	CUSTOMER	Khách hàng
4b686a10-0b77-4bd8-8984-c2ad713c5748	SALES_STAFF	Nhân viên bán hàng
3371dd35-c741-4cf0-885c-2ce531dba33e	WAREHOUSE_KEEPER	Thủ kho
2089228b-adc8-430d-9810-05ee91191862	WARRANTY_TECHNICIAN	Nhân viên kỹ thuật/bảo hành
af24b0d9-a2af-476b-b213-94054737acac	DELIVERY_STAFF	Nhân viên giao hàng
\.


--
-- Data for Name: serial_numbers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.serial_numbers (serial_id, variant_id, warehouse_id, serial_number, status, receipt_device_id) FROM stdin;
\.


--
-- Data for Name: shipments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.shipments (shipment_id, order_id, employee_id, tracking_number, shipping_provider, shipped_date, delivered_date, status) FROM stdin;
\.


--
-- Data for Name: specifications; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.specifications (specification_id, product_id, spec_key, spec_value) FROM stdin;
\.


--
-- Data for Name: suppliers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.suppliers (supplier_id, supplier_code, supplier_name, phone, email, address, status) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_roles (user_id, role_id) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (user_id, username, password, email, display_name, phone, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: warehouses; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.warehouses (warehouse_id, warehouse_name, address, status) FROM stdin;
\.


--
-- Data for Name: warranties; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.warranties (warranty_id, serial_id, order_item_id, customer_id, start_date, end_date, status) FROM stdin;
\.


--
-- Data for Name: warranty_tickets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.warranty_tickets (ticket_id, ticket_code, warranty_id, serial_id, customer_id, employee_id, issue_description, resolution_note, created_at, resolved_at, status) FROM stdin;
\.


--
-- Name: brands brands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brands
    ADD CONSTRAINT brands_pkey PRIMARY KEY (brand_id);


--
-- Name: cart_items cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (cart_item_id);


--
-- Name: carts carts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT carts_pkey PRIMARY KEY (cart_id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (category_id);


--
-- Name: chat_history chat_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history
    ADD CONSTRAINT chat_history_pkey PRIMARY KEY (history_id);


--
-- Name: chat_message_retrievals chat_message_retrievals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message_retrievals
    ADD CONSTRAINT chat_message_retrievals_pkey PRIMARY KEY (retrieval_id);


--
-- Name: chat_sessions chat_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_sessions
    ADD CONSTRAINT chat_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: chatbot_qna chatbot_qna_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_qna
    ADD CONSTRAINT chatbot_qna_pkey PRIMARY KEY (qna_id);


--
-- Name: chatbot_scripts chatbot_scripts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_scripts
    ADD CONSTRAINT chatbot_scripts_pkey PRIMARY KEY (script_id);


--
-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (address_id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);


--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (employee_id);


--
-- Name: goods_receipt_item_device_imeis goods_receipt_item_device_imeis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_device_imeis
    ADD CONSTRAINT goods_receipt_item_device_imeis_pkey PRIMARY KEY (receipt_device_imei_id);


--
-- Name: goods_receipt_item_devices goods_receipt_item_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_devices
    ADD CONSTRAINT goods_receipt_item_devices_pkey PRIMARY KEY (receipt_device_id);


--
-- Name: goods_receipt_items goods_receipt_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_pkey PRIMARY KEY (receipt_item_id);


--
-- Name: goods_receipts goods_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_pkey PRIMARY KEY (receipt_id);


--
-- Name: imei imei_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imei
    ADD CONSTRAINT imei_pkey PRIMARY KEY (imei_id);


--
-- Name: installment_payments installment_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_payments
    ADD CONSTRAINT installment_payments_pkey PRIMARY KEY (installment_id);


--
-- Name: installment_providers installment_providers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_providers
    ADD CONSTRAINT installment_providers_pkey PRIMARY KEY (provider_id);


--
-- Name: inventory inventory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (inventory_id);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (invoice_id);


--
-- Name: knowledge_chunks knowledge_chunks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT knowledge_chunks_pkey PRIMARY KEY (chunk_id);


--
-- Name: knowledge_documents knowledge_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_documents
    ADD CONSTRAINT knowledge_documents_pkey PRIMARY KEY (document_id);


--
-- Name: message_feedback message_feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_feedback
    ADD CONSTRAINT message_feedback_pkey PRIMARY KEY (feedback_id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (order_item_id);


--
-- Name: order_status_history order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (history_id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: promotion_products pk_promotion_products; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_products
    ADD CONSTRAINT pk_promotion_products PRIMARY KEY (promotion_id, product_id);


--
-- Name: role_permissions pk_role_permissions; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id);


--
-- Name: user_roles pk_user_roles; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id);


--
-- Name: product_images product_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT product_images_pkey PRIMARY KEY (image_id);


--
-- Name: product_semantic_index product_semantic_index_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_semantic_index
    ADD CONSTRAINT product_semantic_index_pkey PRIMARY KEY (product_id);


--
-- Name: product_variants product_variants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT product_variants_pkey PRIMARY KEY (variant_id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (product_id);


--
-- Name: promotions promotions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_pkey PRIMARY KEY (promotion_id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (refresh_token_id);


--
-- Name: return_exchange_items return_exchange_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_exchange_items
    ADD CONSTRAINT return_exchange_items_pkey PRIMARY KEY (exchange_item_id);


--
-- Name: return_items return_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_pkey PRIMARY KEY (return_item_id);


--
-- Name: return_requests return_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT return_requests_pkey PRIMARY KEY (return_id);


--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (review_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: serial_numbers serial_numbers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT serial_numbers_pkey PRIMARY KEY (serial_id);


--
-- Name: shipments shipments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_pkey PRIMARY KEY (shipment_id);


--
-- Name: specifications specifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.specifications
    ADD CONSTRAINT specifications_pkey PRIMARY KEY (specification_id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (supplier_id);


--
-- Name: brands uq_brands_brand_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brands
    ADD CONSTRAINT uq_brands_brand_name UNIQUE (brand_name);


--
-- Name: cart_items uq_cart_items_cart_variant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT uq_cart_items_cart_variant UNIQUE (cart_id, variant_id);


--
-- Name: carts uq_carts_customer_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT uq_carts_customer_id UNIQUE (customer_id);


--
-- Name: customers uq_customers_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uq_customers_user_id UNIQUE (user_id);


--
-- Name: employees uq_employees_employee_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT uq_employees_employee_code UNIQUE (employee_code);


--
-- Name: employees uq_employees_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT uq_employees_user_id UNIQUE (user_id);


--
-- Name: goods_receipts uq_goods_receipts_receipt_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT uq_goods_receipts_receipt_code UNIQUE (receipt_code);


--
-- Name: imei uq_imei_imei_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imei
    ADD CONSTRAINT uq_imei_imei_number UNIQUE (imei_number);


--
-- Name: installment_payments uq_installment_payments_payment_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_payments
    ADD CONSTRAINT uq_installment_payments_payment_id UNIQUE (payment_id);


--
-- Name: installment_providers uq_installment_providers_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_providers
    ADD CONSTRAINT uq_installment_providers_code UNIQUE (provider_code);


--
-- Name: inventory uq_inventory_warehouse_variant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT uq_inventory_warehouse_variant UNIQUE (warehouse_id, variant_id);


--
-- Name: invoices uq_invoices_invoice_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT uq_invoices_invoice_number UNIQUE (invoice_number);


--
-- Name: invoices uq_invoices_order_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT uq_invoices_order_id UNIQUE (order_id);


--
-- Name: knowledge_chunks uq_knowledge_chunks_document_index; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT uq_knowledge_chunks_document_index UNIQUE (document_id, chunk_index);


--
-- Name: message_feedback uq_message_feedback_history_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_feedback
    ADD CONSTRAINT uq_message_feedback_history_id UNIQUE (history_id);


--
-- Name: order_items uq_order_items_serial_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT uq_order_items_serial_id UNIQUE (serial_id);


--
-- Name: orders uq_orders_order_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uq_orders_order_code UNIQUE (order_code);


--
-- Name: payments uq_payments_transaction_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT uq_payments_transaction_code UNIQUE (transaction_code);


--
-- Name: permissions uq_permissions_permission_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT uq_permissions_permission_name UNIQUE (permission_name);


--
-- Name: product_variants uq_product_variants_sku; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT uq_product_variants_sku UNIQUE (sku);


--
-- Name: goods_receipt_item_device_imeis uq_receipt_device_imeis_device_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_device_imeis
    ADD CONSTRAINT uq_receipt_device_imeis_device_number UNIQUE (receipt_device_id, imei_number);


--
-- Name: goods_receipt_item_devices uq_receipt_item_devices_item_serial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_devices
    ADD CONSTRAINT uq_receipt_item_devices_item_serial UNIQUE (receipt_item_id, serial_number);


--
-- Name: refresh_tokens uq_refresh_tokens_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);


--
-- Name: return_exchange_items uq_return_exchange_items_return_variant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_exchange_items
    ADD CONSTRAINT uq_return_exchange_items_return_variant UNIQUE (return_id, variant_id);


--
-- Name: return_requests uq_return_requests_exchange_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT uq_return_requests_exchange_order UNIQUE (exchange_order_id);


--
-- Name: return_requests uq_return_requests_return_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT uq_return_requests_return_code UNIQUE (return_code);


--
-- Name: reviews uq_reviews_customer_order_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT uq_reviews_customer_order_item UNIQUE (customer_id, order_item_id);


--
-- Name: roles uq_roles_role_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uq_roles_role_name UNIQUE (role_name);


--
-- Name: serial_numbers uq_serial_numbers_receipt_device; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT uq_serial_numbers_receipt_device UNIQUE (receipt_device_id);


--
-- Name: serial_numbers uq_serial_numbers_serial_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT uq_serial_numbers_serial_number UNIQUE (serial_number);


--
-- Name: shipments uq_shipments_tracking_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT uq_shipments_tracking_number UNIQUE (tracking_number);


--
-- Name: suppliers uq_suppliers_supplier_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT uq_suppliers_supplier_code UNIQUE (supplier_code);


--
-- Name: user_roles uq_user_roles_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT uq_user_roles_user_id UNIQUE (user_id);


--
-- Name: users uq_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_email UNIQUE (email);


--
-- Name: users uq_users_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_username UNIQUE (username);


--
-- Name: warranties uq_warranties_serial_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranties
    ADD CONSTRAINT uq_warranties_serial_id UNIQUE (serial_id);


--
-- Name: warranty_tickets uq_warranty_tickets_ticket_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT uq_warranty_tickets_ticket_code UNIQUE (ticket_code);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (warehouse_id);


--
-- Name: warranties warranties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranties
    ADD CONSTRAINT warranties_pkey PRIMARY KEY (warranty_id);


--
-- Name: warranty_tickets warranty_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT warranty_tickets_pkey PRIMARY KEY (ticket_id);


--
-- Name: idx_brands_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_brands_status ON public.brands USING btree (status);


--
-- Name: idx_cart_items_cart_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cart_items_cart_id ON public.cart_items USING btree (cart_id);


--
-- Name: idx_cart_items_variant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cart_items_variant_id ON public.cart_items USING btree (variant_id);


--
-- Name: idx_carts_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carts_warehouse_id ON public.carts USING btree (warehouse_id);


--
-- Name: idx_categories_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_parent_id ON public.categories USING btree (parent_id);


--
-- Name: idx_categories_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_status ON public.categories USING btree (status);


--
-- Name: idx_chat_history_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_created_at ON public.chat_history USING btree (created_at);


--
-- Name: idx_chat_history_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_customer_id ON public.chat_history USING btree (customer_id);


--
-- Name: idx_chat_history_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_employee_id ON public.chat_history USING btree (employee_id);


--
-- Name: idx_chat_history_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_session_id ON public.chat_history USING btree (session_id);


--
-- Name: idx_chat_message_retrievals_chunk_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_retrievals_chunk_id ON public.chat_message_retrievals USING btree (chunk_id);


--
-- Name: idx_chat_message_retrievals_history_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_retrievals_history_id ON public.chat_message_retrievals USING btree (history_id);


--
-- Name: idx_chat_sessions_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_sessions_customer_id ON public.chat_sessions USING btree (customer_id);


--
-- Name: idx_chat_sessions_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_sessions_employee_id ON public.chat_sessions USING btree (employee_id);


--
-- Name: idx_chat_sessions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_sessions_status ON public.chat_sessions USING btree (status);


--
-- Name: idx_chatbot_qna_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_qna_category ON public.chatbot_qna USING btree (category);


--
-- Name: idx_chatbot_qna_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_qna_employee_id ON public.chatbot_qna USING btree (employee_id);


--
-- Name: idx_chatbot_qna_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_qna_status ON public.chatbot_qna USING btree (status);


--
-- Name: idx_chatbot_scripts_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_scripts_employee_id ON public.chatbot_scripts USING btree (employee_id);


--
-- Name: idx_chatbot_scripts_priority; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_scripts_priority ON public.chatbot_scripts USING btree (priority);


--
-- Name: idx_chatbot_scripts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_scripts_status ON public.chatbot_scripts USING btree (status);


--
-- Name: idx_customer_addresses_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_addresses_customer_id ON public.customer_addresses USING btree (customer_id);


--
-- Name: idx_customers_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customers_user_id ON public.customers USING btree (user_id);


--
-- Name: idx_employees_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employees_user_id ON public.employees USING btree (user_id);


--
-- Name: idx_employees_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employees_warehouse_id ON public.employees USING btree (warehouse_id);


--
-- Name: idx_goods_receipt_items_receipt_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipt_items_receipt_id ON public.goods_receipt_items USING btree (receipt_id);


--
-- Name: idx_goods_receipt_items_variant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipt_items_variant_id ON public.goods_receipt_items USING btree (variant_id);


--
-- Name: idx_goods_receipts_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipts_employee_id ON public.goods_receipts USING btree (employee_id);


--
-- Name: idx_goods_receipts_receipt_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipts_receipt_date ON public.goods_receipts USING btree (receipt_date);


--
-- Name: idx_goods_receipts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipts_status ON public.goods_receipts USING btree (status);


--
-- Name: idx_goods_receipts_supplier_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipts_supplier_id ON public.goods_receipts USING btree (supplier_id);


--
-- Name: idx_goods_receipts_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_goods_receipts_warehouse_id ON public.goods_receipts USING btree (warehouse_id);


--
-- Name: idx_imei_serial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_imei_serial_id ON public.imei USING btree (serial_id);


--
-- Name: idx_installment_payments_provider_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_installment_payments_provider_id ON public.installment_payments USING btree (provider_id);


--
-- Name: idx_inventory_variant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_variant_id ON public.inventory USING btree (variant_id);


--
-- Name: idx_inventory_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_warehouse_id ON public.inventory USING btree (warehouse_id);


--
-- Name: idx_knowledge_chunks_document_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_chunks_document_id ON public.knowledge_chunks USING btree (document_id);


--
-- Name: idx_knowledge_documents_document_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_documents_document_type ON public.knowledge_documents USING btree (document_type);


--
-- Name: idx_knowledge_documents_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_documents_employee_id ON public.knowledge_documents USING btree (employee_id);


--
-- Name: idx_knowledge_documents_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_documents_status ON public.knowledge_documents USING btree (status);


--
-- Name: idx_message_feedback_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_message_feedback_customer_id ON public.message_feedback USING btree (customer_id);


--
-- Name: idx_order_items_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_order_id ON public.order_items USING btree (order_id);


--
-- Name: idx_order_items_variant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_variant_id ON public.order_items USING btree (variant_id);


--
-- Name: idx_order_status_history_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_status_history_order ON public.order_status_history USING btree (order_id, changed_at);


--
-- Name: idx_order_status_history_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_status_history_user ON public.order_status_history USING btree (changed_by_user_id);


--
-- Name: idx_orders_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_customer_id ON public.orders USING btree (customer_id);


--
-- Name: idx_orders_order_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_order_date ON public.orders USING btree (order_date);


--
-- Name: idx_orders_sales_channel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_sales_channel ON public.orders USING btree (sales_channel);


--
-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status ON public.orders USING btree (status);


--
-- Name: idx_orders_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_warehouse_id ON public.orders USING btree (warehouse_id);


--
-- Name: idx_payments_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_order_id ON public.payments USING btree (order_id);


--
-- Name: idx_payments_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_status ON public.payments USING btree (status);


--
-- Name: idx_product_images_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_images_product_id ON public.product_images USING btree (product_id);


--
-- Name: idx_product_variants_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_variants_product_id ON public.product_variants USING btree (product_id);


--
-- Name: idx_product_variants_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_variants_status ON public.product_variants USING btree (status);


--
-- Name: idx_products_brand_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_brand_id ON public.products USING btree (brand_id);


--
-- Name: idx_products_category_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_category_id ON public.products USING btree (category_id);


--
-- Name: idx_products_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_status ON public.products USING btree (status);


--
-- Name: idx_promotion_products_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotion_products_product_id ON public.promotion_products USING btree (product_id);


--
-- Name: idx_promotions_end_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotions_end_date ON public.promotions USING btree (end_date);


--
-- Name: idx_promotions_start_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotions_start_date ON public.promotions USING btree (start_date);


--
-- Name: idx_promotions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotions_status ON public.promotions USING btree (status);


--
-- Name: idx_receipt_device_imeis_device; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receipt_device_imeis_device ON public.goods_receipt_item_device_imeis USING btree (receipt_device_id);


--
-- Name: idx_receipt_item_devices_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receipt_item_devices_item ON public.goods_receipt_item_devices USING btree (receipt_item_id);


--
-- Name: idx_refresh_tokens_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_expires_at ON public.refresh_tokens USING btree (expires_at);


--
-- Name: idx_refresh_tokens_replaced_by_token_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_replaced_by_token_id ON public.refresh_tokens USING btree (replaced_by_token_id);


--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_return_exchange_items_return_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_exchange_items_return_id ON public.return_exchange_items USING btree (return_id);


--
-- Name: idx_return_items_order_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_items_order_item_id ON public.return_items USING btree (order_item_id);


--
-- Name: idx_return_items_return_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_items_return_id ON public.return_items USING btree (return_id);


--
-- Name: idx_return_items_serial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_items_serial_id ON public.return_items USING btree (serial_id);


--
-- Name: idx_return_requests_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_requests_customer_id ON public.return_requests USING btree (customer_id);


--
-- Name: idx_return_requests_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_requests_employee_id ON public.return_requests USING btree (employee_id);


--
-- Name: idx_return_requests_exchange_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_requests_exchange_order_id ON public.return_requests USING btree (exchange_order_id);


--
-- Name: idx_return_requests_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_requests_order_id ON public.return_requests USING btree (order_id);


--
-- Name: idx_return_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_requests_status ON public.return_requests USING btree (status);


--
-- Name: idx_reviews_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_customer_id ON public.reviews USING btree (customer_id);


--
-- Name: idx_reviews_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_employee_id ON public.reviews USING btree (employee_id);


--
-- Name: idx_reviews_order_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_order_item_id ON public.reviews USING btree (order_item_id);


--
-- Name: idx_reviews_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_product_id ON public.reviews USING btree (product_id);


--
-- Name: idx_reviews_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_status ON public.reviews USING btree (status);


--
-- Name: idx_role_permissions_permission_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_permissions_permission_id ON public.role_permissions USING btree (permission_id);


--
-- Name: idx_serial_numbers_variant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_serial_numbers_variant_id ON public.serial_numbers USING btree (variant_id);


--
-- Name: idx_serial_numbers_warehouse_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_serial_numbers_warehouse_id ON public.serial_numbers USING btree (warehouse_id);


--
-- Name: idx_shipments_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipments_employee_id ON public.shipments USING btree (employee_id);


--
-- Name: idx_shipments_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipments_order_id ON public.shipments USING btree (order_id);


--
-- Name: idx_shipments_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipments_status ON public.shipments USING btree (status);


--
-- Name: idx_specifications_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_specifications_product_id ON public.specifications USING btree (product_id);


--
-- Name: idx_suppliers_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_suppliers_status ON public.suppliers USING btree (status);


--
-- Name: idx_user_roles_role_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_roles_role_id ON public.user_roles USING btree (role_id);


--
-- Name: idx_warehouses_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouses_status ON public.warehouses USING btree (status);


--
-- Name: idx_warranties_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranties_customer_id ON public.warranties USING btree (customer_id);


--
-- Name: idx_warranties_order_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranties_order_item_id ON public.warranties USING btree (order_item_id);


--
-- Name: idx_warranty_tickets_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranty_tickets_customer_id ON public.warranty_tickets USING btree (customer_id);


--
-- Name: idx_warranty_tickets_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranty_tickets_employee_id ON public.warranty_tickets USING btree (employee_id);


--
-- Name: idx_warranty_tickets_serial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranty_tickets_serial_id ON public.warranty_tickets USING btree (serial_id);


--
-- Name: idx_warranty_tickets_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranty_tickets_status ON public.warranty_tickets USING btree (status);


--
-- Name: idx_warranty_tickets_warranty_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warranty_tickets_warranty_id ON public.warranty_tickets USING btree (warranty_id);


--
-- Name: uq_customer_addresses_one_default; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customer_addresses_one_default ON public.customer_addresses USING btree (customer_id) WHERE (is_default = true);


--
-- Name: uq_customers_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_phone ON public.customers USING btree (phone) WHERE (phone IS NOT NULL);


--
-- Name: uq_product_images_primary; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_product_images_primary ON public.product_images USING btree (product_id) WHERE (is_primary = true);


--
-- Name: serial_numbers trg_serial_numbers_match_receipt_device; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_serial_numbers_match_receipt_device BEFORE INSERT OR UPDATE OF receipt_device_id, variant_id, serial_number ON public.serial_numbers FOR EACH ROW WHEN ((new.receipt_device_id IS NOT NULL)) EXECUTE FUNCTION public.fn_serial_numbers_match_receipt_device();


--
-- Name: customer_addresses customer_addresses_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: cart_items fk_cart_items_cart; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES public.carts(cart_id) ON DELETE RESTRICT;


--
-- Name: cart_items fk_cart_items_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: carts fk_carts_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT fk_carts_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: carts fk_carts_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT fk_carts_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: categories fk_categories_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES public.categories(category_id) ON DELETE RESTRICT;


--
-- Name: chat_history fk_chat_history_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history
    ADD CONSTRAINT fk_chat_history_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: chat_history fk_chat_history_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history
    ADD CONSTRAINT fk_chat_history_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: chat_history fk_chat_history_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history
    ADD CONSTRAINT fk_chat_history_session FOREIGN KEY (session_id) REFERENCES public.chat_sessions(session_id) ON DELETE RESTRICT;


--
-- Name: chat_message_retrievals fk_chat_message_retrievals_chunk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message_retrievals
    ADD CONSTRAINT fk_chat_message_retrievals_chunk FOREIGN KEY (chunk_id) REFERENCES public.knowledge_chunks(chunk_id) ON DELETE RESTRICT;


--
-- Name: chat_message_retrievals fk_chat_message_retrievals_history; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message_retrievals
    ADD CONSTRAINT fk_chat_message_retrievals_history FOREIGN KEY (history_id) REFERENCES public.chat_history(history_id) ON DELETE RESTRICT;


--
-- Name: chat_sessions fk_chat_sessions_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_sessions
    ADD CONSTRAINT fk_chat_sessions_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: chat_sessions fk_chat_sessions_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_sessions
    ADD CONSTRAINT fk_chat_sessions_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: chatbot_qna fk_chatbot_qna_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_qna
    ADD CONSTRAINT fk_chatbot_qna_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: chatbot_scripts fk_chatbot_scripts_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_scripts
    ADD CONSTRAINT fk_chatbot_scripts_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: customers fk_customers_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: employees fk_employees_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: employees fk_employees_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk_employees_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: goods_receipt_items fk_goods_receipt_items_receipt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT fk_goods_receipt_items_receipt FOREIGN KEY (receipt_id) REFERENCES public.goods_receipts(receipt_id) ON DELETE RESTRICT;


--
-- Name: goods_receipt_items fk_goods_receipt_items_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT fk_goods_receipt_items_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: goods_receipts fk_goods_receipts_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT fk_goods_receipts_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: goods_receipts fk_goods_receipts_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT fk_goods_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(supplier_id) ON DELETE RESTRICT;


--
-- Name: goods_receipts fk_goods_receipts_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT fk_goods_receipts_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: imei fk_imei_serial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imei
    ADD CONSTRAINT fk_imei_serial FOREIGN KEY (serial_id) REFERENCES public.serial_numbers(serial_id) ON DELETE RESTRICT;


--
-- Name: installment_payments fk_installment_payments_payment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_payments
    ADD CONSTRAINT fk_installment_payments_payment FOREIGN KEY (payment_id) REFERENCES public.payments(payment_id) ON DELETE RESTRICT;


--
-- Name: installment_payments fk_installment_payments_provider; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment_payments
    ADD CONSTRAINT fk_installment_payments_provider FOREIGN KEY (provider_id) REFERENCES public.installment_providers(provider_id) ON DELETE RESTRICT;


--
-- Name: inventory fk_inventory_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: inventory fk_inventory_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: invoices fk_invoices_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: knowledge_chunks fk_knowledge_chunks_document; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunks_document FOREIGN KEY (document_id) REFERENCES public.knowledge_documents(document_id) ON DELETE RESTRICT;


--
-- Name: knowledge_documents fk_knowledge_documents_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_documents
    ADD CONSTRAINT fk_knowledge_documents_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: message_feedback fk_message_feedback_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_feedback
    ADD CONSTRAINT fk_message_feedback_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: message_feedback fk_message_feedback_history; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_feedback
    ADD CONSTRAINT fk_message_feedback_history FOREIGN KEY (history_id) REFERENCES public.chat_history(history_id) ON DELETE RESTRICT;


--
-- Name: order_items fk_order_items_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: order_items fk_order_items_serial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk_order_items_serial FOREIGN KEY (serial_id) REFERENCES public.serial_numbers(serial_id) ON DELETE RESTRICT;


--
-- Name: order_items fk_order_items_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: order_status_history fk_order_status_history_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: order_status_history fk_order_status_history_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT fk_order_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: orders fk_orders_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: orders fk_orders_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: payments fk_payments_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: product_images fk_product_images_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT;


--
-- Name: product_variants fk_product_variants_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT;


--
-- Name: products fk_products_brand; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES public.brands(brand_id) ON DELETE RESTRICT;


--
-- Name: products fk_products_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES public.categories(category_id) ON DELETE RESTRICT;


--
-- Name: promotion_products fk_promotion_products_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_products
    ADD CONSTRAINT fk_promotion_products_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT;


--
-- Name: promotion_products fk_promotion_products_promotion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_products
    ADD CONSTRAINT fk_promotion_products_promotion FOREIGN KEY (promotion_id) REFERENCES public.promotions(promotion_id) ON DELETE RESTRICT;


--
-- Name: goods_receipt_item_device_imeis fk_receipt_device_imeis_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_device_imeis
    ADD CONSTRAINT fk_receipt_device_imeis_device FOREIGN KEY (receipt_device_id) REFERENCES public.goods_receipt_item_devices(receipt_device_id) ON DELETE CASCADE;


--
-- Name: goods_receipt_item_devices fk_receipt_item_devices_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_item_devices
    ADD CONSTRAINT fk_receipt_item_devices_item FOREIGN KEY (receipt_item_id) REFERENCES public.goods_receipt_items(receipt_item_id) ON DELETE CASCADE;


--
-- Name: refresh_tokens fk_refresh_tokens_replaced_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_token_id) REFERENCES public.refresh_tokens(refresh_token_id) ON DELETE RESTRICT;


--
-- Name: refresh_tokens fk_refresh_tokens_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: return_exchange_items fk_return_exchange_items_return; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_exchange_items
    ADD CONSTRAINT fk_return_exchange_items_return FOREIGN KEY (return_id) REFERENCES public.return_requests(return_id) ON DELETE RESTRICT;


--
-- Name: return_exchange_items fk_return_exchange_items_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_exchange_items
    ADD CONSTRAINT fk_return_exchange_items_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: return_items fk_return_items_order_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT fk_return_items_order_item FOREIGN KEY (order_item_id) REFERENCES public.order_items(order_item_id) ON DELETE RESTRICT;


--
-- Name: return_items fk_return_items_return; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES public.return_requests(return_id) ON DELETE RESTRICT;


--
-- Name: return_items fk_return_items_serial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT fk_return_items_serial FOREIGN KEY (serial_id) REFERENCES public.serial_numbers(serial_id) ON DELETE RESTRICT;


--
-- Name: return_requests fk_return_requests_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT fk_return_requests_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: return_requests fk_return_requests_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT fk_return_requests_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: return_requests fk_return_requests_exchange_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT fk_return_requests_exchange_order FOREIGN KEY (exchange_order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: return_requests fk_return_requests_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_requests
    ADD CONSTRAINT fk_return_requests_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: reviews fk_reviews_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: reviews fk_reviews_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: reviews fk_reviews_order_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES public.order_items(order_item_id) ON DELETE RESTRICT;


--
-- Name: reviews fk_reviews_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT;


--
-- Name: role_permissions fk_role_permissions_permission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE RESTRICT;


--
-- Name: role_permissions fk_role_permissions_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE RESTRICT;


--
-- Name: serial_numbers fk_serial_numbers_receipt_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT fk_serial_numbers_receipt_device FOREIGN KEY (receipt_device_id) REFERENCES public.goods_receipt_item_devices(receipt_device_id) ON DELETE RESTRICT;


--
-- Name: serial_numbers fk_serial_numbers_variant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT fk_serial_numbers_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT;


--
-- Name: serial_numbers fk_serial_numbers_warehouse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serial_numbers
    ADD CONSTRAINT fk_serial_numbers_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: shipments fk_shipments_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT fk_shipments_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: shipments fk_shipments_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT;


--
-- Name: specifications fk_specifications_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.specifications
    ADD CONSTRAINT fk_specifications_product FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT;


--
-- Name: user_roles fk_user_roles_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE RESTRICT;


--
-- Name: user_roles fk_user_roles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: warranties fk_warranties_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranties
    ADD CONSTRAINT fk_warranties_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: warranties fk_warranties_order_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranties
    ADD CONSTRAINT fk_warranties_order_item FOREIGN KEY (order_item_id) REFERENCES public.order_items(order_item_id) ON DELETE RESTRICT;


--
-- Name: warranties fk_warranties_serial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranties
    ADD CONSTRAINT fk_warranties_serial FOREIGN KEY (serial_id) REFERENCES public.serial_numbers(serial_id) ON DELETE RESTRICT;


--
-- Name: warranty_tickets fk_warranty_tickets_customer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT fk_warranty_tickets_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE RESTRICT;


--
-- Name: warranty_tickets fk_warranty_tickets_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT fk_warranty_tickets_employee FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id) ON DELETE RESTRICT;


--
-- Name: warranty_tickets fk_warranty_tickets_serial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT fk_warranty_tickets_serial FOREIGN KEY (serial_id) REFERENCES public.serial_numbers(serial_id) ON DELETE RESTRICT;


--
-- Name: warranty_tickets fk_warranty_tickets_warranty; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty_tickets
    ADD CONSTRAINT fk_warranty_tickets_warranty FOREIGN KEY (warranty_id) REFERENCES public.warranties(warranty_id) ON DELETE RESTRICT;


--
-- Name: product_semantic_index product_semantic_index_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_semantic_index
    ADD CONSTRAINT product_semantic_index_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
