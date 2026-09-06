package org.simonegiusso.springweb.config.security;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is an OAuth2 resource server for Microsoft Entra ID. Callers are service principals using
 * the client-credentials flow, so there is no session, no CSRF token and no login redirect: every
 * request carries its own bearer token.
 *
 * <p>The decoder and the claim mapping are auto-configured from
 * {@code spring.security.oauth2.resourceserver.jwt.*}; only the parts Spring Boot cannot infer are
 * declared here.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemSecurityHandler problems) {
        return http
            // Safe because the credential is never ambient: a browser attaches cookies on its own,
            // never an Authorization header, so a forged cross-site request carries no authority.
            // This stops being true the day anything here authenticates a cookie or Basic auth.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .build();
    }
}
