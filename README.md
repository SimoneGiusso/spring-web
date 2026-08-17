# spring-web

A small product-catalog REST API built with **Spring Boot 4.1** on **Java 21**, backed by
**PostgreSQL 17** through **JPA/Hibernate**, with schema migrations by **Flyway** and end-to-end
tests running against a real database via **Testcontainers**.

It exposes three endpoints — `GET`, `POST` and `PATCH` — on a single `Product` resource.

---

## Project layout

```
src/main/java/org/simonegiusso/springweb
├── SpringWebApplication.java             entry point
├── config/
│   └── PersistenceConfiguration.java     Clock bean + JPA auditing wiring
├── product/                              the feature, as one cohesive package
│   ├── Product.java                      JPA entity (Lombok accessors)
│   ├── ProductCategory.java
│   ├── ProductRepository.java            Spring Data JPA repository
│   ├── ProductService.java               transaction boundary + business rules
│   ├── ProductController.java            HTTP boundary
│   ├── ProductDTO.java                   request and response body (record + constraints)
│   ├── ProductNotFoundException.java
│   ├── DuplicateSkuException.java
│   └── validation/
│       ├── OnCreate.java                 validation group
│       └── OnPatch.java                  validation group
└── web/
    └── GlobalExceptionHandler.java       exceptions → RFC 9457 ProblemDetail

src/main/resources
├── application.yml
└── db/migration/V1__create_products_table.sql

src/test/java/org/simonegiusso/springweb
├── TestcontainersConfiguration.java      the PostgreSQL container, as a bean
├── TestSpringWebApplication.java         run the app locally on a throw-away container
├── support/
│   ├── AbstractIntegrationTest.java      shared test setup
│   └── JsonFixture.java                  loads expected bodies from files
└── product/ProductApiIntegrationTest.java

src/test/resources/fixtures/*.json        expected response bodies
```

The code is organised **by feature, not by layer**: everything about a product lives in one package,
so the visibility modifiers do real work (the controller and the mapping methods are package-private
— nothing outside the feature can reach them). `web/` holds only what is genuinely cross-cutting.

---

## Main design points

### Requests and responses are records, and they are not the entity

`ProductDTO` is a Java record that carries the Bean Validation constraints. Keeping it separate from
the `Product` entity means the HTTP contract can evolve independently of the database schema, and no
internal field (`version`, for example) is exposed by accident.

`id` is deliberately **not** part of the representation: a product is identified publicly by its
`sku`, which is unique and carries business meaning, so the internal UUID never leaks. A client that
needs it after a `POST` reads it from the `Location` header; on `GET` and `PATCH` it is already in
the request URL.

### One record, two validation groups

`POST`, `PATCH` and the response describe the *same* product attributes. Rather than duplicate them
across near-identical records, there is a single `ProductDTO`, and the rules that differ between the
endpoints are tagged with a **validation group**:

```java
public record ProductDTO(
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnPatch.class, message = "must not be provided in a patch request")
    @Pattern(regexp = "SKU-\\d{6}", message = "must match SKU-<6 digits>")
    String sku,

    @NotNull(groups = OnCreate.class)
    @Size(min = 1, max = 120)
    String name,
    ...

    @Null(message = "is read-only and must not be provided")
    Instant createdAt,

    @Null(message = "is read-only and must not be provided")
    Instant updatedAt
) {}
```

The controller selects the group per endpoint with `@Validated` (`@Valid` cannot carry groups):

```java
create(@Validated(OnCreate.class) @RequestBody ProductDTO request, ...)
patch(@PathVariable UUID id, @Validated(OnPatch.class) @RequestBody ProductDTO request)
```

The key detail is that both groups **extend `Default`**:

```java
public interface OnCreate extends Default {}
public interface OnPatch extends Default {}
```

Group inheritance means validating `OnPatch` also validates everything in `Default`. So the
untagged constraints — the value rules such as `@Size`, `@DecimalMin`, `@PositiveOrZero` — are
declared once and enforced on *both* endpoints, while only the presence rules are group-specific.
Without that inheritance, a `PATCH` would skip every value rule and happily store a negative price.

This also gives `PATCH` something split records could not express as directly: `sku` is immutable,
so `@Null(groups = OnPatch.class)` rejects a request that tries to change it, instead of silently
ignoring the field.

Sharing one record with the response means `createdAt` and `updatedAt` live on the same type. They
are server-managed, so an untagged `@Null` rejects them on input — a client that tries to forge a
`createdAt` gets a 400 rather than having the field quietly ignored. Being untagged, that rule
applies to `POST` and `PATCH` alike, and never to the outgoing representation, since responses are
not validated.

Wrapper types (`Integer stockQuantity`) are used instead of primitives so `@NotNull` can tell an
omitted field from a `0` that was actually sent.

### Lombok removes the entity's accessor boilerplate

`Product` is annotated `@Getter` and `@NoArgsConstructor(access = PROTECTED)`, which generates the
ten accessors and the constructor JPA requires.

`@Setter` is applied **per field**, not on the class, and only to the five attributes `PATCH` may
change: `name`, `description`, `price`, `stockQuantity` and `category`. `id`, `sku`, `createdAt`,
`updatedAt` and `version` therefore have no setter at all — they are assigned by the constructor,
by Hibernate or by auditing, and nothing else can overwrite them. A class-level `@Setter` would have
generated `setId` and `setVersion` and quietly handed callers the ability to corrupt an entity's
identity and optimistic-locking state.

`@Data` and `@EqualsAndHashCode` are deliberately avoided on an entity: generated `equals`/`hashCode`
over a mutable, generated-on-persist id breaks identity semantics, and a generated `toString` can
trigger lazy loading.

Lombok is `provided` scope and excluded from the repackaged jar. Both are needed: Spring Boot's
`repackage` goal bundles provided-scope dependencies by default, and the Boot 4.1 parent no longer
carries the lombok exclusion earlier versions had.

### Error handling: `@RestControllerAdvice` + `ProblemDetail`

`GlobalExceptionHandler` is annotated `@RestControllerAdvice` **and extends
`ResponseEntityExceptionHandler`**. This combination is what makes it worth using:

- `ResponseEntityExceptionHandler` already maps every exception Spring MVC raises *before* a
  controller is reached — unreadable JSON, wrong media type, unknown route, a path variable that is
  not a UUID — onto `ProblemDetail`. Extending it means those come out as `application/problem+json`
  for free, instead of the servlet container's default HTML error page.
- `@RestControllerAdvice` then adds handlers for the application's own failures
  (`ProductNotFoundException` → 404, `DuplicateSkuException` → 409, anything else → 500).

The alternative Spring offers is letting each exception implement `ErrorResponse` (or carry
`@ResponseStatus`) and describe its own HTTP status. That was not chosen here: it pushes HTTP
concerns into the domain classes, and it scatters the error contract across the codebase. A single
advice keeps every response shape in one file, which is also the only place that needs to change if
the error format does.

Responses follow **RFC 9457** (`ProblemDetail`), with `type`/`title`/`status`/`detail`/`instance`
plus extension members where they help a client:

```json
{
  "type": "https://api.spring-web.example/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request body failed validation. See the errors field for details.",
  "instance": "/api/products",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    },
    {
      "field": "price",
      "message": "must be greater than 0.00"
    }
  ]
}
```

The `errors` array is added by overriding `handleMethodArgumentNotValid`, so a client can map each
violation back to the input field that caused it — and **all** violations are reported at once.

### PATCH semantics

`PATCH` applies a partial update: `ProductService.patch` only touches the fields that are present in
the body, and the managed entity is mutated inside the transaction so Hibernate's dirty checking
issues the `UPDATE`.

**An omitted field and an explicit `null` both mean "leave unchanged."** This is the common
convention and it is unambiguous for every mandatory attribute, but it does mean the optional
`description` cannot be cleared through this endpoint. Implementing RFC 7396 JSON Merge Patch (where
`null` means *remove*) would be the fully spec-compliant alternative; it needs a wrapper type such as
`Optional<T>` or `JsonNullable<T>` on every field to distinguish the three cases, which was not worth
the extra indirection here.

A body that names no field is not an error: `PATCH {}` returns `200 OK` with the product unchanged.
Because no attribute is touched, Hibernate finds nothing dirty and issues no `UPDATE` at all, so
neither `updatedAt` nor `version` moves — the request is a genuine no-op rather than a write that
happens to store the same values. Sending `sku` *is* rejected, via `@Null(groups = OnPatch.class)`.

### The entity controls how it is built

`Product` is created through the static factory `create(...)` over a private constructor, so a
product cannot be instantiated half-built from outside the package. Mutation after that point is
limited to the five fields with a generated setter (see the Lombok section above); everything else
is write-once.

### Schema is owned by Flyway, verified by Hibernate

`spring.jpa.hibernate.ddl-auto: validate` — Hibernate never creates or alters anything. The schema
comes from versioned migrations in `db/migration`, and Hibernate's only job at startup is to fail
fast if the entity mapping and the migrated schema disagree. `ddl-auto: update` is convenient but is
not something to run against a real database.

### `Clock` is a bean

`PersistenceConfiguration` registers a `Clock` and wires it into Spring Data JPA auditing via a
`DateTimeProvider`, so `@CreatedDate`/`@LastModifiedDate` read the time from an injected dependency.
The tests replace that bean with a fixed clock, which is what allows `createdAt`/`updatedAt` to be
asserted as literal values in the expected JSON files.

### Other choices worth naming

- **UUIDv7 primary keys** (`@UuidGenerator(style = VERSION_7)`) — globally unique like a random UUID,
  but time-ordered, so inserts stay at the right edge of the B-tree index instead of scattering.
- **`open-in-view: false`** — the persistence context closes with the transaction, so no query can
  be triggered accidentally while the response is being serialised.
- **Virtual threads** (`spring.threads.virtual.enabled: true`) — Java 21 carries each request on a
  virtual thread, so blocking JDBC calls no longer pin a platform thread.
- **`@Version`** on the entity for optimistic locking.
- **Optimised `Location` header** — `POST` returns `201 Created` with the URI of the new resource,
  built from the injected `UriComponentsBuilder` rather than a hard-coded string.

---

## Tests

`ProductApiIntegrationTest` runs the **whole stack**: the real embedded Tomcat on a random port, the
real Spring MVC pipeline, and a real PostgreSQL 17 in Docker. Nothing is mocked, and requests go over
actual HTTP through `RestTestClient`.

Each test follows the same three steps:

1. **Call an endpoint** over HTTP.
2. **Assert the response body against a JSON file** in `src/test/resources/fixtures`, compared in
   `STRICT` mode — every field must match, and no unexpected field may appear.
3. **Assert the database** with plain SQL through `JdbcClient`, so the check cannot be satisfied by
   Hibernate's first-level cache. The `PATCH` test, for example, verifies that the untouched columns
   really are untouched and that `version` went from `0` to `1`.

Values that cannot be known upfront — the generated id — are written as `${id}` in the fixture and
substituted by `JsonFixture`, which keeps the comparison strict over the entire document.

The container is declared as a `@Bean` with `@ServiceConnection` in `TestcontainersConfiguration`:
Spring Boot derives `spring.datasource.*` from it automatically (no property wiring by hand) and
reuses the same container across the test suite.

```bash
mvn test          # requires a running Docker daemon
```

---

## Running the application

**Prerequisites:** Java 21 and Docker.

### Option 1 — `spring-boot:run` (PostgreSQL starts automatically)

`spring-boot-docker-compose` is on the classpath, so Boot starts the `compose.yaml` services before
the application and stops them on shutdown. Nothing else to set up:

```bash
mvn spring-boot:run
```

### Option 2 — from the IDE, on a throw-away container

Run `TestSpringWebApplication` (in `src/test/java`) instead of `SpringWebApplication`. It boots the
app with the Testcontainers-managed database, so no compose stack and no local PostgreSQL is needed.

### Option 3 — against your own PostgreSQL

```bash
docker compose up -d
mvn spring-boot:run
```

Or point the app at any other instance via `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`.

The API is then at `http://localhost:8080`, with health at `http://localhost:8080/actuator/health`.

---

## Endpoints

Base path: `/api/products`

### `POST /api/products` — create

```bash
curl -i -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{
        "sku": "SKU-000123",
        "name": "Noise Cancelling Headphones",
        "description": "Over-ear, 40h battery",
        "price": 249.99,
        "stockQuantity": 15,
        "category": "ELECTRONICS"
      }'
```

`201 Created`, with a `Location` header pointing at the new resource — this is where the generated
id comes from, since the body does not carry it:

```
Location: http://localhost:8080/api/products/01a01132-537e-7c62-a235-fe7cdf6601c9
```

```json
{
  "sku": "SKU-000123",
  "name": "Noise Cancelling Headphones",
  "description": "Over-ear, 40h battery",
  "price": 249.99,
  "stockQuantity": 15,
  "category": "ELECTRONICS",
  "createdAt": "2026-08-17T19:28:25.986501Z",
  "updatedAt": "2026-08-17T19:28:25.986501Z"
}
```

| Field           | Rules                                                               |
|-----------------|---------------------------------------------------------------------|
| `sku`           | required, `SKU-` followed by exactly 6 digits, unique               |
| `name`          | required, 1–120 characters                                          |
| `description`   | optional, max 2000 characters                                       |
| `price`         | required, `> 0`, max 2 decimals                                     |
| `stockQuantity` | required, `0 … 1000000`                                             |
| `category`      | required, one of `ELECTRONICS`, `BOOKS`, `CLOTHING`, `HOME`, `TOYS` |

### `GET /api/products/{id}` — read

```bash
curl http://localhost:8080/api/products/01a01132-537e-7c62-a235-fe7cdf6601c9
```

`200 OK` with the same representation, or `404 Not Found` as a problem document.

### `PATCH /api/products/{id}` — partial update

Send only the fields to change:

```bash
curl -X PATCH http://localhost:8080/api/products/01a01132-537e-7c62-a235-fe7cdf6601c9 \
  -H 'Content-Type: application/json' \
  -d '{ "price": 199.99, "stockQuantity": 8 }'
```

`200 OK` with the updated representation; every field not mentioned keeps its value and `updatedAt`
moves forward. All fields are optional — an empty body `{}` is accepted and simply changes nothing.
`sku` is rejected, since it identifies the product and cannot be changed. Present fields are held to
the same value rules as on create.

### Error responses

All errors are `application/problem+json`:

| Status                      | When                                                                                                                           |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request`           | constraint violations, malformed JSON, unknown category, non-UUID id, `sku` in a patch, `createdAt`/`updatedAt` in any request |
| `404 Not Found`             | no product with that id                                                                                                        |
| `409 Conflict`              | the `sku` is already taken                                                                                                     |
| `500 Internal Server Error` | anything unexpected (details are logged, never returned)                                                                       |

```bash
curl http://localhost:8080/api/products/01a01132-0000-7000-8000-000000000000
```

```json
{
  "type": "https://api.spring-web.example/problems/product-not-found",
  "title": "Product not found",
  "status": 404,
  "detail": "No product exists with id 01a01132-0000-7000-8000-000000000000",
  "instance": "/api/products/01a01132-0000-7000-8000-000000000000",
  "productId": "01a01132-0000-7000-8000-000000000000"
}
```
