package org.simonegiusso.springweb.support;

import static java.time.ZoneOffset.UTC;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Clock;
import java.time.Instant;
import org.simonegiusso.springweb.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Sql("/sql-scripts/truncate-tables.sql")
public abstract class BaseIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-08-17T10:15:30Z");

    @TestBean
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(FIXED_NOW, UTC);
    }
}
