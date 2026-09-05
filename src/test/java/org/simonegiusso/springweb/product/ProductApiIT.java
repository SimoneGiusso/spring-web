package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.envers.RevisionType.ADD;
import static org.hibernate.envers.RevisionType.MOD;
import static org.simonegiusso.springweb.product.ProductController.BASE_PATH;
import static org.simonegiusso.springweb.support.ProductTestFactory.ADMIN;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.BOB;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.simonegiusso.springweb.support.ProductTestFactory.KEYBOARD_ID;
import static org.simonegiusso.springweb.support.ProductTestFactory.SEEDED_AT;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.simonegiusso.springweb.support.TestProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

class ProductApiIT extends BaseApiIT {

    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-7000-8000-000000000000");

    @Autowired
    private ProductTestFactory testData;

    @Autowired
    private TestProductRepository products;

    private RestTestClient admin;
    private RestTestClient alice;
    private RestTestClient bob;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }

    @BeforeEach
    void prepareClients() {
        alice = clientFor(ALICE);
        bob = clientFor(BOB);
        admin = clientFor(ADMIN);
    }

    @Test
    void givenExistingProduct_whenGet_thenReturnIt() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        alice.get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("stored-product.json"), STRICT);
    }

    @Test
    void givenUnknownId_whenGet_thenReturnProblemDetail() {
        alice.get()
            .uri(BASE_PATH + "/{id}", UNKNOWN_ID).exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("get-product-not-found.json", UNKNOWN_ID), STRICT);
    }

    @Test
    void givenValidProduct_whenPost_thenCreateItAndPointAtIt() {
        URI location = alice.post()
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
                .expectBody().isEmpty()
                .getResponseHeaders().getLocation();

        assertProductCreation(alice, location, "post-created-product.json");
    }

    @Test
    void givenSeveralInvalidFields_whenPost_thenReportEveryViolationAtOnce() {
        alice.post()
            .uri(BASE_PATH)
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

        alice.post()
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

        assertThat(products.count()).isOne();
        assertThat(products.revisionTypes()).isEmpty();
    }

    @Test
    void givenExistingProduct_whenPatchSomeFields_thenUpdateOnlyThoseFields() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        alice.patch()
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
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        alice.patch()
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
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        alice.patch()
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

    @Test
    void givenAdmin_whenGet_thenSeeTheProductsOfEveryOwner() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);
        testData.insertAKeyboardOwnedBy(BOB);

        admin.get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectBody().json(assertionFile("stored-product.json"), STRICT);

        admin.get()
            .uri(BASE_PATH + "/{id}", KEYBOARD_ID).exchange()
            .expectStatus().isOk()
            .expectBody().json(assertionFile("stored-keyboard.json"), STRICT);
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

    private void assertProductCreation(RestTestClient client, URI location, String jsonFile) {
        client.get()
            .uri(location).exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile(jsonFile), STRICT);

        assertThat(products.revisionTypes()).containsExactly(ADD);
    }

}
