package org.simonegiusso.springweb.config.openapi;

import static io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc derives the paths, schemas and constraints from the controller and the DTO's Bean
 * Validation annotations. Only what it cannot infer is declared here.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER_TOKEN = "entra-bearer-token";

    @Bean
    OpenAPI catalogApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Product catalog")
                .version("v1")
                .description("""
                    Multi-tenant product catalog. A product belongs to the service principal that \
                    created it, identified by the `oid` claim of the bearer token, and is visible \
                    only to that principal — or to a caller holding `Catalog.Read.All`.

                    Roles come from the `roles` claim: `Catalog.Read` reads own products, \
                    `Catalog.ReadWrite` also modifies them, `Catalog.Read.All` reads every owner's \
                    and modifies none. A product belonging to someone else answers `404`, never \
                    `403`, which would confirm that it exists."""))
            .components(new Components().addSecuritySchemes(BEARER_TOKEN, new SecurityScheme()
                .type(HTTP)
                .in(HEADER)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("A client-credentials access token issued by Microsoft Entra ID.")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_TOKEN));
    }
}
