package org.simonegiusso.springweb.config.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.stereotype.Component;

/**
 * Hands the auto-configured {@link OpenTelemetry} instance to the Logback appender declared in
 * {@code logback-spring.xml}.
 */
@Component
class OpenTelemetryLogbackInstaller {

    OpenTelemetryLogbackInstaller(OpenTelemetry openTelemetry) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
