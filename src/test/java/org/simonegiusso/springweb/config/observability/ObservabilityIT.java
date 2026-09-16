package org.simonegiusso.springweb.config.observability;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.ESPRESSO_MACHINE_ID;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.LgtmStack;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;

/**
 * The observability wiring, end to end, against the real stack: one request to the API, then Tempo,
 * Prometheus and Loki are asked whether they received it.
 *
 * <p>Nothing is asserted about the exporters themselves — the assertion is that the data arrived,
 * which is the only claim worth making about a telemetry pipeline. The application is given no
 * endpoint: {@code @ServiceConnection} reads the container's OTLP port and Spring Boot points all
 * three exporters at it.
 *
 * <p>{@code @SpringBootTest} disables metric export and tracing by default, so both are asked for
 * back explicitly. That makes this context different from every other test's, which is why it owns
 * its containers rather than sharing them.
 */
@AutoConfigureMetrics
@AutoConfigureTracing
@TestPropertySource(properties = {
    // Defaults are a minute and five seconds — long enough to make a test look broken.
    "management.otlp.metrics.export.step=2s",
    "management.opentelemetry.tracing.export.schedule-delay=1s"
})
class ObservabilityIT extends BaseApiIT {

    private static final String BASE_PATH = "/api/products";

    @Autowired
    private ProductTestFactory testData;

    @Autowired
    private LgtmStackContainer lgtmContainer;

    private LgtmStack lgtm;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }

    @BeforeEach
    void resolveTheStack() {
        lgtm = LgtmStack.at(lgtmContainer.getTempoUrl(), lgtmContainer.getPrometheusHttpUrl(), lgtmContainer.getLokiUrl());
    }

    @Test
    void givenARequestIsServed_whenTempoIsSearchedByTenant_thenFindTheTraceItProduced() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientFor(ALICE).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk();

        await().atMost(ofSeconds(60)).pollInterval(ofSeconds(2)).untilAsserted(() -> {
            JsonNode traces = lgtm.searchTraces("{ span.tenant = \"%s\" }".formatted(ALICE));
            assertThat(traces.path("traces")).describedAs("traces: %s", traces).isNotEmpty();
            assertThat(traces.path("traces").get(0).path("rootServiceName").asString()).isEqualTo("spring-web");
        });
    }

    @Test
    void givenARequestIsServed_whenPrometheusIsQueried_thenFindTheMeterItIncremented() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientFor(ALICE).get()
            .uri(BASE_PATH + "/{id}", ESPRESSO_MACHINE_ID).exchange()
            .expectStatus().isOk();

        await().atMost(ofSeconds(60)).pollInterval(ofSeconds(2)).untilAsserted(() -> {
            JsonNode result = lgtm.query("{__name__=~\"http_server_requests.*\", uri=\"" + BASE_PATH + "/{id}\"}");
            assertThat(result.path("data").path("result")).describedAs("metrics: %s", result).isNotEmpty();
        });
    }

    @Test
    void givenARequestLogsAWarning_whenLokiIsQueried_thenFindTheLineTaggedWithTheTenant() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);

        clientFor(ALICE).post()
            .uri(BASE_PATH)
            .contentType(APPLICATION_JSON)
            .body(
                """
                    {
                      "sku": "SKU-500100",
                      "name": "Another Espresso Machine",
                      "price": 500.00,
                      "stockQuantity": 1,
                      "category": "HOME"
                    }
                    """).exchange()
            .expectStatus().isEqualTo(CONFLICT);

        await().atMost(ofSeconds(60)).pollInterval(ofSeconds(2)).untilAsserted(() -> {
            JsonNode logs = lgtm.queryLogs(
                "{service_name=\"spring-web\"} | tenant = \"%s\" |= \"Database constraint violated\"".formatted(ALICE));
            assertThat(logs.path("data").path("result")).describedAs("logs: %s", logs).isNotEmpty();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LgtmConfiguration {

        @Bean
        @ServiceConnection
        LgtmStackContainer lgtmContainer() {
            return new LgtmStackContainer("grafana/otel-lgtm:0.32.1");
        }
    }
}
