package org.simonegiusso.springweb.product;

import static org.simonegiusso.springweb.config.security.Roles.READ;
import static org.simonegiusso.springweb.config.security.Roles.READ_ALL;
import static org.simonegiusso.springweb.config.security.Roles.READ_WRITE;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.simonegiusso.springweb.product.validation.OnCreate;
import org.simonegiusso.springweb.product.validation.OnPatch;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(path = ProductController.BASE_PATH, produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
class ProductController {

    static final String BASE_PATH = "/api/products";

    private final ProductService products;
    private final ProductMapper mapper;
    private final ProductCsvImporter csvImporter;

    @Operation(summary = "Import products from a CSV file")
    @PreAuthorize("hasAuthority('" + READ_WRITE + "')")
    @PostMapping(path = "/import", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(CREATED)
    ImportResult importCsv(@RequestPart("file") MultipartFile file) {
        return new ImportResult(csvImporter.importFile(file));
    }

    @PreAuthorize("hasAnyAuthority('" + READ + "', '" + READ_WRITE + "', '" + READ_ALL + "')")
    @GetMapping("/{id}")
    ProductDTO getById(@PathVariable UUID id) {
        return mapper.toDto(products.findById(id));
    }

    @PreAuthorize("hasAuthority('" + READ_WRITE + "')")
    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<Void> create(
        @Validated(OnCreate.class) @RequestBody ProductDTO request,
        UriComponentsBuilder uriBuilder) {
        Product created = products.create(request);
        URI location = uriBuilder.path(BASE_PATH + "/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).build();
    }

    @PreAuthorize("hasAuthority('" + READ_WRITE + "')")
    @PatchMapping(path = "/{id}", consumes = APPLICATION_JSON_VALUE)
    ProductDTO patch(
        @PathVariable UUID id, @Validated(OnPatch.class) @RequestBody ProductDTO request) {
        return mapper.toDto(products.patch(id, request));
    }

    private record ImportResult(int imported) {}
}
