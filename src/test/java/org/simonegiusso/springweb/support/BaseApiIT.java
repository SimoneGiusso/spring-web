package org.simonegiusso.springweb.support;

import static org.simonegiusso.springweb.config.security.Permission.READ_WRITE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.Map;
import java.util.UUID;
import org.simonegiusso.springweb.config.security.Permission;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

public abstract class BaseApiIT extends BaseIT {

    @LocalServerPort
    protected int port;

    protected abstract String basePath();

    protected final RestTestClient clientFor(String objectId) {
        return clientFor(objectId, READ_WRITE);
    }

    protected final RestTestClient clientFor(String objectId, Permission... permissions) {
        return clientBearing(MockEntra.tokenFor(objectId, permissions));
    }

    protected final RestTestClient clientBearing(String token) {
        return clientBuilder().defaultHeader(AUTHORIZATION, "Bearer " + token).build();
    }

    protected final RestTestClient anonymousClient() {
        return clientBuilder().build();
    }

    private RestTestClient.Builder<?> clientBuilder() {
        return RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
            .baseUrl("http://localhost:" + port);
    }

    protected final String assertionFile(String fileName) {
        return FileUtils.load(fileName, Map.of("basePath", basePath()));
    }

    protected final String assertionFile(String fileName, UUID id) {
        return FileUtils.load(fileName, Map.of("basePath", basePath(), "id", id));
    }

}
