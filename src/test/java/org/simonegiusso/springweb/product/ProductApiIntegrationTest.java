package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.envers.RevisionType.ADD;
import static org.hibernate.envers.RevisionType.MOD;
import static org.simonegiusso.springweb.product.ProductCategory.ELECTRONICS;
import static org.simonegiusso.springweb.product.ProductController.BASE_PATH;
import static org.simonegiusso.springweb.support.ProductTestData.ESPRESSO_MACHINE_ID;
import static org.simonegiusso.springweb.support.ProductTestData.SEEDED_AT;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.AbstractIntegrationTest;
import org.simonegiusso.springweb.support.ProductTestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

class ProductApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-7000-8000-000000000000");

    @Autowired
    private ProductTestData products;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }

    @Test
    void givenExistingProduct_whenGet_thenReturnIt() {
        products.insertAnEspressoMachine();

        client.get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("stored-product.json"), STRICT);
    }

    @Test
    void givenUnknownId_whenGet_thenReturnProblemDetail() {
        client.get()
            .uri(BASE_PATH + "/{id}", UNKNOWN_ID).exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("get-product-not-found.json", UNKNOWN_ID), STRICT);
    }

    @Test
    void givenValidProduct_whenPost_thenCreateItAndReturnItsRepresentation() {
        EntityExchangeResult<byte[]> response =
            client.post()
                .uri(BASE_PATH)
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
                        """).exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody().json(assertionFile("post-created-product.json"), STRICT)
                .returnResult();

        Product stored = products.findOnlyProduct();

        assertThat(response.getResponseHeaders().getLocation())
            .hasToString("http://localhost:" + port + BASE_PATH + "/" + stored.getId());
        assertThat(stored.getSku()).isEqualTo("SKU-100200");
        assertThat(stored.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(stored.getPrice()).isEqualByComparingTo("129.90");
        assertThat(stored.getStockQuantity()).isEqualTo(42);
        assertThat(stored.getCategory()).isEqualTo(ELECTRONICS);
        assertThat(stored.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(stored.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(stored.getVersion()).isZero();
        assertThat(products.revisionTypes()).containsExactly(ADD);
    }

    @Test
    void givenSeveralInvalidFields_whenPost_thenReportEveryViolationAtOnce() {
        client.post()
            .uri(BASE_PATH)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "KEYBOARD",
                      "name": "",
                      "price": 0.00,
                      "stockQuantity": -5,
                      "createdAt": "1999-01-01T00:00:00Z"
                    }
                    """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("post-validation-errors.json"), STRICT);

        assertThat(products.countProducts()).isZero();
    }

    @Test
    void givenAlreadyUsedSku_whenPost_thenReportAConflict() {
        products.insertAnEspressoMachine();

        client.post()
            .uri(BASE_PATH)
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
            .expectStatus().isEqualTo(CONFLICT)
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("post-duplicate-sku-conflict.json"), STRICT);

        assertThat(products.countProducts()).isOne();
        assertThat(products.revisionTypes()).isEmpty();
    }

    @Test
    void givenExistingProduct_whenPatchSomeFields_thenUpdateOnlyThoseFields() {
        products.insertAnEspressoMachine();

        client.patch()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "price": 799.50,
                      "stockQuantity": 3
                    }
                    """)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("patch-updated-product.json"), STRICT);

        Product stored = products.findBy(ESPRESSO_MACHINE_ID);
        assertThat(stored.getPrice()).isEqualByComparingTo("799.50");
        assertThat(stored.getStockQuantity()).isEqualTo(3);
        assertThat(stored.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(stored.getVersion()).isOne();
        assertThat(products.revisionTypes()).containsExactly(MOD);
    }

    @Test
    void givenSkuInTheBody_whenPatch_thenRejectTheRequest() {
        products.insertAnEspressoMachine();

        client.patch()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-999999",
                      "price": 799.50
                    }
                    """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("patch-sku-rejected.json", ESPRESSO_MACHINE_ID), STRICT);

        Product stored = products.findBy(ESPRESSO_MACHINE_ID);
        assertThat(stored.getSku()).isEqualTo("SKU-500100");
        assertThat(stored.getPrice()).isEqualByComparingTo("899.00");
        assertThat(stored.getVersion()).isZero();
        assertThat(products.revisionTypes()).isEmpty();
    }

    @Test
    void givenEmptyBody_whenPatch_thenLeaveTheProductUntouched() {
        products.insertAnEspressoMachine();

        client.patch()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body("{}")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("stored-product.json"), STRICT);

        Product stored = products.findBy(ESPRESSO_MACHINE_ID);
        assertThat(stored.getUpdatedAt()).isEqualTo(SEEDED_AT);
        assertThat(stored.getVersion()).isZero();
        assertThat(products.revisionTypes()).isEmpty();
    }
}
