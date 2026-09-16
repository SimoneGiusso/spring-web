package org.simonegiusso.springweb.product;

import static com.fasterxml.jackson.dataformat.csv.CsvParser.Feature.FAIL_ON_MISSING_COLUMNS;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.simonegiusso.springweb.product.validation.OnCreate;
import org.simonegiusso.springweb.utils.ClassAnalyzer;
import org.simonegiusso.springweb.utils.HTTPResponse;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
class ProductCsvImporter {

    private final Validator validator;
    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final CsvMapper csvMapper;

    private final List<String> writableColumns;

    ProductCsvImporter(Validator validator, ProductRepository repository, ProductMapper mapper, ClassAnalyzer classAnalyzer) {
        this.validator = validator;
        this.repository = repository;
        this.mapper = mapper;
        csvMapper = CsvMapper.builder()
            .enable(FAIL_ON_MISSING_COLUMNS)
            .build();

        writableColumns = classAnalyzer.fieldsWithoutNullConstraint(ProductDTO.class);
    }

    @Transactional
    public int importFile(InputStreamSource file) {
        List<Product> products = parse(file).stream()
            .map(mapper::toEntity)
            .toList();
        return repository.saveAll(products).size();
    }

    private List<ProductDTO> parse(InputStreamSource file) {
        try {
            validateHeader(file);
            return readProducts(file);
        } catch (IOException | UncheckedIOException ex) {
            log.warn(ex.getMessage(), ex);
            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
            throw HTTPResponse.invalid("Unable to read CSV. Supply valid UTF-8 CSV with properly quoted fields.");
        }
    }

    private void validateHeader(InputStreamSource file) throws IOException {
        String [] header = readHeader(file);
        if (!Arrays.equals(header, writableColumns.toArray(String[]::new))) {
            throw HTTPResponse.invalid("Expected CSV header: " + String.join(",", writableColumns));
        }
    }

    private static String[] readHeader(InputStreamSource file) throws IOException {
        try (var input = new BufferedReader(new InputStreamReader(file.getInputStream(), UTF_8.newDecoder()))) {
            String header = input.readLine();
            return header == null ? null : header.split(",", -1);
        }
    }

    private List<ProductDTO> readProducts(InputStreamSource file) throws IOException {
        List<ProductDTO> rows = new ArrayList<>();
        long recordNumber = 2;

        try (var input = new InputStreamReader(file.getInputStream(), UTF_8.newDecoder());
            MappingIterator<ProductDTO> products = csvMapper.readerFor(ProductDTO.class)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(input)) {
            while (products.hasNextValue()) {
                ProductDTO product = readProduct(products, recordNumber);
                validateProduct(product, recordNumber);
                rows.add(product);
                recordNumber++;
            }
        }
        if (rows.isEmpty()) {
            throw HTTPResponse.invalid("CSV must contain at least one product.");
        }
        return rows;
    }

    private static ProductDTO readProduct(MappingIterator<ProductDTO> products, long recordNumber) throws IOException {
        try {
            return products.nextValue();
        } catch (JsonMappingException ex) {
            throw HTTPResponse.invalid("CSV record " + recordNumber + ": " + ex.getMessage());
        }
    }

    private void validateProduct(ProductDTO product, long recordNumber) {
        var violations = validator.validate(product, OnCreate.class);
        if (violations.isEmpty()) {
            return;
        }
        String details = violations.stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        throw HTTPResponse.invalid("CSV record " + recordNumber + ": " + details);
    }

}
