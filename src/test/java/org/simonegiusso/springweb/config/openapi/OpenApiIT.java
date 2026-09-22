package org.simonegiusso.springweb.config.openapi;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.MockEntra;

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
            .jsonPath("$.paths['/api/products/import'].post.requestBody.content['multipart/form-data']").exists()
            .jsonPath("$.paths['/api/products/import'].post.responses['201']").exists()
            .jsonPath("$.paths['/api/products/{id}'].get").exists()
            .jsonPath("$.paths['/api/products/{id}'].patch").exists()
            .jsonPath("$.components.securitySchemes.entra-user-login.type").isEqualTo("oauth2")
            .jsonPath("$.components.securitySchemes.entra-user-login.flows.authorizationCode.scopes.openid")
                .isEqualTo("Sign in with your user identity")
            .jsonPath("$.security[0]['entra-user-login'][0]")
                .isEqualTo("api://" + MockEntra.audience() + "/access_as_user")
            .jsonPath("$.components.securitySchemes.entra-user-login.flows.authorizationCode.authorizationUrl").exists()
            .jsonPath("$.components.securitySchemes.entra-user-login.flows.authorizationCode.tokenUrl").exists()
            .jsonPath("$.components.schemas.ProductDTO.properties.sku.pattern").isEqualTo("SKU-\\d{6}")
            .jsonPath("$.components.schemas.ProductDTO.properties.sku.example").isEqualTo("SKU-100200")
            .jsonPath("$.components.schemas.ProductDTO.properties.owner.readOnly").isEqualTo(true)
            .jsonPath("$.components.schemas.ProductDTO.properties.createdAt.readOnly").isEqualTo(true)
            .jsonPath("$.components.schemas.ProductDTO.properties.updatedAt.readOnly").isEqualTo(true);
    }

    @Test
    void givenNoToken_whenGetOAuthRedirectPage_thenServeIt() {
        anonymousClient().get()
            .uri("/swagger-ui/oauth2-redirect.html").exchange()
            .expectStatus().isOk();
    }

    @Test
    void givenNoToken_whenGetSwaggerUi_thenServeIt() {
        anonymousClient().get()
            .uri("/swagger-ui/index.html").exchange()
            .expectStatus().isOk();
    }
}
