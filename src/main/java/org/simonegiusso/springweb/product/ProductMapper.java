package org.simonegiusso.springweb.product;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

@Mapper(componentModel = ComponentModel.SPRING)
interface ProductMapper {

    ProductDTO toDto(Product product);

    /**
     * Everything a client may not choose is ignored here rather than left to validation elsewhere:
     * the id and version belong to JPA, the timestamps to Spring Data auditing, and the owner to
     * Hibernate's {@code @TenantId}, which assigns the current tenant on insert.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Product toEntity(ProductDTO request);
}
