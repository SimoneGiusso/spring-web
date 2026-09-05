package org.simonegiusso.springweb.product;

import static java.util.concurrent.TimeUnit.SECONDS;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logs how many products the catalog holds, across every owner.
 *
 * <p>This exists only to prove the system path still works after multi-tenancy was introduced. A tenant is bound
 * only while an API call is served on a user's behalf, so a scheduled task is system work by
 * default. .
 */
@Component
@Slf4j
class ProductCountLogger {

    private final ProductRepository products;

    ProductCountLogger(ProductRepository products) {
        this.products = products;
    }

    @Scheduled(fixedRate = 10, timeUnit = SECONDS)
    void logProductCount() {
        log.info("Products stored: {}", products.count());
    }
}
