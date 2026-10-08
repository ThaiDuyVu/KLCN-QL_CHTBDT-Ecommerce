CREATE TABLE customer_addresses (
    address_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    label VARCHAR(100),
    recipient_name VARCHAR(255) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    ward VARCHAR(150),
    district VARCHAR(150),
    province VARCHAR(150),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses(customer_id);
CREATE UNIQUE INDEX uq_customer_addresses_one_default
    ON customer_addresses(customer_id) WHERE is_default = TRUE;
