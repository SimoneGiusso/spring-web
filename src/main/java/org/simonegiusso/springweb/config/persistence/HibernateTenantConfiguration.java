package org.simonegiusso.springweb.config.persistence;

import static org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.simonegiusso.springweb.config.web.CurrentUser;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Tells Hibernate which tenant a session belongs to, and registers itself in Spring
 * Boot when it builds the {@code EntityManagerFactory}.
 */
@Component
@RequiredArgsConstructor
class HibernateTenantConfiguration implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    static final String SYSTEM = "__system__";

    private final CurrentUser currentUser;

    @Override
    public String resolveCurrentTenantIdentifier() {
        return isARequest() ? currentUser.name() : SYSTEM;
    }

    private static boolean isARequest() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return SYSTEM.equals(tenantId) || (isARequest() && currentUser.permission().canReadEveryOwner());
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
