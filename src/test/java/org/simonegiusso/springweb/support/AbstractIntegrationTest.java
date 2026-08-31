package org.simonegiusso.springweb.support;

import static java.time.ZoneOffset.UTC;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.simonegiusso.springweb.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = "spring.docker.compose.enabled=false")
@Import({TestcontainersConfiguration.class, ProductTestData.class})
@Sql("/sql-scripts/truncate-tables.sql")
public abstract class AbstractIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-08-17T10:15:30Z");
    @LocalServerPort
    protected int port;
    protected RestTestClient client;
    @TestBean
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(FIXED_NOW, UTC);
    }

    protected abstract String basePath();

    protected String assertionFile(String fileName) {
        return FileUtils.load(fileName, Map.of("basePath", basePath()));
    }

    protected String assertionFile(String fileName, UUID id) {
        return FileUtils.load(fileName, Map.of("basePath", basePath(), "id", id));
    }

    @BeforeEach
    void prepareClient() {
        client =
            RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
