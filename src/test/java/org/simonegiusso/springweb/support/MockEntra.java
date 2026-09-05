package org.simonegiusso.springweb.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import org.simonegiusso.springweb.config.security.Permission;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stands in for Entra ID: a real authorization server on localhost, so the application runs its real
 * {@code JwtDecoder} against real JWKS and real signatures.
 *
 * <p>The audience is read from {@code entra/decoded-token.json} rather than invented, so the
 * tokens the suite mints carry whatever a real token carried.
 */
public enum MockEntra {
    ;

    private static final String ISSUER_ID = "entra";

    private static final JsonNode DECODED_TOKEN = readDecodedToken();
    private static final String AUDIENCE = DECODED_TOKEN.get("aud").asString();
    private static final MockOAuth2Server O_AUTH_2_SERVER = start();

    private static MockOAuth2Server start() {
        MockOAuth2Server server = new MockOAuth2Server();
        server.start(0);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        return server;
    }

    static void registerIssuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> O_AUTH_2_SERVER.issuerUrl(ISSUER_ID).toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
    }

    public static String audience() {
        return AUDIENCE;
    }

    public static JsonNode decodedToken() {
        return DECODED_TOKEN;
    }

    public static String tokenWithBrokenSignature(String objectId, Permission... permissions) {
        String[] token = tokenFor(objectId, permissions).split("\\.");
        String[] signedOverOtherClaims = tokenFor(objectId + "-someone-else", permissions).split("\\.");
        String anotherTokenSignature = signedOverOtherClaims[2];
        return token[0] + "." + token[1] + "." + anotherTokenSignature;
    }

    static String tokenFor(String objectId, Permission... permissions) {
        return token(objectId, AUDIENCE, 3600, permissions);
    }

    public static String tokenFor(String objectId, String audience, Permission... permissions) {
        return token(objectId, audience, 3600, permissions);
    }

    public static String expiredTokenFor(String objectId, Permission... permissions) {
        return token(objectId, AUDIENCE, -60, permissions);
    }

    private static String token(String objectId, String audience, long expirySeconds, Permission... permissions) {
        List<String> roles = Arrays.stream(permissions).map(Permission::role).toList();
        SignedJWT jwt = O_AUTH_2_SERVER.issueToken(
            ISSUER_ID,
            objectId,
            new DefaultOAuth2TokenCallback(
                ISSUER_ID,
                objectId,
                "JWT",
                List.of(audience),
                Map.of("oid", objectId, "roles", roles, "idtyp", "app"),
                expirySeconds));
        return jwt.serialize();
    }

    private static JsonNode readDecodedToken() {
        try {
            return new ObjectMapper().readTree(
                new ClassPathResource("entra/decoded-token.json").getContentAsString(UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read the decoded Entra token fixture", exception);
        }
    }
}
