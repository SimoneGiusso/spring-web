package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;
import org.springframework.util.LinkedMultiValueMap;

class ProductCsvImportIT extends BaseProductApiIT {

    @Autowired
    private ProductRepository repository;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;

    @Override
    protected String basePath() {
        return "/api/products/import";
    }

    @Test
    void givenValidCsvWithQuotedFields_whenImport_thenCreateProducts() {
        defaultClient().post()
            .uri(basePath())
            .contentType(MULTIPART_FORM_DATA)
            .body(file("valid_products.csv"))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody().json(assertionFile("csv/csv-import-created-response.json"), STRICT);

        assertThat(repository.findAll()).hasSize(2);
    }

    @ParameterizedTest
    @MethodSource("invalidCsvFiles")
    void givenInvalidCsv_whenImport_thenRejectWithoutSaving(String csvFile, String expectedBody) {
        defaultClient().post()
            .uri(basePath())
            .contentType(MULTIPART_FORM_DATA)
            .body(file(csvFile))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile(expectedBody), STRICT);

        assertThat(products.count()).isZero();
    }

    private static Stream<Arguments> invalidCsvFiles() {
        return Stream.of(
            Arguments.of("empty_file.csv", "csv/csv-invalid-header-response.json"),
            Arguments.of("invalid_header.csv", "csv/csv-invalid-header-response.json"),
            Arguments.of("header_only.csv", "csv/csv-empty-products-response.json"),
            Arguments.of("invalid_product_constraints.csv", "csv/csv-validation-errors-response.json"),
            Arguments.of("invalid_price_format.csv", "csv/csv-invalid-value-response.json"),
            Arguments.of("unterminated_quote.csv", "csv/csv-malformed-response.json"));
    }

    @Test
    void givenDuplicateSkuInCsv_whenImport_thenRollBackWholeUpload() {
        defaultClient().post()
            .uri(basePath())
            .contentType(MULTIPART_FORM_DATA)
            .body(file("duplicate_sku.csv"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("post-duplicate-sku-conflict-response.json"), STRICT);

        assertThat(products.count()).isZero();
    }

    @Test
    void givenMissingFilePart_whenImport_thenRejectTheRequest() {
        var body = file("valid_products.csv");
        body.add("other", body.remove("file").getFirst());
        defaultClient().post()
            .uri(basePath())
            .contentType(MULTIPART_FORM_DATA)
            .body(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("csv/csv-missing-file-response.json"), STRICT);
    }

    @Test
    void givenFileLargerThanConfiguredLimit_whenImport_thenRejectTheUpload() {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(new byte[Math.toIntExact(maxFileSize.toBytes() + 1)]) {
            @Override
            public String getFilename() {
                return "oversized_products.csv";
            }
        });

        defaultClient().post()
            .uri(basePath())
            .contentType(MULTIPART_FORM_DATA)
            .body(body)
            .exchange()
            .expectStatus().isEqualTo(CONTENT_TOO_LARGE)
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody().json(assertionFile("csv/csv-upload-too-large-response.json"), STRICT);
    }

    private static LinkedMultiValueMap<String, Object> file(String filename) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("input/csv/" + filename));
        return body;
    }
}
