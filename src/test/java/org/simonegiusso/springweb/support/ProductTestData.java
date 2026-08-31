package org.simonegiusso.springweb.support;

import static java.time.ZoneOffset.UTC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.RevisionType;
import org.simonegiusso.springweb.product.Product;
import org.simonegiusso.springweb.product.ProductRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

public class ProductTestData {

    public static final UUID ESPRESSO_MACHINE_ID = UUID.fromString("0198e2c5-1a2b-7c3d-8e4f-000000000001");
    public static final Instant SEEDED_AT = Instant.parse("2026-01-15T09:00:00Z");

    private final JdbcClient database;
    private final ProductRepository repository;

    ProductTestData(JdbcClient database, ProductRepository repository) {
        this.database = database;
        this.repository = repository;
    }

    /** Inserted with SQL: auditing would overwrite the timestamps and the id is generated on save. */
    public void insertAnEspressoMachine() {
        database
            .sql(
                """
                    INSERT INTO products (id, sku, name, description, price, stock_quantity,
                                          category, created_at, updated_at, version)
                    VALUES (:id, 'SKU-500100', 'Espresso Machine', 'Dual boiler, PID controlled',
                            899.00, 7, 'HOME', :timestamp, :timestamp, 0)
                    """)
            .param("id", ESPRESSO_MACHINE_ID)
            .param("timestamp", SEEDED_AT.atOffset(UTC))
            .update();
    }

    public Product findBy(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("No product with id " + id));
    }

    public Product findOnlyProduct() {
        List<Product> products = repository.findAll();
        if (products.size() != 1) {
            throw new IllegalStateException("Expected exactly one product but found " + products.size());
        }
        return products.getFirst();
    }

    public long countProducts() {
        return repository.count();
    }

    public List<RevisionType> revisionTypes() {
        return database
            .sql("SELECT revtype FROM products_aud ORDER BY rev")
            .query(Byte.class)
            .list()
            .stream()
            .map(RevisionType::fromRepresentation)
            .toList();
    }
}
