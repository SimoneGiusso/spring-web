package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.envers.RevisionType.ADD;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ProductPostIT extends BaseProductApiIT {

    @Test
    void givenValidProduct_whenPost_thenCreateItAndPointAtIt() {
        URI location = defaultClient().post()
            .uri(basePath())
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
            .expectStatus().isCreated()
            .expectBody().isEmpty()
            .getResponseHeaders().getLocation();

        defaultClient().get()
            .uri(location).exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("post-created-product.json"), STRICT);

        assertThat(products.revisionTypes()).containsExactly(ADD);
    }

    @Test
    void givenSeveralInvalidFields_whenPost_thenReportEveryViolationAtOnce() {
        defaultClient().post()
            .uri(basePath())
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "KEYBOARD",
                      "name": "",
                      "price": 0.00,
                      "stockQuantity": -5,
                      "owner": "bob",
                      "createdAt": "1999-01-01T00:00:00Z"
                    }
                    """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("post-validation-errors.json"), STRICT);

        assertThat(products.count()).isZero();
    }

    @Test
    void givenAlreadyUsedSku_whenPost_thenReportAConflict() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        defaultClient().post()
            .uri(basePath())
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
            .expectBody().json(assertionFile("post-duplicate-sku-conflict-response.json"), STRICT);

        assertThat(products.count()).isOne();
        assertThat(products.revisionTypes()).isEmpty();
    }
}
