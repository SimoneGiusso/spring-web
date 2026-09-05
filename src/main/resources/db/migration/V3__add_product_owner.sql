ALTER TABLE products
    ADD COLUMN owner VARCHAR(64) NOT NULL DEFAULT 'unknown';
ALTER TABLE products
    ALTER COLUMN owner DROP DEFAULT;

ALTER TABLE products
    DROP CONSTRAINT products_sku_key;

ALTER TABLE products
    ADD CONSTRAINT products_owner_sku_unique UNIQUE (owner, sku);

DROP INDEX idx_products_category;

CREATE INDEX idx_products_owner_category ON products (owner, category);

ALTER TABLE products_aud
    ADD COLUMN owner VARCHAR(64);
