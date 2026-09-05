package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simonegiusso.springweb.config.web.Permission.READ;
import static org.simonegiusso.springweb.config.web.Permission.READ_ALL;
import static org.simonegiusso.springweb.product.ProductController.BASE_PATH;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.BOB;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.simonegiusso.springweb.support.ProductTestFactory.KEYBOARD_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.simonegiusso.springweb.support.TestProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

class ProductAccessControlIT extends BaseApiIT {

    @Autowired
    private ProductTestFactory testData;

    @Autowired
    private TestProductRepository products;

    private RestTestClient bob;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }

    @BeforeEach
    void prepareClients() {
        bob = clientFor(BOB);
    }

    @Test
    void givenProductOwnedByAnotherUser_whenGet_thenHideItBehindNotFound() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        bob.get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("get-product-not-found.json", ESPRESSO_MACHINE_ID), STRICT);
    }

    @Test
    void givenReadAll_whenGet_thenSeeTheProductsOfEveryOwner() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);
        testData.insertAKeyboardOwnedBy(BOB);

        RestTestClient auditor = clientFor(ALICE, READ_ALL);

        auditor.get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectBody().json(assertionFile("stored-product.json"), STRICT);

        auditor.get()
            .uri(BASE_PATH + "/{id}", KEYBOARD_ID).exchange()
            .expectStatus().isOk()
            .expectBody().json(assertionFile("stored-keyboard.json"), STRICT);
    }

    @Test
    void givenReadOnly_whenPost_thenForbidItAndCreateNothing() {
        clientFor(ALICE, READ).post()
            .uri(BASE_PATH)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-100200",
                      "name": "Mechanical Keyboard",
                      "price": 129.90,
                      "stockQuantity": 42,
                      "category": "ELECTRONICS"
                    }
                    """).exchange()
            .expectStatus().isForbidden()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("post-forbidden.json"), STRICT);

        assertThat(products.count()).isZero();
    }

    @Test
    void givenReadAll_whenPatch_thenForbidItBecauseReadingEveryOwnerGrantsNoWrites() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientFor(ALICE, READ_ALL).patch()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID)
            .contentType(APPLICATION_JSON)
            .body("{\"price\": 1.00}").exchange()
            .expectStatus().isForbidden();

        assertThat(products.findBy(ESPRESSO_MACHINE_ID).getPrice()).isNotEqualByComparingTo("1.00");
    }

    @Test
    void givenNoPermissionHeader_whenGet_thenRejectTheRequest() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientWithoutPermissionFor(ALICE).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isForbidden()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("missing-permission.json", ESPRESSO_MACHINE_ID), STRICT);
    }

}
