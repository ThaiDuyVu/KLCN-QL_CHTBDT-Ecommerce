-- =========================================================
-- V3 - CREATE PRODUCT SCHEMA
-- Project: QL_CHTBDT_Ecommerce
-- Database: PostgreSQL 16
--
-- Feature:
--   - Brand
--   - Product
--   - ProductVariant
--   - ProductImage
--   - Specification
--
-- Dependencies:
--   - V2 categories
--
-- Notes:
--   - Money uses NUMERIC(15,2)
--   - No hard delete for business data
--   - Foreign keys use ON DELETE RESTRICT
-- =========================================================


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
-- INDEXES
-- =========================================================

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


-- =========================================================
-- BUSINESS CONSTRAINT
-- Only one primary image per product
-- =========================================================

CREATE UNIQUE INDEX uq_product_images_primary
    ON product_images (product_id)
    WHERE is_primary = TRUE;

