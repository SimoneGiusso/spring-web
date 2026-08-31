CREATE TABLE revinfo
(
    rev      INTEGER PRIMARY KEY,
    revtstmp BIGINT
);

CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE products_aud
(
    id             UUID     NOT NULL,
    rev            INTEGER  NOT NULL,
    revtype        SMALLINT,
    sku            VARCHAR(32),
    name           VARCHAR(120),
    description    VARCHAR(2000),
    price          NUMERIC(12, 2),
    stock_quantity INTEGER,
    category       VARCHAR(32),
    created_at     TIMESTAMP(6) WITH TIME ZONE,
    updated_at     TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT products_aud_pk PRIMARY KEY (rev, id),
    CONSTRAINT products_aud_rev_fk FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
