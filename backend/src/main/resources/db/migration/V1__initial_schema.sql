-- =========================================================
-- V1 - INITIAL DATABASE SCHEMA
-- Project: QL_CHTBDT_Ecommerce
-- Database: PostgreSQL 16
--
-- Notes:
--   - UUID primary keys
--   - PostgreSQL generates UUID using gen_random_uuid()
--   - Money: NUMERIC(15,2)
--   - Date/time: TIMESTAMPTZ
--   - Business status fields use VARCHAR
--   - RAG embedding_vector is intentionally NOT created
--     because the embedding model/dimension is not selected yet.
--
-- IMPORTANT:
--   - Project policy: NO HARD DELETE for business data.
--   - Therefore, all foreign keys use ON DELETE RESTRICT.
--   - Lifecycle removal should be handled by status / business state.
-- =========================================================


-- =========================================================
-- 01. USER & ACCESS CONTROL
-- =========================================================

CREATE TABLE users (
                       user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       username VARCHAR(255) NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       phone VARCHAR(30),
                       status VARCHAR(30) NOT NULL,

                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uq_users_username
                           UNIQUE (username),

                       CONSTRAINT uq_users_email
                           UNIQUE (email)
);


CREATE TABLE roles (
                       role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       role_name VARCHAR(100) NOT NULL,
                       description VARCHAR(255),

                       CONSTRAINT uq_roles_role_name
                           UNIQUE (role_name)
);


CREATE TABLE permissions (
                             permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             permission_name VARCHAR(150) NOT NULL,
                             description VARCHAR(255),

                             CONSTRAINT uq_permissions_permission_name
                                 UNIQUE (permission_name)
);


CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role_id UUID NOT NULL,

                            CONSTRAINT pk_user_roles
                                PRIMARY KEY (user_id, role_id),

                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users (user_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_id)
                                    REFERENCES roles (role_id)
                                    ON DELETE RESTRICT
);


CREATE TABLE role_permissions (
                                  role_id UUID NOT NULL,
                                  permission_id UUID NOT NULL,

                                  CONSTRAINT pk_role_permissions
                                      PRIMARY KEY (role_id, permission_id),

                                  CONSTRAINT fk_role_permissions_role
                                      FOREIGN KEY (role_id)
                                          REFERENCES roles (role_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_role_permissions_permission
                                      FOREIGN KEY (permission_id)
                                          REFERENCES permissions (permission_id)
                                          ON DELETE RESTRICT
);


CREATE TABLE employees (
                           employee_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           user_id UUID NOT NULL,
                           employee_code VARCHAR(50) NOT NULL,
                           full_name VARCHAR(255) NOT NULL,
                           position VARCHAR(100),

                           CONSTRAINT uq_employees_user_id
                               UNIQUE (user_id),

                           CONSTRAINT uq_employees_employee_code
                               UNIQUE (employee_code),

                           CONSTRAINT fk_employees_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (user_id)
                                   ON DELETE RESTRICT
);


CREATE TABLE customers (
                           customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           user_id UUID NOT NULL,
                           full_name VARCHAR(255) NOT NULL,
                           address VARCHAR(500),
                           loyalty_point INTEGER NOT NULL DEFAULT 0,

                           CONSTRAINT uq_customers_user_id
                               UNIQUE (user_id),

                           CONSTRAINT ck_customers_loyalty_point
                               CHECK (loyalty_point >= 0),

                           CONSTRAINT fk_customers_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (user_id)
                                   ON DELETE RESTRICT
);


-- =========================================================
-- 02. PRODUCT CATALOG
-- =========================================================

CREATE TABLE categories (
                            category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                            parent_id UUID,
                            category_name VARCHAR(255) NOT NULL,
                            description TEXT,

                            CONSTRAINT fk_categories_parent
                                FOREIGN KEY (parent_id)
                                    REFERENCES categories (category_id)
                                    ON DELETE RESTRICT
);


CREATE TABLE brands (
                        brand_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                        brand_name VARCHAR(255) NOT NULL,
                        description TEXT,

                        CONSTRAINT uq_brands_brand_name
                            UNIQUE (brand_name)
);


CREATE TABLE products (
                          product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          category_id UUID NOT NULL,
                          brand_id UUID NOT NULL,
                          product_name VARCHAR(255) NOT NULL,
                          description TEXT,
                          status VARCHAR(30) NOT NULL,

                          CONSTRAINT fk_products_category
                              FOREIGN KEY (category_id)
                                  REFERENCES categories (category_id)
                                  ON DELETE RESTRICT,

                          CONSTRAINT fk_products_brand
                              FOREIGN KEY (brand_id)
                                  REFERENCES brands (brand_id)
                                  ON DELETE RESTRICT
);


CREATE TABLE product_variants (
                                  variant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                  product_id UUID NOT NULL,
                                  sku VARCHAR(100) NOT NULL,
                                  price NUMERIC(15,2) NOT NULL,
                                  cost_price NUMERIC(15,2) NOT NULL,
                                  color VARCHAR(100),
                                  storage VARCHAR(100),
                                  ram VARCHAR(100),

                                  CONSTRAINT uq_product_variants_sku
                                      UNIQUE (sku),

                                  CONSTRAINT ck_product_variants_price
                                      CHECK (price >= 0),

                                  CONSTRAINT ck_product_variants_cost_price
                                      CHECK (cost_price >= 0),

                                  CONSTRAINT fk_product_variants_product
                                      FOREIGN KEY (product_id)
                                          REFERENCES products (product_id)
                                          ON DELETE RESTRICT
);


CREATE TABLE product_images (
                                image_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                product_id UUID NOT NULL,
                                image_url VARCHAR(1000) NOT NULL,
                                is_primary BOOLEAN NOT NULL DEFAULT FALSE,

                                CONSTRAINT fk_product_images_product
                                    FOREIGN KEY (product_id)
                                        REFERENCES products (product_id)
                                        ON DELETE RESTRICT
);


CREATE TABLE specifications (
                                specification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                product_id UUID NOT NULL,
                                spec_key VARCHAR(100) NOT NULL,
                                spec_value VARCHAR(1000) NOT NULL,

                                CONSTRAINT fk_specifications_product
                                    FOREIGN KEY (product_id)
                                        REFERENCES products (product_id)
                                        ON DELETE RESTRICT
);


-- =========================================================
-- 03. SUPPLIER & WAREHOUSE
-- =========================================================

CREATE TABLE suppliers (
                           supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           supplier_code VARCHAR(50) NOT NULL,
                           supplier_name VARCHAR(255) NOT NULL,
                           phone VARCHAR(30),
                           email VARCHAR(255),
                           address VARCHAR(500),

                           CONSTRAINT uq_suppliers_supplier_code
                               UNIQUE (supplier_code)
);


CREATE TABLE warehouses (
                            warehouse_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                            warehouse_name VARCHAR(255) NOT NULL,
                            address VARCHAR(500)
);


CREATE TABLE goods_receipts (
                                receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                supplier_id UUID NOT NULL,
                                warehouse_id UUID NOT NULL,
                                employee_id UUID NOT NULL,
                                receipt_date TIMESTAMPTZ NOT NULL,
                                total_amount NUMERIC(15,2) NOT NULL,
                                status VARCHAR(30) NOT NULL,

                                CONSTRAINT ck_goods_receipts_total_amount
                                    CHECK (total_amount >= 0),

                                CONSTRAINT fk_goods_receipts_supplier
                                    FOREIGN KEY (supplier_id)
                                        REFERENCES suppliers (supplier_id)
                                        ON DELETE RESTRICT,

                                CONSTRAINT fk_goods_receipts_warehouse
                                    FOREIGN KEY (warehouse_id)
                                        REFERENCES warehouses (warehouse_id)
                                        ON DELETE RESTRICT,

                                CONSTRAINT fk_goods_receipts_employee
                                    FOREIGN KEY (employee_id)
                                        REFERENCES employees (employee_id)
                                        ON DELETE RESTRICT
);


CREATE TABLE goods_receipt_items (
                                     receipt_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                     receipt_id UUID NOT NULL,
                                     variant_id UUID NOT NULL,
                                     quantity INTEGER NOT NULL,
                                     unit_cost NUMERIC(15,2) NOT NULL,

                                     CONSTRAINT ck_goods_receipt_items_quantity
                                         CHECK (quantity > 0),

                                     CONSTRAINT ck_goods_receipt_items_unit_cost
                                         CHECK (unit_cost >= 0),

                                     CONSTRAINT fk_goods_receipt_items_receipt
                                         FOREIGN KEY (receipt_id)
                                             REFERENCES goods_receipts (receipt_id)
                                             ON DELETE RESTRICT,

                                     CONSTRAINT fk_goods_receipt_items_variant
                                         FOREIGN KEY (variant_id)
                                             REFERENCES product_variants (variant_id)
                                             ON DELETE RESTRICT
);


CREATE TABLE inventory (
                           inventory_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           warehouse_id UUID NOT NULL,
                           variant_id UUID NOT NULL,
                           quantity INTEGER NOT NULL DEFAULT 0,
                           reserved_quantity INTEGER NOT NULL DEFAULT 0,

                           CONSTRAINT uq_inventory_warehouse_variant
                               UNIQUE (warehouse_id, variant_id),

                           CONSTRAINT ck_inventory_quantity
                               CHECK (quantity >= 0),

                           CONSTRAINT ck_inventory_reserved_quantity
                               CHECK (reserved_quantity >= 0),

                           CONSTRAINT ck_inventory_reserved_le_quantity
                               CHECK (reserved_quantity <= quantity),

                           CONSTRAINT fk_inventory_warehouse
                               FOREIGN KEY (warehouse_id)
                                   REFERENCES warehouses (warehouse_id)
                                   ON DELETE RESTRICT,

                           CONSTRAINT fk_inventory_variant
                               FOREIGN KEY (variant_id)
                                   REFERENCES product_variants (variant_id)
                                   ON DELETE RESTRICT
);


CREATE TABLE serial_numbers (
                                serial_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                variant_id UUID NOT NULL,
                                warehouse_id UUID NOT NULL,
                                serial_number VARCHAR(150) NOT NULL,
                                status VARCHAR(30) NOT NULL,

                                CONSTRAINT uq_serial_numbers_serial_number
                                    UNIQUE (serial_number),

                                CONSTRAINT fk_serial_numbers_variant
                                    FOREIGN KEY (variant_id)
                                        REFERENCES product_variants (variant_id)
                                        ON DELETE RESTRICT,

                                CONSTRAINT fk_serial_numbers_warehouse
                                    FOREIGN KEY (warehouse_id)
                                        REFERENCES warehouses (warehouse_id)
                                        ON DELETE RESTRICT
);


CREATE TABLE imeis (
                       imei_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       serial_id UUID NOT NULL,
                       imei_number VARCHAR(50) NOT NULL,

                       CONSTRAINT uq_imeis_imei_number
                           UNIQUE (imei_number),

                       CONSTRAINT fk_imeis_serial
                           FOREIGN KEY (serial_id)
                               REFERENCES serial_numbers (serial_id)
                               ON DELETE RESTRICT
);


-- =========================================================
-- 04. SHOPPING CART & ORDER
-- =========================================================

CREATE TABLE carts (
                       cart_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       customer_id UUID NOT NULL,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uq_carts_customer_id
                           UNIQUE (customer_id),

                       CONSTRAINT fk_carts_customer
                           FOREIGN KEY (customer_id)
                               REFERENCES customers (customer_id)
                               ON DELETE RESTRICT
);


CREATE TABLE cart_items (
                            cart_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                            cart_id UUID NOT NULL,
                            variant_id UUID NOT NULL,
                            quantity INTEGER NOT NULL,
                            unit_price NUMERIC(15,2) NOT NULL,

                            CONSTRAINT uq_cart_items_cart_variant
                                UNIQUE (cart_id, variant_id),

                            CONSTRAINT ck_cart_items_quantity
                                CHECK (quantity > 0),

                            CONSTRAINT ck_cart_items_unit_price
                                CHECK (unit_price >= 0),

                            CONSTRAINT fk_cart_items_cart
                                FOREIGN KEY (cart_id)
                                    REFERENCES carts (cart_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_cart_items_variant
                                FOREIGN KEY (variant_id)
                                    REFERENCES product_variants (variant_id)
                                    ON DELETE RESTRICT
);


CREATE TABLE orders (
                        order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                        customer_id UUID NOT NULL,
                        employee_id UUID,
                        order_code VARCHAR(50) NOT NULL,
                        order_date TIMESTAMPTZ NOT NULL,
                        total_amount NUMERIC(15,2) NOT NULL,
                        status VARCHAR(30) NOT NULL,

                        CONSTRAINT uq_orders_order_code
                            UNIQUE (order_code),

                        CONSTRAINT ck_orders_total_amount
                            CHECK (total_amount >= 0),

                        CONSTRAINT fk_orders_customer
                            FOREIGN KEY (customer_id)
                                REFERENCES customers (customer_id)
                                ON DELETE RESTRICT,

                        CONSTRAINT fk_orders_employee
                            FOREIGN KEY (employee_id)
                                REFERENCES employees (employee_id)
                                ON DELETE RESTRICT
);


CREATE TABLE order_items (
                             order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             order_id UUID NOT NULL,
                             variant_id UUID NOT NULL,
                             serial_id UUID,
                             quantity INTEGER NOT NULL,

    -- =====================================================
    -- PRICE SNAPSHOT AT THE TIME OF SALE
    --
    -- cost_price:
    --   Product cost at the time of sale.
    --
    -- unit_price:
    --   Selling price per unit before discount.
    --
    -- discount_amount:
    --   Actual discount amount per unit.
    --
    -- final_unit_price:
    --   Actual customer price per unit after discount.
    --
    -- Formula:
    --   final_unit_price = unit_price - discount_amount
    -- =====================================================
                             cost_price NUMERIC(15,2) NOT NULL,
                             unit_price NUMERIC(15,2) NOT NULL,
                             discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
                             final_unit_price NUMERIC(15,2) NOT NULL,

                             CONSTRAINT ck_order_items_quantity
                                 CHECK (quantity > 0),

                             CONSTRAINT ck_order_items_cost_price
                                 CHECK (cost_price >= 0),

                             CONSTRAINT ck_order_items_unit_price
                                 CHECK (unit_price >= 0),

                             CONSTRAINT ck_order_items_discount_amount
                                 CHECK (
                                     discount_amount >= 0
                                         AND discount_amount <= unit_price
                                     ),

                             CONSTRAINT ck_order_items_final_unit_price
                                 CHECK (
                                     final_unit_price >= 0
                                         AND final_unit_price = unit_price - discount_amount
                                     ),

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id)
                                     REFERENCES orders (order_id)
                                     ON DELETE RESTRICT,

                             CONSTRAINT fk_order_items_variant
                                 FOREIGN KEY (variant_id)
                                     REFERENCES product_variants (variant_id)
                                     ON DELETE RESTRICT,

                             CONSTRAINT fk_order_items_serial
                                 FOREIGN KEY (serial_id)
                                     REFERENCES serial_numbers (serial_id)
                                     ON DELETE RESTRICT
);


CREATE TABLE payments (
                          payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          order_id UUID NOT NULL,
                          payment_method VARCHAR(50) NOT NULL,
                          amount NUMERIC(15,2) NOT NULL,
                          payment_date TIMESTAMPTZ NOT NULL,
                          status VARCHAR(30) NOT NULL,

                          CONSTRAINT ck_payments_amount
                              CHECK (amount >= 0),

                          CONSTRAINT fk_payments_order
                              FOREIGN KEY (order_id)
                                  REFERENCES orders (order_id)
                                  ON DELETE RESTRICT
);


CREATE TABLE installment_payments (
                                      installment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                      order_id UUID NOT NULL,
                                      provider VARCHAR(100) NOT NULL,
                                      total_amount NUMERIC(15,2) NOT NULL,
                                      down_payment NUMERIC(15,2) NOT NULL,
                                      remaining_amount NUMERIC(15,2) NOT NULL,
                                      term_months INTEGER NOT NULL,
                                      status VARCHAR(30) NOT NULL,

                                      CONSTRAINT uq_installment_payments_order_id
                                          UNIQUE (order_id),

                                      CONSTRAINT ck_installment_total_amount
                                          CHECK (total_amount >= 0),

                                      CONSTRAINT ck_installment_down_payment
                                          CHECK (down_payment >= 0),

                                      CONSTRAINT ck_installment_remaining_amount
                                          CHECK (remaining_amount >= 0),

                                      CONSTRAINT ck_installment_amount_consistency
                                          CHECK (
                                              down_payment + remaining_amount = total_amount
                                              ),

                                      CONSTRAINT ck_installment_term_months
                                          CHECK (term_months > 0),

                                      CONSTRAINT fk_installment_payments_order
                                          FOREIGN KEY (order_id)
                                              REFERENCES orders (order_id)
                                              ON DELETE RESTRICT
);


CREATE TABLE invoices (
                          invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          order_id UUID NOT NULL,
                          invoice_number VARCHAR(100) NOT NULL,
                          issued_date TIMESTAMPTZ NOT NULL,
                          tax_amount NUMERIC(15,2) NOT NULL,
                          total_amount NUMERIC(15,2) NOT NULL,

                          CONSTRAINT uq_invoices_order_id
                              UNIQUE (order_id),

                          CONSTRAINT uq_invoices_invoice_number
                              UNIQUE (invoice_number),

                          CONSTRAINT ck_invoices_tax_amount
                              CHECK (tax_amount >= 0),

                          CONSTRAINT ck_invoices_total_amount
                              CHECK (total_amount >= 0),

                          CONSTRAINT fk_invoices_order
                              FOREIGN KEY (order_id)
                                  REFERENCES orders (order_id)
                                  ON DELETE RESTRICT
);


CREATE TABLE shipments (
                           shipment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           order_id UUID NOT NULL,
                           carrier VARCHAR(100),
                           tracking_number VARCHAR(150),
                           shipping_address VARCHAR(500) NOT NULL,
                           shipped_date TIMESTAMPTZ,
                           delivered_date TIMESTAMPTZ,
                           status VARCHAR(30) NOT NULL,

                           CONSTRAINT uq_shipments_order_id
                               UNIQUE (order_id),

                           CONSTRAINT ck_shipments_date_range
                               CHECK (
                                   delivered_date IS NULL
                                       OR shipped_date IS NULL
                                       OR delivered_date >= shipped_date
                                   ),

                           CONSTRAINT fk_shipments_order
                               FOREIGN KEY (order_id)
                                   REFERENCES orders (order_id)
                                   ON DELETE RESTRICT
);


-- =========================================================
-- 05. RETURN & WARRANTY
-- =========================================================

CREATE TABLE return_requests (
                                 return_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                 order_id UUID NOT NULL,
                                 customer_id UUID NOT NULL,
                                 employee_id UUID,
                                 reason TEXT NOT NULL,
                                 request_date TIMESTAMPTZ NOT NULL,
                                 status VARCHAR(30) NOT NULL,

                                 CONSTRAINT fk_return_requests_order
                                     FOREIGN KEY (order_id)
                                         REFERENCES orders (order_id)
                                         ON DELETE RESTRICT,

                                 CONSTRAINT fk_return_requests_customer
                                     FOREIGN KEY (customer_id)
                                         REFERENCES customers (customer_id)
                                         ON DELETE RESTRICT,

                                 CONSTRAINT fk_return_requests_employee
                                     FOREIGN KEY (employee_id)
                                         REFERENCES employees (employee_id)
                                         ON DELETE RESTRICT
);


CREATE TABLE return_items (
                              return_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              return_id UUID NOT NULL,
                              order_item_id UUID NOT NULL,
                              serial_id UUID,
                              quantity INTEGER NOT NULL,
                              reason VARCHAR(500) NOT NULL,

                              CONSTRAINT ck_return_items_quantity
                                  CHECK (quantity > 0),

                              CONSTRAINT fk_return_items_return
                                  FOREIGN KEY (return_id)
                                      REFERENCES return_requests (return_id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_return_items_order_item
                                  FOREIGN KEY (order_item_id)
                                      REFERENCES order_items (order_item_id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_return_items_serial
                                  FOREIGN KEY (serial_id)
                                      REFERENCES serial_numbers (serial_id)
                                      ON DELETE RESTRICT
);


CREATE TABLE warranties (
                            warranty_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                            serial_id UUID NOT NULL,
                            order_item_id UUID NOT NULL,
                            customer_id UUID NOT NULL,
                            start_date DATE NOT NULL,
                            end_date DATE NOT NULL,
                            status VARCHAR(30) NOT NULL,

                            CONSTRAINT uq_warranties_serial_id
                                UNIQUE (serial_id),

                            CONSTRAINT uq_warranties_order_item_id
                                UNIQUE (order_item_id),

                            CONSTRAINT ck_warranties_date_range
                                CHECK (end_date >= start_date),

                            CONSTRAINT fk_warranties_serial
                                FOREIGN KEY (serial_id)
                                    REFERENCES serial_numbers (serial_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_warranties_order_item
                                FOREIGN KEY (order_item_id)
                                    REFERENCES order_items (order_item_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_warranties_customer
                                FOREIGN KEY (customer_id)
                                    REFERENCES customers (customer_id)
                                    ON DELETE RESTRICT
);


CREATE TABLE warranty_tickets (
                                  ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                  warranty_id UUID NOT NULL,
                                  serial_id UUID NOT NULL,
                                  customer_id UUID NOT NULL,
                                  employee_id UUID,
                                  issue_description TEXT NOT NULL,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  resolved_at TIMESTAMPTZ,
                                  status VARCHAR(30) NOT NULL,

                                  CONSTRAINT ck_warranty_tickets_resolved_at
                                      CHECK (
                                          resolved_at IS NULL
                                              OR resolved_at >= created_at
                                          ),

                                  CONSTRAINT fk_warranty_tickets_warranty
                                      FOREIGN KEY (warranty_id)
                                          REFERENCES warranties (warranty_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_warranty_tickets_serial
                                      FOREIGN KEY (serial_id)
                                          REFERENCES serial_numbers (serial_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_warranty_tickets_customer
                                      FOREIGN KEY (customer_id)
                                          REFERENCES customers (customer_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_warranty_tickets_employee
                                      FOREIGN KEY (employee_id)
                                          REFERENCES employees (employee_id)
                                          ON DELETE RESTRICT
);


-- =========================================================
-- 06. PROMOTION & REVIEW
-- =========================================================

CREATE TABLE promotions (
                            promotion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                            promotion_name VARCHAR(255) NOT NULL,
                            description TEXT,
                            discount_type VARCHAR(30) NOT NULL,
                            discount_value NUMERIC(15,2) NOT NULL,
                            start_date TIMESTAMPTZ NOT NULL,
                            end_date TIMESTAMPTZ NOT NULL,
                            status VARCHAR(30) NOT NULL,

                            CONSTRAINT ck_promotions_discount_type
                                CHECK (discount_type IN ('PERCENT', 'FIXED')),

                            CONSTRAINT ck_promotions_discount_value
                                CHECK (discount_value >= 0),

                            CONSTRAINT ck_promotions_percent_value
                                CHECK (
                                    discount_type <> 'PERCENT'
                                        OR discount_value <= 100
                                    ),

                            CONSTRAINT ck_promotions_date_range
                                CHECK (end_date >= start_date)
);


CREATE TABLE promotion_products (
                                    promotion_id UUID NOT NULL,
                                    product_id UUID NOT NULL,

                                    CONSTRAINT pk_promotion_products
                                        PRIMARY KEY (promotion_id, product_id),

                                    CONSTRAINT fk_promotion_products_promotion
                                        FOREIGN KEY (promotion_id)
                                            REFERENCES promotions (promotion_id)
                                            ON DELETE RESTRICT,

                                    CONSTRAINT fk_promotion_products_product
                                        FOREIGN KEY (product_id)
                                            REFERENCES products (product_id)
                                            ON DELETE RESTRICT
);


CREATE TABLE reviews (
                         review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                         customer_id UUID NOT NULL,
                         product_id UUID NOT NULL,
                         order_item_id UUID NOT NULL,
                         employee_id UUID,
                         rating INTEGER NOT NULL,
                         review_text TEXT,
                         reply_text TEXT,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         replied_at TIMESTAMPTZ,

                         CONSTRAINT ck_reviews_rating
                             CHECK (rating BETWEEN 1 AND 5),

                         CONSTRAINT ck_reviews_reply_date
                             CHECK (
                                 replied_at IS NULL
                                     OR replied_at >= created_at
                                 ),

                         CONSTRAINT fk_reviews_customer
                             FOREIGN KEY (customer_id)
                                 REFERENCES customers (customer_id)
                                 ON DELETE RESTRICT,

                         CONSTRAINT fk_reviews_product
                             FOREIGN KEY (product_id)
                                 REFERENCES products (product_id)
                                 ON DELETE RESTRICT,

                         CONSTRAINT fk_reviews_order_item
                             FOREIGN KEY (order_item_id)
                                 REFERENCES order_items (order_item_id)
                                 ON DELETE RESTRICT,

                         CONSTRAINT fk_reviews_employee
                             FOREIGN KEY (employee_id)
                                 REFERENCES employees (employee_id)
                                 ON DELETE RESTRICT
);


-- =========================================================
-- 07. CHATBOT & CONVERSATION
-- =========================================================

CREATE TABLE chatbot_qna (
                             qna_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             question TEXT NOT NULL,
                             answer TEXT NOT NULL,
                             category VARCHAR(100),
                             keywords TEXT,
                             status VARCHAR(30) NOT NULL,
                             employee_id UUID NOT NULL,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_chatbot_qna_employee
                                 FOREIGN KEY (employee_id)
                                     REFERENCES employees (employee_id)
                                     ON DELETE RESTRICT
);


CREATE TABLE chatbot_scripts (
                                 script_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                 script_name VARCHAR(255) NOT NULL,
                                 trigger VARCHAR(255) NOT NULL,
                                 response_template TEXT NOT NULL,
                                 priority INTEGER NOT NULL,
                                 status VARCHAR(30) NOT NULL,
                                 employee_id UUID NOT NULL,

                                 CONSTRAINT ck_chatbot_scripts_priority
                                     CHECK (priority >= 0),

                                 CONSTRAINT fk_chatbot_scripts_employee
                                     FOREIGN KEY (employee_id)
                                         REFERENCES employees (employee_id)
                                         ON DELETE RESTRICT
);


CREATE TABLE chat_sessions (
                               session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                               customer_id UUID NOT NULL,
                               employee_id UUID,
                               started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               ended_at TIMESTAMPTZ,
                               status VARCHAR(30) NOT NULL,

                               CONSTRAINT ck_chat_sessions_date_range
                                   CHECK (
                                       ended_at IS NULL
                                           OR ended_at >= started_at
                                       ),

                               CONSTRAINT fk_chat_sessions_customer
                                   FOREIGN KEY (customer_id)
                                       REFERENCES customers (customer_id)
                                       ON DELETE RESTRICT,

                               CONSTRAINT fk_chat_sessions_employee
                                   FOREIGN KEY (employee_id)
                                       REFERENCES employees (employee_id)
                                       ON DELETE RESTRICT
);


CREATE TABLE chat_history (
                              history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              session_id UUID NOT NULL,
                              customer_id UUID,
                              employee_id UUID,
                              sender_type VARCHAR(30) NOT NULL,
                              message TEXT NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT ck_chat_history_sender_type
                                  CHECK (
                                      sender_type IN ('CUSTOMER', 'AI', 'EMPLOYEE')
                                      ),

                              CONSTRAINT fk_chat_history_session
                                  FOREIGN KEY (session_id)
                                      REFERENCES chat_sessions (session_id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_chat_history_customer
                                  FOREIGN KEY (customer_id)
                                      REFERENCES customers (customer_id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_chat_history_employee
                                  FOREIGN KEY (employee_id)
                                      REFERENCES employees (employee_id)
                                      ON DELETE RESTRICT
);


CREATE TABLE message_feedback (
                                  feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                  history_id UUID NOT NULL,
                                  customer_id UUID NOT NULL,
                                  rating INTEGER NOT NULL,
                                  feedback_text TEXT,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT ck_message_feedback_rating
                                      CHECK (rating BETWEEN 1 AND 5),

                                  CONSTRAINT fk_message_feedback_history
                                      FOREIGN KEY (history_id)
                                          REFERENCES chat_history (history_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_message_feedback_customer
                                      FOREIGN KEY (customer_id)
                                          REFERENCES customers (customer_id)
                                          ON DELETE RESTRICT
);


-- =========================================================
-- 08. RAG KNOWLEDGE BASE
-- =========================================================

CREATE TABLE knowledge_documents (
                                     document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                     title VARCHAR(255) NOT NULL,
                                     source VARCHAR(1000) NOT NULL,
                                     document_type VARCHAR(100) NOT NULL,
                                     content TEXT NOT NULL,
                                     status VARCHAR(30) NOT NULL,
                                     employee_id UUID NOT NULL,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_knowledge_documents_employee
                                         FOREIGN KEY (employee_id)
                                             REFERENCES employees (employee_id)
                                             ON DELETE RESTRICT
);


CREATE TABLE knowledge_chunks (
                                  chunk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                  document_id UUID NOT NULL,
                                  chunk_index INTEGER NOT NULL,
                                  content TEXT NOT NULL,

                                  CONSTRAINT uq_knowledge_chunks_document_index
                                      UNIQUE (document_id, chunk_index),

                                  CONSTRAINT ck_knowledge_chunks_chunk_index
                                      CHECK (chunk_index >= 0),

                                  CONSTRAINT fk_knowledge_chunks_document
                                      FOREIGN KEY (document_id)
                                          REFERENCES knowledge_documents (document_id)
                                          ON DELETE RESTRICT
);


CREATE TABLE chat_message_retrievals (
                                         retrieval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                         history_id UUID NOT NULL,
                                         chunk_id UUID NOT NULL,
                                         similarity_score NUMERIC(10,8) NOT NULL,
                                         retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                         CONSTRAINT ck_chat_message_retrievals_similarity_score
                                             CHECK (
                                                 similarity_score >= 0
                                                     AND similarity_score <= 1
                                                 ),

                                         CONSTRAINT fk_chat_message_retrievals_history
                                             FOREIGN KEY (history_id)
                                                 REFERENCES chat_history (history_id)
                                                 ON DELETE RESTRICT,

                                         CONSTRAINT fk_chat_message_retrievals_chunk
                                             FOREIGN KEY (chunk_id)
                                                 REFERENCES knowledge_chunks (chunk_id)
                                                 ON DELETE RESTRICT
);


-- =========================================================
-- 09. INDEXES
-- =========================================================


-- =========================================================
-- USER & ACCESS CONTROL
-- =========================================================

CREATE INDEX idx_user_roles_role_id
    ON user_roles (role_id);

CREATE INDEX idx_role_permissions_permission_id
    ON role_permissions (permission_id);


-- =========================================================
-- PRODUCT CATALOG
-- =========================================================

CREATE INDEX idx_categories_parent_id
    ON categories (parent_id);

CREATE INDEX idx_products_category_id
    ON products (category_id);

CREATE INDEX idx_products_brand_id
    ON products (brand_id);

CREATE INDEX idx_product_variants_product_id
    ON product_variants (product_id);

CREATE INDEX idx_product_images_product_id
    ON product_images (product_id);

CREATE INDEX idx_specifications_product_id
    ON specifications (product_id);


-- Only one primary image per product
CREATE UNIQUE INDEX uq_product_images_primary
    ON product_images (product_id)
    WHERE is_primary = TRUE;


-- =========================================================
-- SUPPLIER & WAREHOUSE
-- =========================================================

CREATE INDEX idx_goods_receipts_supplier_id
    ON goods_receipts (supplier_id);

CREATE INDEX idx_goods_receipts_warehouse_id
    ON goods_receipts (warehouse_id);

CREATE INDEX idx_goods_receipts_employee_id
    ON goods_receipts (employee_id);

CREATE INDEX idx_goods_receipts_receipt_date
    ON goods_receipts (receipt_date);

CREATE INDEX idx_goods_receipt_items_receipt_id
    ON goods_receipt_items (receipt_id);

CREATE INDEX idx_goods_receipt_items_variant_id
    ON goods_receipt_items (variant_id);

CREATE INDEX idx_inventory_warehouse_id
    ON inventory (warehouse_id);

CREATE INDEX idx_inventory_variant_id
    ON inventory (variant_id);

CREATE INDEX idx_serial_numbers_variant_id
    ON serial_numbers (variant_id);

CREATE INDEX idx_serial_numbers_warehouse_id
    ON serial_numbers (warehouse_id);

CREATE INDEX idx_imeis_serial_id
    ON imeis (serial_id);


-- =========================================================
-- CART & ORDER
-- =========================================================

CREATE INDEX idx_cart_items_variant_id
    ON cart_items (variant_id);

CREATE INDEX idx_orders_customer_id
    ON orders (customer_id);

CREATE INDEX idx_orders_employee_id
    ON orders (employee_id);

CREATE INDEX idx_orders_order_date
    ON orders (order_date);

CREATE INDEX idx_order_items_order_id
    ON order_items (order_id);

CREATE INDEX idx_order_items_variant_id
    ON order_items (variant_id);

CREATE INDEX idx_order_items_serial_id
    ON order_items (serial_id);

CREATE INDEX idx_payments_order_id
    ON payments (order_id);

CREATE INDEX idx_installment_payments_order_id
    ON installment_payments (order_id);

CREATE INDEX idx_invoices_order_id
    ON invoices (order_id);

CREATE INDEX idx_shipments_order_id
    ON shipments (order_id);


-- =========================================================
-- RETURN & WARRANTY
-- =========================================================

CREATE INDEX idx_return_requests_order_id
    ON return_requests (order_id);

CREATE INDEX idx_return_requests_customer_id
    ON return_requests (customer_id);

CREATE INDEX idx_return_requests_employee_id
    ON return_requests (employee_id);

CREATE INDEX idx_return_items_return_id
    ON return_items (return_id);

CREATE INDEX idx_return_items_order_item_id
    ON return_items (order_item_id);

CREATE INDEX idx_return_items_serial_id
    ON return_items (serial_id);

CREATE INDEX idx_warranties_customer_id
    ON warranties (customer_id);

CREATE INDEX idx_warranty_tickets_warranty_id
    ON warranty_tickets (warranty_id);

CREATE INDEX idx_warranty_tickets_serial_id
    ON warranty_tickets (serial_id);

CREATE INDEX idx_warranty_tickets_customer_id
    ON warranty_tickets (customer_id);

CREATE INDEX idx_warranty_tickets_employee_id
    ON warranty_tickets (employee_id);


-- =========================================================
-- PROMOTION & REVIEW
-- =========================================================

CREATE INDEX idx_promotion_products_product_id
    ON promotion_products (product_id);

CREATE INDEX idx_reviews_customer_id
    ON reviews (customer_id);

CREATE INDEX idx_reviews_product_id
    ON reviews (product_id);

CREATE INDEX idx_reviews_order_item_id
    ON reviews (order_item_id);

CREATE INDEX idx_reviews_employee_id
    ON reviews (employee_id);


-- =========================================================
-- CHATBOT
-- =========================================================

CREATE INDEX idx_chatbot_qna_employee_id
    ON chatbot_qna (employee_id);

CREATE INDEX idx_chatbot_qna_status
    ON chatbot_qna (status);

CREATE INDEX idx_chatbot_scripts_employee_id
    ON chatbot_scripts (employee_id);

CREATE INDEX idx_chatbot_scripts_status_priority
    ON chatbot_scripts (status, priority);

CREATE INDEX idx_chat_sessions_customer_id
    ON chat_sessions (customer_id);

CREATE INDEX idx_chat_sessions_employee_id
    ON chat_sessions (employee_id);

CREATE INDEX idx_chat_history_session_id
    ON chat_history (session_id);

CREATE INDEX idx_chat_history_customer_id
    ON chat_history (customer_id);

CREATE INDEX idx_chat_history_employee_id
    ON chat_history (employee_id);

CREATE INDEX idx_chat_history_created_at
    ON chat_history (created_at);

CREATE INDEX idx_message_feedback_history_id
    ON message_feedback (history_id);

CREATE INDEX idx_message_feedback_customer_id
    ON message_feedback (customer_id);


-- =========================================================
-- RAG
-- =========================================================

CREATE INDEX idx_knowledge_documents_employee_id
    ON knowledge_documents (employee_id);

CREATE INDEX idx_knowledge_documents_status
    ON knowledge_documents (status);

CREATE INDEX idx_knowledge_chunks_document_id
    ON knowledge_chunks (document_id);

CREATE INDEX idx_chat_message_retrievals_history_id
    ON chat_message_retrievals (history_id);

CREATE INDEX idx_chat_message_retrievals_chunk_id
    ON chat_message_retrievals (chunk_id);

CREATE INDEX idx_chat_message_retrievals_retrieved_at
    ON chat_message_retrievals (retrieved_at);