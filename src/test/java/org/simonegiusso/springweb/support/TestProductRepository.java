package org.simonegiusso.springweb.support;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.RevisionType;
import org.simonegiusso.springweb.product.Product;
import org.simonegiusso.springweb.product.ProductRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TestProductRepository {

    private final JdbcClient database;
    private final ProductRepository repository;

    public Product findBy(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("No product with id " + id));
    }

    public long count() {
        return repository.count();
    }

    public List<RevisionType> revisionTypes() {
        return database
            .sql("SELECT revtype FROM products_aud ORDER BY rev")
            .query(Byte.class)
            .list()
            .stream()
            .map(RevisionType::fromRepresentation)
            .toList();
    }

}
