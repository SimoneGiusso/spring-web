package org.simonegiusso.springweb.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.stream.Collectors.joining;

import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Reads back what the application exported: Tempo for traces, Prometheus for metrics, Loki for logs.
 */
public record LgtmStack(String tempoUrl, String prometheusUrl, String lokiUrl) {

    /** Wide enough that a slow container start cannot push the window past the data. */
    private static final Duration LOOK_BACK = Duration.ofMinutes(15);

    private static final Duration LOOK_AHEAD = Duration.ofMinutes(1);

    private static final RestClient HTTP = RestClient.create();

    public static LgtmStack at(String tempoUrl, String prometheusUrl, String lokiUrl) {
        return new LgtmStack(tempoUrl, prometheusUrl, lokiUrl);
    }

    public JsonNode searchTraces(String traceQl) {
        return get(tempoUrl + "/api/search", Map.of(
            "q", traceQl,
            "start", String.valueOf(windowStart().getEpochSecond()),
            "end", String.valueOf(windowEnd().getEpochSecond())));
    }

    public JsonNode query(String promQl) {
        return get(prometheusUrl + "/api/v1/query", Map.of("query", promQl));
    }

    /** Log lines matching a LogQL query, for example {@code {service_name="spring-web"}}. */
    public JsonNode queryLogs(String logQl) {
        return get(lokiUrl + "/loki/api/v1/query_range", Map.of(
            "query", logQl,
            "start", String.valueOf(windowStart().getEpochSecond() * 1_000_000_000L),
            "limit", "1000"));
    }

    private static JsonNode get(String url, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
            .map(parameter -> parameter.getKey() + "=" + URLEncoder.encode(parameter.getValue(), UTF_8))
            .collect(joining("&"));
        return HTTP.get().uri(URI.create(url + "?" + query)).retrieve().body(JsonNode.class);
    }

    private static Instant windowStart() {
        return now().minus(LOOK_BACK);
    }

    private static Instant windowEnd() {
        return now().plus(LOOK_AHEAD);
    }
}
