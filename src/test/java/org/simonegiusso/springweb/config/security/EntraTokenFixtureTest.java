package org.simonegiusso.springweb.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.MockEntra;
import tools.jackson.databind.JsonNode;

/**
 * Checks the documented delegated-token shape against the synthetic fixture.
 * A development-tenant token can be decoded with {@code scripts/capture-entra-token.sh}.
 */
class EntraTokenFixtureTest {

    private final JsonNode decodedToken = MockEntra.decodedToken();

    @Test
    void theFixtureUsesAV2TokenWithTheBareClientIdAudience() {
        assertThat(decodedToken.get("iss").asString()).endsWith("/v2.0");
        assertThat(decodedToken.get("ver").asString()).isEqualTo("2.0");
        assertThat(decodedToken.get("aud").asString()).doesNotStartWith("api://");
    }

    @Test
    void theSuiteMintsTokensForTheFixtureAudience() {
        assertThat(MockEntra.audience()).isEqualTo(decodedToken.get("aud").asString());
    }

    @Test
    void theTokenRepresentsAUser() {
        assertThat(decodedToken.get("sub").asString()).isNotEqualTo(decodedToken.get("oid").asString());
        assertThat(decodedToken.get("iss").asString()).contains(decodedToken.get("tid").asString());
    }

    @Test
    void theTokenCarriesBothRolesAndTheDelegatedScope() {
        assertThat(decodedToken.has("roles")).isTrue();
        assertThat(decodedToken.get("scp").asString()).isEqualTo("access_as_user");
    }
}
