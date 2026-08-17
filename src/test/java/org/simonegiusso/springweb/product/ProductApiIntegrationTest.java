package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.AbstractIntegrationTest;
import org.simonegiusso.springweb.support.JsonFixture;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

class ProductApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ESPRESSO_MACHINE_ID =
        UUID.fromString("0198e2c5-1a2b-7c3d-8e4f-000000000001");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-7000-8000-000000000000");
    private static final OffsetDateTime SEEDED_AT = OffsetDateTime.parse("2026-01-15T09:00:00Z");
    private static final String SELECT_PRODUCT =
        """
            SELECT id, sku, name, description, price, stock_quantity, category,
                   created_at, updated_at, version
            FROM products
            """;

    @Test
    void getReturnsTheStoredProduct() {
        seedEspressoMachine();

        client.get()
            .uri("/api/products/{id}", ESPRESSO_MACHINE_ID)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(APPLICATION_JSON)
            .expectBody()
            .json(JsonFixture.load("espresso-machine.json"), STRICT);

        assertThat(storedProduct(ESPRESSO_MACHINE_ID).version()).isZero();
    }

    @Test
    void getReturnsProblemDetailWhenTheProductDoesNotExist() {
        client.get()
            .uri("/api/products/{id}", UNKNOWN_ID)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .json(
                JsonFixture.load("product-not-found.json", Map.of("id", UNKNOWN_ID)),
                STRICT);
    }

    @Test
    void postCreatesTheProductAndReturnsItsRepresentation() {
        EntityExchangeResult<byte[]> response =
            client.post()
                .uri("/api/products")
                .contentType(APPLICATION_JSON)
                .body(
                    """
                        {
                          "sku": "SKU-100200",
                          "name": "Mechanical Keyboard",
                          "description": "Compact 75% layout with hot-swappable switches",
                          "price": 129.90,
                          "stockQuantity": 42,
                          "category": "ELECTRONICS"
                        }
                        """)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentType(APPLICATION_JSON)
                .expectBody()
                .json(JsonFixture.load("mechanical-keyboard.json"), STRICT)
                .returnResult();

        ProductRow stored = singleStoredProduct();

        assertThat(response.getResponseHeaders().getLocation())
            .hasToString("http://localhost:" + port + "/api/products/" + stored.id());
        assertThat(stored.sku()).isEqualTo("SKU-100200");
        assertThat(stored.name()).isEqualTo("Mechanical Keyboard");
        assertThat(stored.price()).isEqualByComparingTo("129.90");
        assertThat(stored.stockQuantity()).isEqualTo(42);
        assertThat(stored.category()).isEqualTo("ELECTRONICS");
        assertThat(stored.createdAt().toInstant()).isEqualTo(FIXED_NOW);
        assertThat(stored.updatedAt().toInstant()).isEqualTo(FIXED_NOW);
        assertThat(stored.version()).isZero();
    }

    @Test
    void postReportsEveryViolatedConstraintAtOnce() {
        client.post()
            .uri("/api/products")
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "KEYBOARD",
                      "name": "",
                      "price": 0.00,
                      "stockQuantity": -5
                    }
                    """)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .json(JsonFixture.load("validation-failed.json"), STRICT);

        assertThat(countProducts()).isZero();
    }

    @Test
    void postRejectsServerManagedFields() {
        client.post()
            .uri("/api/products")
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-100300",
                      "name": "Forged Timestamp",
                      "price": 10.00,
                      "stockQuantity": 1,
                      "category": "TOYS",
                      "createdAt": "1999-01-01T00:00:00Z"
                    }
                    """)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .json(JsonFixture.load("readonly-field.json"), STRICT);

        assertThat(countProducts()).isZero();
    }

    @Test
    void postRejectsAnAlreadyUsedSku() {
        seedEspressoMachine();

        client.post()
            .uri("/api/products")
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-500100",
                      "name": "Another Espresso Machine",
                      "price": 500.00,
                      "stockQuantity": 1,
                      "category": "HOME"
                    }
                    """)
            .exchange()
            .expectStatus()
            .isEqualTo(CONFLICT)
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .json(JsonFixture.load("duplicate-sku.json"), STRICT);

        assertThat(countProducts()).isOne();
    }

    @Test
    void patchUpdatesOnlyTheProvidedFields() {
        seedEspressoMachine();

        client.patch()
            .uri("/api/products/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "price": 799.50,
                      "stockQuantity": 3
                    }
                    """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(APPLICATION_JSON)
            .expectBody()
            .json(JsonFixture.load("espresso-machine-discounted.json"), STRICT);

        ProductRow stored = storedProduct(ESPRESSO_MACHINE_ID);
        assertThat(stored.price()).isEqualByComparingTo("799.50");
        assertThat(stored.stockQuantity()).isEqualTo(3);
        assertThat(stored.name()).isEqualTo("Espresso Machine");
        assertThat(stored.description()).isEqualTo("Dual boiler, PID controlled");
        assertThat(stored.category()).isEqualTo("HOME");
        assertThat(stored.createdAt()).isEqualTo(SEEDED_AT);
        assertThat(stored.updatedAt().toInstant()).isEqualTo(FIXED_NOW);
        assertThat(stored.version()).isOne();
    }

    @Test
    void patchRejectsASkuInTheBody() {
        seedEspressoMachine();

        client.patch()
            .uri("/api/products/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-999999",
                      "price": 799.50
                    }
                    """)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .json(JsonFixture.load("patch-with-sku.json"), STRICT);

        ProductRow stored = storedProduct(ESPRESSO_MACHINE_ID);
        assertThat(stored.sku()).isEqualTo("SKU-500100");
        assertThat(stored.price()).isEqualByComparingTo("899.00");
        assertThat(stored.version()).isZero();
    }

    @Test
    void patchWithoutAnyFieldLeavesTheProductUntouched() {
        seedEspressoMachine();

        client.patch()
            .uri("/api/products/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body("{}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(APPLICATION_JSON)
            .expectBody()
            .json(JsonFixture.load("espresso-machine.json"), STRICT);

        ProductRow stored = storedProduct(ESPRESSO_MACHINE_ID);
        assertThat(stored.updatedAt()).isEqualTo(SEEDED_AT);
        assertThat(stored.version()).isZero();
    }

    private void seedEspressoMachine() {
        database
            .sql(
                """
                    INSERT INTO products (id, sku, name, description, price, stock_quantity,
                                          category, created_at, updated_at, version)
                    VALUES (:id, 'SKU-500100', 'Espresso Machine', 'Dual boiler, PID controlled',
                            899.00, 7, 'HOME', :timestamp, :timestamp, 0)
                    """)
            .param("id", ESPRESSO_MACHINE_ID)
            .param("timestamp", SEEDED_AT)
            .update();
    }

    private ProductRow storedProduct(UUID id) {
        return database.sql(SELECT_PRODUCT + " WHERE id = :id").param("id", id).query(ProductRow.class).single();
    }

    private ProductRow singleStoredProduct() {
        return database.sql(SELECT_PRODUCT).query(ProductRow.class).single();
    }

    private long countProducts() {
        return database.sql("SELECT count(*) FROM products").query(Long.class).single();
    }

    record ProductRow(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        String category,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {}
}
