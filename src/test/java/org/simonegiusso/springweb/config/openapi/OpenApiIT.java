package org.simonegiusso.springweb.config.openapi;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseApiIT;

class OpenApiIT extends BaseApiIT {

    @Override
    protected String basePath() {
        return "/v3/api-docs";
    }

    @Test
    void givenNoToken_whenGetTheSpecification_thenServeIt() {
        anonymousClient().get()
            .uri("/v3/api-docs").exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.paths['/api/products'].post").exists()
            .jsonPath("$.paths['/api/products/{id}'].get").exists()
            .jsonPath("$.paths['/api/products/{id}'].patch").exists()
            .jsonPath("$.components.securitySchemes.entra-bearer-token.scheme").isEqualTo("bearer")
            .jsonPath("$.components.schemas.ProductDTO.properties.sku.pattern").isEqualTo("SKU-\\d{6}");
    }

    @Test
    void givenNoToken_whenGetSwaggerUi_thenServeIt() {
        anonymousClient().get()
            .uri("/swagger-ui/index.html").exchange()
            .expectStatus().isOk();
    }
}
