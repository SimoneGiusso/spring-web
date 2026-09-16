package org.simonegiusso.springweb.config.observability;

import io.micrometer.common.KeyValue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Names the tenant on everything a request emits: as a span attribute, so a trace can be found by
 * owner in Tempo, and in the MDC, so the log lines it produced can be found by the same owner in
 * Loki.
 *
 * <p>The attribute is added as a <em>high</em>-cardinality key value. Micrometer puts those on the
 * span only, while low-cardinality ones also become metric tags
 *
 * <p>Registration order is what makes the caller visible: Spring Boot registers the security chain
 * at {@code -100} and an unordered filter last, so this one runs nested inside it, after the bearer
 * token has been decoded and before the {@code SecurityContext} is cleared again.
 */
@Component
class TenantObservabilityFilter extends OncePerRequestFilter {

    private static final String TENANT = "tenant";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String tenant = extractTenant(SecurityContextHolder.getContext().getAuthentication());

        if (tenant == null) {
            chain.doFilter(request, response);
            return;
        }

        addToObservation(request, tenant);
        MDC.put(TENANT, tenant);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TENANT);
        }
    }

    private static String extractTenant(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token ? token.getName() : null;
    }

    private static void addToObservation(HttpServletRequest request, String tenant) {
        ServerHttpObservationFilter.findObservationContext(request)
            .ifPresent(observation -> observation.addHighCardinalityKeyValue(KeyValue.of(TENANT, tenant)));
    }
}
