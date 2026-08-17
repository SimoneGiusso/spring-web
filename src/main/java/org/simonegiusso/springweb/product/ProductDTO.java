package org.simonegiusso.springweb.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import org.simonegiusso.springweb.product.validation.OnCreate;
import org.simonegiusso.springweb.product.validation.OnPatch;

public record ProductDTO(
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnPatch.class, message = "must not be provided in a patch request")
    @Pattern(regexp = "SKU-\\d{6}", message = "must match SKU-<6 digits>")
    String sku,

    @NotNull(groups = OnCreate.class)
    @Size(min = 1, max = 120)
    String name,

    @Size(max = 2000)
    String description,

    @NotNull(groups = OnCreate.class)
    @DecimalMin(value = "0.00", inclusive = false)
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,

    @NotNull(groups = OnCreate.class)
    @PositiveOrZero @Max(1_000_000)
    Integer stockQuantity,

    @NotNull(groups = OnCreate.class)
    ProductCategory category,

    @Null(message = "is read-only and must not be provided")
    Instant createdAt,

    @Null(message = "is read-only and must not be provided")
    Instant updatedAt
) {

    static ProductDTO from(Product product) {
        return new ProductDTO(
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getCategory(),
            product.getCreatedAt(),
            product.getUpdatedAt());
    }
}
