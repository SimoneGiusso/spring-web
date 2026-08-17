package org.simonegiusso.springweb.product;

public class DuplicateSkuException extends RuntimeException {

    private final String sku;

    public DuplicateSkuException(String sku) {
        super("A product with sku " + sku + " already exists");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
