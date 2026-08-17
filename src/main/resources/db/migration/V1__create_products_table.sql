CREATE TABLE products
(
    id             UUID PRIMARY KEY,
    sku            VARCHAR(32)                 NOT NULL UNIQUE,
    name           VARCHAR(120)                NOT NULL,
    description    VARCHAR(2000),
    price          NUMERIC(12, 2)              NOT NULL,
    stock_quantity INTEGER                     NOT NULL,
    category       VARCHAR(32)                 NOT NULL,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version        BIGINT                      NOT NULL,

    CONSTRAINT products_price_non_negative CHECK (price >= 0),
    CONSTRAINT products_stock_quantity_non_negative CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_category ON products (category);
