package org.simonegiusso.springweb.product;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("No product exists with id " + productId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }
}
