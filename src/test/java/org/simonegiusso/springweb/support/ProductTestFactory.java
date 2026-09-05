package org.simonegiusso.springweb.support;

import static java.time.ZoneOffset.UTC;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class ProductTestFactory {

    public static final String ALICE = "alice";
    public static final String BOB = "bob";
    public static final UUID ESPRESSO_MACHINE_ID = UUID.fromString("0198e2c5-1a2b-7c3d-8e4f-000000000001");
    public static final Instant SEEDED_AT = Instant.parse("2026-01-15T09:00:00Z");
    public static final UUID KEYBOARD_ID = UUID.fromString("0198e2c5-1a2b-7c3d-8e4f-000000000002");
    private final JdbcClient database;

    ProductTestFactory(JdbcClient database) {
        this.database = database;
    }

    public void insertAnEspressoMachineOwnedBy(String owner) {
        database.sql(
                """
                    INSERT INTO products (id, owner, sku, name, description, price, stock_quantity,
                                          category, created_at, updated_at, version)
                    VALUES (:id, :owner, 'SKU-500100', 'Espresso Machine', 'Dual boiler, PID controlled',
                            899.00, 7, 'HOME', :timestamp, :timestamp, 0)
                    """)
            .param("id", ESPRESSO_MACHINE_ID)
            .param("owner", owner)
            .param("timestamp", SEEDED_AT.atOffset(UTC))
            .update();
    }

    public void insertAKeyboardOwnedBy(String owner) {
        database.sql(
                """
                    INSERT INTO products (id, owner, sku, name, description, price, stock_quantity,
                                          category, created_at, updated_at, version)
                    VALUES (:id, :owner, 'SKU-100200', 'Mechanical Keyboard', 'Hot-swappable switches',
                            129.90, 42, 'ELECTRONICS', :timestamp, :timestamp, 0)
                    """)
            .param("id", KEYBOARD_ID)
            .param("owner", owner)
            .param("timestamp", SEEDED_AT.atOffset(UTC))
            .update();
    }
}
