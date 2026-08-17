package org.simonegiusso.springweb.product;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import java.util.UUID;
import org.simonegiusso.springweb.product.validation.OnCreate;
import org.simonegiusso.springweb.product.validation.OnPatch;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/products", produces = APPLICATION_JSON_VALUE)
class ProductController {

    private final ProductService products;

    ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/{id}")
    ProductDTO getById(@PathVariable UUID id) {
        return ProductDTO.from(products.findById(id));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<ProductDTO> create(
        @Validated(OnCreate.class) @RequestBody ProductDTO request,
        UriComponentsBuilder uriBuilder) {
        Product created = products.create(request);
        URI location = uriBuilder.path("/api/products/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(ProductDTO.from(created));
    }

    @PatchMapping(path = "/{id}", consumes = APPLICATION_JSON_VALUE)
    ProductDTO patch(
        @PathVariable UUID id, @Validated(OnPatch.class) @RequestBody ProductDTO request) {
        return ProductDTO.from(products.patch(id, request));
    }
}
