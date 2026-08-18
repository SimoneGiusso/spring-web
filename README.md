# spring-web

A small product-catalog REST API used as a working reference for the Spring features below.
**Spring Boot 4.1** · **Spring Framework 7** · Java 21 · PostgreSQL 17.

Each section names the feature, says how it is wired here, and links to the file that uses it and to
the reference documentation.

---

## Web layer

[Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html) annotated
controllers. [`ProductController`](src/main/java/org/simonegiusso/springweb/product/ProductController.java)
declares `@RestController` with a class-level `@RequestMapping` fixing the base path and the produced
media type, so each handler only declares what differs. `ResponseEntity.created(...)` builds the
`201` response with a `Location` header from an injected `UriComponentsBuilder`, which resolves
against the current request rather than a hard-coded host.

## Request validation with groups

[Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
applied to `@RequestBody` arguments — see
[method validation in MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html).

One [`ProductDTO`](src/main/java/org/simonegiusso/springweb/product/ProductDTO.java) serves create,
patch and response, with the rules that differ per endpoint tagged by validation group. The
controller selects the group with `@Validated(OnCreate.class)` / `@Validated(OnPatch.class)`; plain
`@Valid` cannot carry groups.

The load-bearing detail is that both groups **extend `Default`**
([`OnCreate`](src/main/java/org/simonegiusso/springweb/product/validation/OnCreate.java),
[`OnPatch`](src/main/java/org/simonegiusso/springweb/product/validation/OnPatch.java)). Group
inheritance means validating `OnPatch` also evaluates every untagged constraint, so value rules are
declared once and enforced on both endpoints while only presence rules stay group-specific. Without
it a `PATCH` would skip every value rule.

## Error responses

[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html) via Spring's
[`ProblemDetail` support](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

[`WebGlobalExceptionHandler`](src/main/java/org/simonegiusso/springweb/config/WebGlobalExceptionHandler.java)
is a `@RestControllerAdvice` that **extends `ResponseEntityExceptionHandler`**. The base class
already renders everything Spring MVC raises before a controller is reached — unreadable JSON, wrong
media type, unknown route, non-UUID path variable — as `application/problem+json`. The advice adds
the remaining cases without defining custom exception types: `NoSuchElementException` → 404,
`DataIntegrityViolationException` → 409, anything else → 500.

Overriding `handleMethodArgumentNotValid` attaches a sorted, machine-readable `errors` array to the
problem document, so every violation is reported in one response.

## Persistence

[Spring Data JPA repositories](https://docs.spring.io/spring-data/jpa/reference/repositories/core-concepts.html).
[`ProductRepository`](src/main/java/org/simonegiusso/springweb/product/ProductRepository.java) is a
bare `JpaRepository` — no query methods are needed.

[`ProductService`](src/main/java/org/simonegiusso/springweb/product/ProductService.java) is
`@Transactional(readOnly = true)` at class level with writes opting in per method. The patch method
mutates the managed entity and returns it without calling `save`: Hibernate's dirty checking flushes
at commit. Duplicate `sku` is left to the database's unique constraint, which Spring's exception
translation surfaces as `DataIntegrityViolationException` — no check-then-act race.

[`Product`](src/main/java/org/simonegiusso/springweb/product/Product.java) uses `@Version` for
optimistic locking and Hibernate's
[`@UuidGenerator`](https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/annotations/UuidGenerator.html)
with `VERSION_7`, so identifiers are time-ordered and index-friendly.

## Auditing with an injectable clock

[Spring Data auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html). `createdAt`
and `updatedAt` are `@CreatedDate` / `@LastModifiedDate` fields populated by an entity listener, and
are rejected by validation if a client sends them.

[`PersistenceConfiguration`](src/main/java/org/simonegiusso/springweb/config/PersistenceConfiguration.java)
enables auditing with a `DateTimeProvider` that reads a `Clock` bean instead of the system clock.
That single indirection is what lets tests pin time to a fixed instant.

## Schema migrations

[Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
owns the schema — [`V1__create_products_table.sql`](src/main/resources/db/migration/V1__create_products_table.sql).
[`application.yml`](src/main/resources/application.yml) sets `ddl-auto: validate`, so Hibernate
checks its mapping against the migrated schema and never modifies it.

## Runtime configuration

- [Docker Compose support](https://docs.spring.io/spring-boot/reference/features/dev-services.html#features.dev-services.docker-compose):
  `spring-boot-docker-compose` starts [`compose.yaml`](compose.yaml) before the app and stops it on
  shutdown, so `mvn spring-boot:run` needs no manual database setup.
- [Virtual threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads):
  `spring.threads.virtual.enabled` puts request handling on virtual threads.
- [Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html): only `health` and
  `info` are exposed.

## Testing

[`ProductApiIntegrationTest`](src/test/java/org/simonegiusso/springweb/product/ProductApiIntegrationTest.java)
drives the API over HTTP against a real PostgreSQL. The supporting pieces live in
[`AbstractIntegrationTest`](src/test/java/org/simonegiusso/springweb/support/AbstractIntegrationTest.java):

- **[`@SpringBootTest(webEnvironment = RANDOM_PORT)`](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)**
  boots the full application on a random port, injected with `@LocalServerPort`.
- **[`RestTestClient`](https://docs.spring.io/spring-framework/reference/testing/resttestclient.html)**
  (new in Spring Framework 7) issues the requests and carries the fluent expectations. Response
  bodies are compared `STRICT` against files in
  [`assertion-files/`](src/test/resources/assertion-files), loaded by
  [`FileUtils`](src/test/java/org/simonegiusso/springweb/support/FileUtils.java).
- **[Testcontainers with `@ServiceConnection`](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)**
  starts PostgreSQL and contributes the datasource properties automatically —
  [`TestcontainersConfiguration`](src/test/java/org/simonegiusso/springweb/TestcontainersConfiguration.java).
- **[`@TestBean`](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-testbean.html)**
  replaces the `Clock` bean with a fixed one, making audit timestamps exactly assertable.
- **[`@Sql`](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/executing-sql.html)**
  runs [`truncate-products.sql`](src/test/resources/sql-scripts/truncate-products.sql) before each
  test method. Note it executes *before* `@BeforeEach`, so per-test seeding must not live there.
- **[`JdbcClient`](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html#jdbc-JdbcClient)**
  seeds rows in [`ProductTestData`](src/test/java/org/simonegiusso/springweb/support/ProductTestData.java).
  Seeding cannot go through the repository: auditing would overwrite the timestamps and the id is
  generated on save, so a fixture with a known id and a past `createdAt` is only reachable via SQL.

```bash
mvn test              # integration tests against a throw-away PostgreSQL container
mvn spring-boot:run   # app on :8080, health at /actuator/health
```

Requires Java 21 and a running Docker daemon.
