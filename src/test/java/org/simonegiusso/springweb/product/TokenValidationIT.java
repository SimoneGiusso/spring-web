package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simonegiusso.springweb.product.ProductController.BASE_PATH;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.config.security.Permission;
import org.simonegiusso.springweb.config.security.Roles;
import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.MockEntra;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * The decoder itself: real JWKS, real signatures, real validation against a local authorization
 * server. These cover only whether a token is accepted at all; the role and tenancy matrix is
 * exercised separately.
 */
class TokenValidationIT extends BaseApiIT {

    @Autowired
    private ProductTestFactory testData;

    @Autowired
    private JwtAuthenticationConverter converter;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }

    @Test
    void givenAValidToken_whenGet_thenAcceptIt() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientFor(ALICE, Permission.READ).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk()
            .expectBody().json(assertionFile("stored-product.json"), STRICT);
    }

    @Test
    void givenATokenForAnotherAudience_whenGet_thenRejectIt() {
        clientBearing(MockEntra.tokenFor(ALICE, "api://" + MockEntra.audience(), Permission.READ)).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void givenAnExpiredToken_whenGet_thenRejectIt() {
        clientBearing(MockEntra.expiredTokenFor(ALICE, Permission.READ)).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void givenATamperedSignature_whenGet_thenRejectIt() {
        clientBearing(MockEntra.tokenWithBrokenSignature(ALICE, Permission.READ)).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isUnauthorized();
    }

    /**
     * Pins the two claim mappings the configuration cannot be trusted to have got right: an empty
     * authority prefix, so a role is not silently renamed to {@code SCOPE_Catalog.Read}, and
     * {@code oid} as the principal, which is the value that lands in the tenant column.
     */
    @Test
    void givenARoleClaim_whenConverted_thenTheAuthorityIsTheRoleAndThePrincipalIsTheObjectId() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("oid", ALICE)
            .claim("roles", List.of(Roles.READ))
            .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .contains(Roles.READ)
            .noneMatch(authority -> authority.startsWith("SCOPE_"));
        assertThat(authentication.getName()).isEqualTo(ALICE);
    }
}
