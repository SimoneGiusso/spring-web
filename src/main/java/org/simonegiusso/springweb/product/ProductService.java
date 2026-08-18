package org.simonegiusso.springweb.product;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;

    ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public Product create(ProductDTO request) {
        return products.save(request.toProduct());
    }

    @Transactional
    public Product patch(UUID id, ProductDTO request) {
        Product product = findById(id);
        applyIfPresent(request.name(), product::setName);
        applyIfPresent(request.description(), product::setDescription);
        applyIfPresent(request.price(), product::setPrice);
        applyIfPresent(request.stockQuantity(), product::setStockQuantity);
        applyIfPresent(request.category(), product::setCategory);
        return product;
    }

    Product findById(UUID id) {
        return products.findById(id).orElseThrow(() -> new NoSuchElementException("No product exists with id " + id));
    }

    private static <T> void applyIfPresent(T value, Consumer<T> update) {
        if (value != null) {
            update.accept(value);
        }
    }
}
