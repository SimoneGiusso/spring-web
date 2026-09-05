package org.simonegiusso.springweb.support;

import static org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor.PERMISSION_HEADER;
import static org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor.USER_HEADER;
import static org.simonegiusso.springweb.config.web.Permission.READ_WRITE;

import java.util.Map;
import java.util.UUID;
import org.simonegiusso.springweb.config.web.Permission;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

public abstract class BaseApiIT extends BaseIT {

    @LocalServerPort
    protected int port;

    protected abstract String basePath();

    protected RestTestClient clientFor(String user) {
        return clientFor(user, READ_WRITE);
    }

    protected RestTestClient clientFor(String user, Permission permission) {
        return clientBuilder()
            .defaultHeader(USER_HEADER, user)
            .defaultHeader(PERMISSION_HEADER, permission.name())
            .build();
    }

    protected RestTestClient clientWithoutPermissionFor(String user) {
        return clientBuilder().defaultHeader(USER_HEADER, user).build();
    }

    private RestTestClient.Builder<?> clientBuilder() {
        return RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
            .baseUrl("http://localhost:" + port);
    }

    protected String assertionFile(String fileName) {
        return FileUtils.load(fileName, Map.of("basePath", basePath()));
    }

    protected String assertionFile(String fileName, UUID id) {
        return FileUtils.load(fileName, Map.of("basePath", basePath(), "id", id));
    }

}
