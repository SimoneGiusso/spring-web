package org.simonegiusso.springweb.config.security;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless resource server for delegated user access tokens.
 * Swagger UI handles the authorization-code login and sends bearer tokens to this API.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemSecurityHandler problems,
        @Value("${catalog.security.required-scope}") String requiredScope) {
        return http
            // Safe because the credential is never ambient: a browser attaches cookies on its own,
            // never an Authorization header, so a forged cross-site request carries no authority.
            // This stops being true the day anything here authenticates a cookie or Basic auth.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().access((authentication, context) -> {
                    var caller = authentication.get();
                    boolean allowed = caller instanceof JwtAuthenticationToken jwt
                        && jwt.isAuthenticated()
                        && hasScope(jwt, requiredScope);
                    return new AuthorizationDecision(allowed);
                }))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .build();
    }

    private static boolean hasScope(JwtAuthenticationToken authentication, String requiredScope) {
        String scopes = authentication.getToken().getClaimAsString("scp");
        return scopes != null && Arrays.asList(scopes.split(" ")).contains(requiredScope);
    }
}
