package org.simonegiusso.springweb.config.openapi;

import static io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc derives the paths, schemas and constraints from the controller and the DTO's Bean
 * Validation annotations. Only what it cannot infer is declared here.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String USER_LOGIN = "entra-user-login";

    @Bean
    OpenAPI catalogApi(
        @Value("${catalog.security.authorization-url}") String authorizationUrl,
        @Value("${catalog.security.token-url}") String tokenUrl,
        @Value("${catalog.security.scope}") String scope) {
        return new OpenAPI()
            .info(new Info()
                .title("Product catalog")
                .version("v1")
                .description("""
                    Multi-tenant product catalog. A product belongs to the user that \
                    created it, identified by the `oid` claim of the bearer token, and is visible \
                    only to that user — or to a caller holding `Catalog.Read.All`.

                    Roles come from the `roles` claim: `Catalog.Read` reads own products, \
                    `Catalog.ReadWrite` also modifies them, `Catalog.Read.All` reads every owner's \
                    and modifies none. A product belonging to someone else answers `404`, never \
                    `403`, which would confirm that it exists."""))
            .components(new Components().addSecuritySchemes(USER_LOGIN, new SecurityScheme()
                .type(OAUTH2)
                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                    .authorizationUrl(authorizationUrl)
                    .tokenUrl(tokenUrl)
                    .scopes(new Scopes()
                        .addString("openid", "Sign in with your user identity")
                        .addString(scope, "Access the catalog as the signed-in user"))))
                .description("Sign in with Microsoft Entra ID using authorization code with PKCE.")))
            .addSecurityItem(new SecurityRequirement().addList(USER_LOGIN, scope));
    }
}
