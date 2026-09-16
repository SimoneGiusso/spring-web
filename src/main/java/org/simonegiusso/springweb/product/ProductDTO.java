package org.simonegiusso.springweb.product;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(example = "SKU-100200")
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnPatch.class, message = "must not be provided in a patch request")
    @Pattern(regexp = "SKU-\\d{6}", message = "must match SKU-<6 digits>")
    String sku,

    @Schema(example = "Mechanical Keyboard")
    @NotNull(groups = OnCreate.class)
    @Size(min = 1, max = 120)
    String name,

    @Schema(example = "Compact 75% layout with hot-swappable switches")
    @Size(max = 2000)
    String description,

    @Schema(example = "129.90")
    @NotNull(groups = OnCreate.class)
    @DecimalMin(value = "0.00", inclusive = false)
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,

    @Schema(example = "42")
    @NotNull(groups = OnCreate.class)
    @PositiveOrZero @Max(1_000_000)
    Integer stockQuantity,

    @Schema(example = "ELECTRONICS")
    @NotNull(groups = OnCreate.class)
    ProductCategory category,

    @Schema(accessMode = READ_ONLY, example = "alice")
    @Null(message = "is read-only and must not be provided")
    String owner,

    @Schema(accessMode = READ_ONLY, example = "2026-09-16T13:54:03.207Z")
    @Null(message = "is read-only and must not be provided")
    Instant createdAt,

    @Schema(accessMode = READ_ONLY, example = "2026-09-16T13:54:03.207Z")
    @Null(message = "is read-only and must not be provided")
    Instant updatedAt
) {}
