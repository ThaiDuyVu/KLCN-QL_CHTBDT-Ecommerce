-- Embedding dimension is intentionally unconstrained until the deployed model is fixed.
-- Only semantic product content belongs here; price and stock remain in business tables.
CREATE TABLE product_semantic_index (
    product_id UUID PRIMARY KEY REFERENCES products(product_id) ON DELETE CASCADE,
    semantic_content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_vector vector NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
