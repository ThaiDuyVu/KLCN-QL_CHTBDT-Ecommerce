-- =========================================================
-- V2 - CREATE CATEGORY SCHEMA
-- Project: QL_CHTBDT_Ecommerce
-- Database: PostgreSQL 16
--
-- Feature:
--   - Category
--
-- Notes:
--   - Supports hierarchical categories
--   - parent_id is NULL for root categories
--   - No hard delete for business data
--   - Foreign key uses ON DELETE RESTRICT
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


-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX idx_categories_parent_id
    ON categories (parent_id);
