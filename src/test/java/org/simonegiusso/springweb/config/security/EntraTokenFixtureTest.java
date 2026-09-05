package org.simonegiusso.springweb.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.MockEntra;
import tools.jackson.databind.JsonNode;

/**
 * Guards the assumptions behind the token configuration against a token actually observed from the
 * tenant, captured by {@code scripts/capture-entra-token.sh}. Without it the suite would only
 * confirm that the mock agrees with whatever the configuration already assumed.
 */
class EntraTokenFixtureTest {

    private final JsonNode decodedToken = MockEntra.decodedToken();

    @Test
    void theObservedTokenIsAV2TokenSoItsAudienceIsTheBareClientId() {
        assertThat(decodedToken.get("iss").asString()).endsWith("/v2.0");
        assertThat(decodedToken.get("ver").asString()).isEqualTo("2.0");
        assertThat(decodedToken.get("aud").asString()).doesNotStartWith("api://");
    }

    @Test
    void theSuiteMintsTokensForTheAudienceThatWasObserved() {
        assertThat(MockEntra.audience()).isEqualTo(decodedToken.get("aud").asString());
    }

    @Test
    void theObservedTokenWasIssuedToAnApplicationRatherThanAUser() {
        assertThat(decodedToken.get("sub").asString()).isEqualTo(decodedToken.get("oid").asString());
        assertThat(decodedToken.get("iss").asString()).contains(decodedToken.get("tid").asString());
    }

    @Test
    void theObservedTokenCarriesRolesRatherThanScopes() {
        assertThat(decodedToken.has("roles")).isTrue();
        assertThat(decodedToken.has("scp")).isFalse();
    }
}
