package org.simonegiusso.springweb.product;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping(path = ProductController.BASE_PATH, produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
class ProductController {

    static final String BASE_PATH = "/api/products";

    private final ProductService products;
    private final ProductMapper mapper;

    @GetMapping("/{id}")
    ProductDTO getById(@PathVariable UUID id) {
        return mapper.toDto(products.findById(id));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<Void> create(
        @Validated(OnCreate.class) @RequestBody ProductDTO request,
        UriComponentsBuilder uriBuilder) {
        Product created = products.create(request);
        URI location = uriBuilder.path(BASE_PATH + "/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).build();
    }

    @PatchMapping(path = "/{id}", consumes = APPLICATION_JSON_VALUE)
    ProductDTO patch(
        @PathVariable UUID id, @Validated(OnPatch.class) @RequestBody ProductDTO request) {
        return mapper.toDto(products.patch(id, request));
    }
}
