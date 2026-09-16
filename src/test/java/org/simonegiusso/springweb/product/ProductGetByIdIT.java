package org.simonegiusso.springweb.product;

import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductGetByIdIT extends BaseProductApiIT {

    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-7000-8000-000000000000");

    @Test
    void givenExistingProduct_whenGet_thenReturnIt() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        defaultClient().get()
            .uri(basePath() + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("stored-product.json"), STRICT);
    }

    @Test
    void givenUnknownId_whenGet_thenReturnProblemDetail() {
        defaultClient().get()
            .uri(basePath() + "/{id}", UNKNOWN_ID).exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("get-product-not-found.json", UNKNOWN_ID), STRICT);
    }
}
