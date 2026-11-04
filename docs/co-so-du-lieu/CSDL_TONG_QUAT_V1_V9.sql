-- =====================================================================
-- CƠ SỞ DỮ LIỆU TỔNG QUÁT – HỆ THỐNG QUẢN LÝ CỬA HÀNG MUA BÁN THIẾT BỊ ĐIỆN TỬ
-- ---------------------------------------------------------------------
-- Sinh tự động ngày 03/11/2026 từ toàn bộ migration Flyway
-- backend/src/main/resources/db/migration (V1 → V9):
--   V1  Tạo toàn bộ CSDL (người dùng, sản phẩm, kho, đơn hàng, hậu mãi, chatbot, RAG)
--   V2  Bảng refresh_tokens
--   V3  Theo dõi Serial/IMEI trên biến thể và thiết bị trong phiếu nhập
--   V4  Chi nhánh (kho) cho giỏ hàng và đơn hàng
--   V5  Bổ sung chi nhánh cho dữ liệu cũ (chỉ cập nhật dữ liệu)
--   V6  Thời hạn bảo hành theo biến thể
--   V7  Chỉ mục ngữ nghĩa sản phẩm (pgvector)
--   V8  Sổ địa chỉ khách hàng
--   V9  Chức danh nhân viên (chỉ thêm dữ liệu vai trò)
--
-- Kết quả: 49 bảng, 312 cột, 75 khóa ngoại, 177 chỉ mục; extension vector 0.8.7.
-- Dữ liệu nền kèm theo (do migration tạo): 8 vai trò, 16 quyền, 31 dòng gán quyền.
-- Không chứa dữ liệu nghiệp vụ, tài khoản hay mật khẩu.
--
-- Cách dùng: chạy trên một CSDL PostgreSQL 16 trống, có sẵn extension pgvector
-- (ví dụ image pgvector/pgvector:pg16):  psql -d <ten_csdl> -f CSDL_TONG_QUAT_V1_V9.sql
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: brands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.brands (
    brand_id uuid DEFAULT gen_random_uuid() NOT NULL,
    brand_name character varying(255) NOT NULL,
    description text,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL
);


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
-- Name: carts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carts (
    cart_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(30) NOT NULL,
    warehouse_id uuid
);


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    category_id uuid DEFAULT gen_random_uuid() NOT NULL,
    parent_id uuid,
    category_name character varying(255) NOT NULL,
    description text,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL
);


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
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


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
-- Name: chat_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_sessions (
    session_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    employee_id uuid,
    started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ended_at timestamp with time zone,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_chat_sessions_end_date CHECK (((ended_at IS NULL) OR (ended_at >= started_at)))
);


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
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


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
    CONSTRAINT ck_chatbot_scripts_priority CHECK ((priority >= 0))
);


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
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    customer_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    full_name character varying(255) NOT NULL,
    address character varying(500),
    loyalty_point integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_customers_loyalty_point CHECK ((loyalty_point >= 0))
);


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employees (
    employee_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    employee_code character varying(50) NOT NULL,
    full_name character varying(255) NOT NULL,
    "position" character varying(100)
);


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
-- Name: goods_receipt_item_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipt_item_devices (
    receipt_device_id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_item_id uuid NOT NULL,
    serial_number character varying(255) NOT NULL,
    CONSTRAINT ck_receipt_item_devices_serial_not_blank CHECK ((btrim((serial_number)::text) <> ''::text))
);


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
    CONSTRAINT ck_goods_receipts_total_amount CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: imei; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imei (
    imei_id uuid DEFAULT gen_random_uuid() NOT NULL,
    serial_id uuid NOT NULL,
    imei_number character varying(50) NOT NULL
);


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
    CONSTRAINT ck_installment_remaining_amount CHECK ((remaining_amount >= (0)::numeric)),
    CONSTRAINT ck_installment_term_months CHECK ((term_months > 0)),
    CONSTRAINT ck_installment_total_amount CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: installment_providers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.installment_providers (
    provider_id uuid DEFAULT gen_random_uuid() NOT NULL,
    provider_name character varying(255) NOT NULL,
    provider_code character varying(100) NOT NULL,
    contact_phone character varying(30),
    contact_email character varying(255),
    status character varying(30) NOT NULL
);


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
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    invoice_id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    invoice_number character varying(100) NOT NULL,
    issued_date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_invoices_total_amount CHECK ((total_amount >= (0)::numeric))
);


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
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


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
    CONSTRAINT ck_order_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_order_items_serial_quantity CHECK (((serial_id IS NULL) OR (quantity = 1))),
    CONSTRAINT ck_order_items_unit_price CHECK ((unit_price >= (0)::numeric))
);


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
    CONSTRAINT ck_orders_discount_amount CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT ck_orders_discount_not_exceed_subtotal CHECK ((discount_amount <= subtotal)),
    CONSTRAINT ck_orders_shipping_fee CHECK ((shipping_fee >= (0)::numeric)),
    CONSTRAINT ck_orders_subtotal CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT ck_orders_total_amount CHECK ((total_amount >= (0)::numeric))
);


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
    CONSTRAINT ck_payments_amount CHECK ((amount >= (0)::numeric))
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id uuid DEFAULT gen_random_uuid() NOT NULL,
    permission_name character varying(150) NOT NULL,
    description character varying(255)
);


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
    CONSTRAINT ck_product_variants_tracking_type CHECK (((tracking_type)::text = ANY ((ARRAY['NONE'::character varying, 'SERIAL'::character varying, 'IMEI'::character varying])::text[]))),
    CONSTRAINT ck_product_variants_warranty_months CHECK ((warranty_months >= 0))
);


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
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: promotion_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotion_products (
    promotion_id uuid NOT NULL,
    product_id uuid NOT NULL
);


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
    CONSTRAINT ck_promotions_discount_value CHECK ((discount_value >= (0)::numeric))
);


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
-- Name: return_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_items (
    return_item_id uuid DEFAULT gen_random_uuid() NOT NULL,
    return_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    serial_id uuid,
    quantity integer NOT NULL,
    reason character varying(500),
    CONSTRAINT ck_return_items_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_return_items_serial_quantity CHECK (((serial_id IS NULL) OR (quantity = 1)))
);


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
    CONSTRAINT ck_return_requests_processed_date CHECK (((processed_at IS NULL) OR (processed_at >= request_date))),
    CONSTRAINT ck_return_requests_refund_amount CHECK ((refund_amount >= (0)::numeric))
);


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
    CONSTRAINT ck_reviews_replied_date CHECK (((replied_at IS NULL) OR (replied_at >= created_at)))
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id uuid DEFAULT gen_random_uuid() NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(255)
);


--
-- Name: serial_numbers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.serial_numbers (
    serial_id uuid DEFAULT gen_random_uuid() NOT NULL,
    variant_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    serial_number character varying(255) NOT NULL,
    status character varying(30) NOT NULL,
    CONSTRAINT ck_serial_numbers_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RESERVED'::character varying, 'SOLD'::character varying, 'RETURNED'::character varying, 'DEFECTIVE'::character varying])::text[])))
);


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
    CONSTRAINT ck_shipments_delivery_date CHECK (((delivered_date IS NULL) OR (shipped_date IS NULL) OR (delivered_date >= shipped_date)))
);


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
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    supplier_id uuid DEFAULT gen_random_uuid() NOT NULL,
    supplier_code character varying(50) NOT NULL,
    supplier_name character varying(255) NOT NULL,
    phone character varying(30),
    email character varying(255),
    address character varying(500),
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL
);


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
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    warehouse_id uuid DEFAULT gen_random_uuid() NOT NULL,
    warehouse_name character varying(255) NOT NULL,
    address character varying(500),
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL
);


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
    CONSTRAINT ck_warranties_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying])::text[])))
);


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
    CONSTRAINT ck_warranty_tickets_resolved_date CHECK (((resolved_at IS NULL) OR (resolved_at >= created_at))),
    CONSTRAINT ck_warranty_tickets_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Data for Name: brands; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: cart_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: carts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: chat_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: chat_message_retrievals; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: chat_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: chatbot_qna; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: chatbot_scripts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: customer_addresses; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: employees; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: goods_receipt_item_device_imeis; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: goods_receipt_item_devices; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: goods_receipt_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: goods_receipts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: imei; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: installment_payments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: installment_providers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: inventory; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: invoices; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: knowledge_chunks; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: knowledge_documents; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: message_feedback; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: order_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('46de84a0-770d-4e01-81e1-3b3172b459cd', 'USER_VIEW', 'Xem thông tin người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('45ed1062-f416-4328-abd6-6205776fedc8', 'USER_CREATE', 'Tạo người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('51c2301f-9b2d-4cf7-b9b1-14bc6162a3dd', 'USER_UPDATE', 'Cập nhật thông tin người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('145a75d6-aa0b-4b2c-a89c-3595a3346814', 'USER_DISABLE', 'Vô hiệu hóa người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('278555e5-befe-4faf-a1f2-62f6d6517cbd', 'USER_ROLE_VIEW', 'Xem vai trò được gán cho người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('24f8870a-fd9b-4d24-9579-f7a6b5d7c057', 'USER_ROLE_ASSIGN', 'Gán vai trò cho người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('a6e56269-dcc3-444d-b9de-b1e2a7ed02c0', 'USER_ROLE_REMOVE', 'Gỡ vai trò khỏi người dùng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('210ac599-94f1-4dae-a01f-180e3b0afbb2', 'ROLE_PERMISSION_VIEW', 'Xem quyền của vai trò');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('284dd2a6-9fde-4c10-8873-858c1fb06bcc', 'ROLE_PERMISSION_ASSIGN', 'Gán quyền cho vai trò');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('f4c7852b-302d-40fe-b017-8a588292bfeb', 'ROLE_PERMISSION_REMOVE', 'Gỡ quyền khỏi vai trò');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('de16ab3e-1180-4de2-b1f3-65b48b51914f', 'EMPLOYEE_VIEW', 'Xem thông tin nhân viên');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('ad1445f6-ea2a-4684-88fc-c3d484cfbf2e', 'EMPLOYEE_CREATE', 'Tạo hồ sơ nhân viên');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('ba189f36-c1b3-4682-a3c0-eafb5ff74f65', 'EMPLOYEE_UPDATE', 'Cập nhật hồ sơ nhân viên');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('d36d18d1-d937-449d-858d-b9622243f1b5', 'CUSTOMER_VIEW', 'Xem thông tin khách hàng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('84a4e57a-6b98-42f1-93b8-92a5a942cce9', 'CUSTOMER_CREATE', 'Tạo hồ sơ khách hàng');
INSERT INTO public.permissions (permission_id, permission_name, description) VALUES ('6cd92cfd-6c0e-49bf-864b-f99385ff6d07', 'CUSTOMER_UPDATE', 'Cập nhật thông tin khách hàng');


--
-- Data for Name: product_images; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: product_semantic_index; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: product_variants; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: products; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: promotion_products; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: promotions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: return_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: return_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: reviews; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '46de84a0-770d-4e01-81e1-3b3172b459cd');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '45ed1062-f416-4328-abd6-6205776fedc8');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '51c2301f-9b2d-4cf7-b9b1-14bc6162a3dd');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '145a75d6-aa0b-4b2c-a89c-3595a3346814');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '278555e5-befe-4faf-a1f2-62f6d6517cbd');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '24f8870a-fd9b-4d24-9579-f7a6b5d7c057');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'a6e56269-dcc3-444d-b9de-b1e2a7ed02c0');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '210ac599-94f1-4dae-a01f-180e3b0afbb2');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '284dd2a6-9fde-4c10-8873-858c1fb06bcc');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'f4c7852b-302d-40fe-b017-8a588292bfeb');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'ad1445f6-ea2a-4684-88fc-c3d484cfbf2e');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'ba189f36-c1b3-4682-a3c0-eafb5ff74f65');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'd36d18d1-d937-449d-858d-b9622243f1b5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '84a4e57a-6b98-42f1-93b8-92a5a942cce9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', '6cd92cfd-6c0e-49bf-864b-f99385ff6d07');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', '46de84a0-770d-4e01-81e1-3b3172b459cd');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', 'ad1445f6-ea2a-4684-88fc-c3d484cfbf2e');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', 'ba189f36-c1b3-4682-a3c0-eafb5ff74f65');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', 'd36d18d1-d937-449d-858d-b9622243f1b5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', '84a4e57a-6b98-42f1-93b8-92a5a942cce9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', '6cd92cfd-6c0e-49bf-864b-f99385ff6d07');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4551854f-64f0-4ab1-bf87-d0c9632009b0', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4551854f-64f0-4ab1-bf87-d0c9632009b0', 'd36d18d1-d937-449d-858d-b9622243f1b5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('7556ab28-f320-4dd0-9c8a-f931139dcf22', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('104f826a-7a3b-49b0-a308-1ec1e89f1f02', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('f0c36fb8-8315-4417-b753-789a65d17fc8', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('8a4ce806-d35e-46c5-9f49-12d9326ed6ff', 'de16ab3e-1180-4de2-b1f3-65b48b51914f');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('7556ab28-f320-4dd0-9c8a-f931139dcf22', 'd36d18d1-d937-449d-858d-b9622243f1b5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('f0c36fb8-8315-4417-b753-789a65d17fc8', 'd36d18d1-d937-449d-858d-b9622243f1b5');


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.roles (role_id, role_name, description) VALUES ('66cfccfa-d519-4eb8-bd28-e39aaa0731a0', 'ADMIN', 'Quản trị viên hệ thống');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('15db60e3-cde4-4032-91c6-6f308044f42c', 'MANAGER', 'Quản lý cửa hàng');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('4551854f-64f0-4ab1-bf87-d0c9632009b0', 'STAFF', 'Nhân viên cửa hàng');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('fdd20483-38b8-4fcc-b5b4-12c53023d9c0', 'CUSTOMER', 'Khách hàng');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('7556ab28-f320-4dd0-9c8a-f931139dcf22', 'SALES_STAFF', 'Nhân viên bán hàng');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('104f826a-7a3b-49b0-a308-1ec1e89f1f02', 'WAREHOUSE_KEEPER', 'Thủ kho');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('f0c36fb8-8315-4417-b753-789a65d17fc8', 'WARRANTY_TECHNICIAN', 'Nhân viên kỹ thuật/bảo hành');
INSERT INTO public.roles (role_id, role_name, description) VALUES ('8a4ce806-d35e-46c5-9f49-12d9326ed6ff', 'DELIVERY_STAFF', 'Nhân viên giao hàng');


--
-- Data for Name: serial_numbers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: shipments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: specifications; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: suppliers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: warehouses; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: warranties; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: warranty_tickets; Type: TABLE DATA; Schema: public; Owner: -
--



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
-- Name: idx_carts_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carts_customer_id ON public.carts USING btree (customer_id);


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
-- Name: idx_orders_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_customer_id ON public.orders USING btree (customer_id);


--
-- Name: idx_orders_order_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_order_date ON public.orders USING btree (order_date);


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
-- Name: uq_product_images_primary; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_product_images_primary ON public.product_images USING btree (product_id) WHERE (is_primary = true);


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
