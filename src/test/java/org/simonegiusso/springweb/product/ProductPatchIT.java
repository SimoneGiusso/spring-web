package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.envers.RevisionType.MOD;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.simonegiusso.springweb.support.ProductTestFactory.SEEDED_AT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import org.junit.jupiter.api.Test;

class ProductPatchIT extends BaseProductApiIT {

    @Test
    void givenExistingProduct_whenPatchSomeFields_thenUpdateOnlyThoseFields() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        defaultClient().patch()
            .uri(basePath() + "/{id}", ESPRESSO_MACHINE_ID)
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
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        defaultClient().patch()
            .uri(basePath() + "/{id}", ESPRESSO_MACHINE_ID)
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
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        defaultClient().patch()
            .uri(basePath() + "/{id}", ESPRESSO_MACHINE_ID)
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
