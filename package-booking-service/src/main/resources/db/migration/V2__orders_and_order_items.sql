-- V2: Order / OrderItem model
-- PRODUCT-ARCHITECTURE.md SS5: Order(customerId, status, currency, quoteId),
-- OrderItem(vertical, supplierOrganizationId, productRef, fulfillmentRef, commercialSnapshot).
-- Additive to V1 package_bookings; the legacy PackageBooking API and table are untouched.

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    quote_id UUID,
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_customer_status ON orders (customer_id, status);

CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    vertical VARCHAR(20) NOT NULL,
    supplier_organization_id UUID,
    product_ref UUID NOT NULL,
    fulfillment_ref UUID,
    description VARCHAR(500) NOT NULL,
    unit_amount NUMERIC(12,2) NOT NULL CHECK (unit_amount > 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_supplier_org ON order_items (supplier_organization_id);
