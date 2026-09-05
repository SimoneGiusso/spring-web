# spring-web

A template for a **multi-tenant REST API** on Spring Boot. The domain — a product catalog — is
deliberately thin. The point is everything around it: each row belongs to a user, and that isolation
is enforced by the persistence layer instead of by checks scattered through application code.

**Java 21 · Spring Boot 4.1 · Spring Framework 7 · Hibernate 7.4 · PostgreSQL 17**

## Libraries

| Library | Used for |
|---|---|
| Spring Web MVC | annotated REST controllers |
| Spring Data JPA · Hibernate ORM | persistence, and the multi-tenancy discriminator |
| Hibernate Envers | a full copy of every row at every change |
| Hibernate Validator | request-body constraints, grouped per endpoint |
| MapStruct | entity ⇄ DTO conversions, generated at compile time |
| Lombok | accessors, constructors, loggers |
| Flyway | schema migrations, versioned and checksummed |
| Testcontainers | a throw-away PostgreSQL for the test suite |
| Actuator | health and info endpoints |

## How it is built

### Multi-tenancy

Discriminator-based multi-tenancy, driven entirely by Hibernate:

- **`@TenantId`** on the entity's `owner` field. Hibernate assigns it on insert, appends `owner = ?`
  to every select, update and delete — including `find()` by primary key — and refuses to let the
  application change it. `ProductService` and `ProductRepository` contain no tenancy code at all: a
  foreign id simply comes back empty and surfaces as `404`, never `403`, which would confirm the row
  exists.
- **`CurrentTenantIdentifierResolver`** answers "which tenant is this session". Spring Boot has no
  property for it, so the bean registers *itself* by also implementing `HibernatePropertiesCustomizer`.
- **A `@RequestScope` bean** holds the caller, built from the injected `HttpServletRequest`. One
  instance per request means nothing has to be unbound afterwards — the scope ends when the request
  does.
- **A `HandlerInterceptor`** validates the `X-User` header and rejects from `preHandle`, which keeps
  the failure inside Spring MVC so it renders as a problem document like every other error. It is
  registered on `/**`, so a new controller is tenant-scoped by default.
- **No request bound ⇒ system.** The resolver asks `RequestContextHolder` whether a request exists;
  when none does, the work is a scheduled task, a message listener or a test, and it resolves to a
  system tenant that `isRoot` reports as Hibernate's **root tenant** — filter off, every owner
  visible. Background code keeps the ordinary repositories and never mentions tenants.
- **`admin` is the same mechanism pointed at a person**: also reported as root, so the plain
  `GET /api/products/{id}` returns any owner's product with no branch in the controller.

`X-User` stands in for authentication, which is not implemented. Any caller can claim any identity,
`admin` included.

### API and error handling

Class-level `@RequestMapping` fixes the base path and media type. `POST` answers `201` with a
`Location` built from an injected `UriComponentsBuilder` and **no body** — clients follow the link.

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) problem documents. A
`@RestControllerAdvice` **extends `ResponseEntityExceptionHandler`**, so everything Spring MVC raises
before a controller is reached — unreadable JSON, wrong media type, unknown route, non-UUID path
variable — is already rendered as `application/problem+json`. The advice adds the rest, and overrides
`handleMethodArgumentNotValid` to attach a sorted machine-readable `errors` array.

### Validation

One DTO serves create, patch and response, with per-endpoint rules tagged by **validation group**.
Both groups extend `Default`, so value rules are declared once and enforced on both endpoints while
only presence rules stay group-specific.

### Mapping

MapStruct generates the conversions from two method signatures, leaving the DTO as pure wire
contract. Declaring `annotationProcessorPaths` disables classpath discovery, so Lombok is listed
there too and `lombok-mapstruct-binding` makes MapStruct run after it. `toEntity` explicitly ignores
the id, version, timestamps and owner: the entity's `@AllArgsConstructor` would otherwise let a
request body assign them.

### Auditing and history

`@CreatedDate` / `@LastModifiedDate` are filled by an entity listener whose `DateTimeProvider` reads
an injectable **`Clock` bean** rather than the system clock — the single indirection that lets tests
pin time. Envers adds the other axis: one `@Audited` writes a revision row holding the whole product
at every change.

### Schema and scheduling

Flyway owns the schema and `ddl-auto: validate` makes Hibernate check its mapping against it without
ever modifying it. `@Scheduled` demonstrates the system path: a task that reads across every owner
while containing no tenancy code.

Request handling runs on **virtual threads**, and `spring-boot-docker-compose` starts the database
before the app.

## Testing

Integration tests only, over HTTP against a real PostgreSQL started by Testcontainers via
`@ServiceConnection`. The base classes split by what a test needs, so nothing inherits what it does
not use:

| | |
|---|---|
| `BaseIT` | context, container, clean schema per method, pinned clock |
| `BaseApiIT` | adds `RestTestClient`s and assertion-file resolution |

- **`RestTestClient`** (new in Spring Framework 7) issues requests; `clientFor(user)` pins `X-User`
  as a default header, so a test reads as `alice.get()` / `bob.patch()` and tenant isolation is
  asserted by *who* makes the call.
- **`@TestBean`** swaps the `Clock` for a fixed one, making audit timestamps exactly assertable.
- **`@Sql`** truncates before each method. It runs *before* `@BeforeEach`, so per-test seeding cannot
  live there.
- Fixtures are split by direction: a factory writes rows with SQL — auditing would overwrite
  timestamps and ids are generated on save, so a known id with a past `createdAt` is only reachable
  that way — and a separate read-only class queries them back.
- Response bodies are compared `STRICT` against files in `src/test/resources/assertion-files`.

Test fixtures are plain `@Component`s: test classes sit under the same base package as
`@SpringBootApplication` and `target/test-classes` is on the classpath while testing, so the
application's own component scan finds them. Only `TestcontainersConfiguration` is imported, because
Boot deliberately holds `@TestConfiguration` back from scanning.

## Running

Requires **Java 21** and a running **Docker** daemon.

```bash
mvn test              # integration tests against a throw-away PostgreSQL container
mvn spring-boot:run   # app on :8080, health at /actuator/health
```

```bash
# every request identifies its user; products are visible only to their owner
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' -H 'X-User: alice' \
  -d '{"sku":"SKU-000001","name":"Widget","price":10.00,"stockQuantity":5,"category":"HOME"}'

curl localhost:8080/api/products/{id} -H 'X-User: alice'   # 200
curl localhost:8080/api/products/{id} -H 'X-User: bob'     # 404 — not bob's
curl localhost:8080/api/products/{id} -H 'X-User: admin'   # 200 — admin sees every owner
```
