package org.simonegiusso.springweb.support;

import static java.time.ZoneOffset.UTC;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.simonegiusso.springweb.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-08-17T10:15:30Z");
    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcClient database;
    protected RestTestClient client;
    @TestBean
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(FIXED_NOW, UTC);
    }

    @BeforeEach
    void prepareClientAndCleanDatabase() {
        client =
            RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
        database.sql("TRUNCATE TABLE products").update();
    }
}
