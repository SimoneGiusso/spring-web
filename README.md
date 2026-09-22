# spring-web

A template for a **multi-tenant REST API** on Spring Boot, built around a product catalog. It
demonstrates secure access, tenant isolation, file imports and observability with integration tests
against real infrastructure.

- **Authentication and authorisation:** an OAuth2 resource server validates Microsoft Entra ID JWT
  bearer tokens and enforces read, write and cross-tenant read permissions through app roles.
- **Tenant isolation:** products belong to the signed-in user; Hibernate enforces
  ownership when reading and writing data.
- **JSON endpoints and CSV uploads:** create, retrieve and partially update products, or import a
  CSV file through `POST /api/products/import`. Imports validate every row and roll back the entire
  upload on failure.
- **Validation and API documentation:** request validation, structured problem responses and
  generated OpenAPI documentation with an interactive Swagger UI, including file uploads.
- **Auditing:** automatic creation and update timestamps, plus a revision history of product changes.
- **Observability:** correlated metrics, traces and logs exported through OpenTelemetry to Grafana LGTM.
- **Local development and integration tests:** Docker Compose supplies PostgreSQL, a mock OAuth2
  issuer and LGTM; tests use Testcontainers and JSON fixtures to verify API responses.

**Java 21 · Spring Boot 4.1 · Spring Framework 7 · Hibernate 7.4 · PostgreSQL 17 · OpenTelemetry**

## Libraries

| Library | Used for |
|---|---|
| Spring Web MVC | annotated REST controllers |
| Spring Security · OAuth2 resource server | validates Entra ID bearer tokens, maps roles to authorities |
| Spring Data JPA · Hibernate ORM | persistence, and the multi-tenancy discriminator |
| Hibernate Envers | a full copy of every row at every change |
| Hibernate Validator | request-body constraints, grouped per endpoint |
| MapStruct | entity ⇄ DTO conversions, generated at compile time |
| Lombok | accessors, constructors, loggers |
| Flyway | schema migrations, versioned and checksummed |
| Testcontainers | a throw-away PostgreSQL and Grafana stack for the test suite |
| mock-oauth2-server | a real authorisation server for the test suite, so the real decoder runs |
| springdoc-openapi | derives the OpenAPI document and serves Swagger UI |
| Actuator | health and info endpoints |
| Micrometer · Micrometer Tracing | the meters and spans everything else is derived from |
| OpenTelemetry SDK · OTLP exporters | metrics, traces and logs off the process over one protocol |
| OpenTelemetry Logback appender | the half Spring Boot leaves out: Logback events into the SDK |
| Grafana LGTM | Loki, Tempo, Prometheus and Grafana in one image, locally and in the suite |

## How it is built

### Authentication

Swagger UI signs users into **Microsoft Entra ID** using authorization code with PKCE.
The API remains a stateless OAuth2 resource server: Swagger sends an access token in
`Authorization: Bearer <jwt>` on each request. The API itself does not create a login session.

Every protected request requires `access_as_user` in the space-separated `scp` claim.
Set `ENTRA_REQUIRED_SCOPE` to change that required scope. App-only tokens lack this delegated
scope and are denied. Existing endpoint permissions still come exclusively from `roles`.

The decoder and the whole claim mapping are auto-configured from properties; no
`JwtAuthenticationConverter` bean is declared:

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: https://login.microsoftonline.com/${ENTRA_TENANT_ID}/v2.0
  audiences: ${ENTRA_API_CLIENT_ID}   # v2 tokens carry the bare client id, not api://...
  authorities-claim-name: roles
  authority-prefix: ""                # roles are authorities as-is, not SCOPE_Catalog.Read
  principal-claim-name: oid
```

Identity and permissions: **`oid`**, the user's object id, becomes the principal and
goes straight into the `@TenantId` column with no mapping table; **`roles`** become authorities and
are checked by `@PreAuthorize`. `authority-prefix: ""` is load-bearing — remove it and every role
silently becomes `SCOPE_`-prefixed and every request 403s, which a test pins.

Spring Security raises 401 and 403 inside the filter chain, before Spring MVC, where the
`@RestControllerAdvice` cannot see them, so an `AuthenticationEntryPoint` and an
`AccessDeniedHandler` render them as the same problem documents as every other error. A
`@PreAuthorize` denial is the mirror case — it is thrown *inside* MVC and never reaches the
`AccessDeniedHandler` — so the advice maps `AuthorizationDeniedException` to the identical document.

### The token

Swagger sends the API a **delegated access token** after the user signs in. Here is its decoded
payload, using the synthetic values from [the test fixture](src/test/resources/entra/decoded-token.json).
This is an illustrative expired example, not a usable token or real user data.

```json
{
  "aud": "8f2a1c34-5b6d-4e7f-9a0b-1c2d3e4f5a6b",
  "iss": "https://login.microsoftonline.com/6c1e0b7a-2d3f-4a5b-8c9d-0e1f2a3b4c5d/v2.0",
  "iat": 1788595200,
  "nbf": 1788595200,
  "exp": 1788598799,
  "aio": "E2ZgYPjBv8Rk6bSVn1mM7oyLZ9dHAA==",
  "azp": "b41d9e6f-0a83-4c25-91d7-5e8a4f60b3c2",
  "azpacr": "0",
  "oid": "3f9b2e10-7c4d-4a1b-9e8f-2d5c6b7a8e90",
  "rh": "0.AR8A2sK1qP3mF0eZbT1xUvQ9c0Zg1nAaBcdEfGhIjKlMnOpQrSt.",
  "roles": [
    "Catalog.ReadWrite"
  ],
  "sub": "synthetic-pairwise-user-subject",
  "tid": "6c1e0b7a-2d3f-4a5b-8c9d-0e1f2a3b4c5d",
  "uti": "5xQ0aL3nEkm7Rb9YtCvPAA",
  "ver": "2.0",
  "scp": "access_as_user"
}
```

| Claim | Meaning | Role here |
|---|---|---|
| `aud` | Who the token is for. In **v2.0 always the bare client id** of the API — a v1.0 token can carry `api://<guid>` instead, and configuring the wrong one is the classic mistake this fixture exists to catch. | validated |
| `iss` | The issuing authority, ending in `/v2.0` for v2 tokens. Its GUID is the tenant. | validated |
| `exp` · `nbf` · `iat` | Expiry, not-before and issue time, as Unix timestamps. | `exp` and `nbf` validated |
| `oid` | Immutable object id of the **user** in this tenant. Stable across applications, so it is the identity worth keying data on. | **the owner** — goes straight into `products.owner` |
| `scp` | Delegated scopes granted to the client on behalf of the user, as a space-separated string. | must include `access_as_user` |
| `roles` | App roles the caller was granted. In user tokens these appear alongside `scp`, which grants delegated access. | **the authorities** `@PreAuthorize` checks |
| `sub` | Subject. Pairwise and unique per application. | — |
| `azp` | Application id of the *calling* app registration. Distinct from `oid`, which is the user's object id. | — |
| `azpacr` | How the client authenticated: `0` public client, `1` client secret, `2` certificate. | — |
| `tid` | Tenant the token was issued in; matches the GUID in `iss`. | — |
| `ver` | Token version, `2.0`. | — |
| `uti` | Per-token identifier, the Entra equivalent of `jti`. Useful in sign-in logs. | — |
| `aio` · `rh` | Opaque, internal to Entra. Microsoft documents these as not for resource consumption. | ignored |

Claim definitions: [Microsoft's access-token claims reference](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference).

For this example, the API first validates the signature, issuer, audience and validity period.
It then requires `access_as_user` in `scp`. The `Catalog.ReadWrite` role permits reading and
modifying products, while `oid` restricts those operations to this user's products. Having the
scope alone does not grant an endpoint role, and having a role without the scope does not permit
delegated access.

### Authorization

Entra app roles:

| Role | Grants |
|---|---|
| `Catalog.Read` | read the caller's own products |
| `Catalog.ReadWrite` | read **and** modify the caller's own products |
| `Catalog.Read.All` | read every owner's products, and modify none |

`Catalog.ReadWrite` covers reading, so it subsumes `Catalog.Read`. `Catalog.Read.All` is not further
along that ladder — it widens *which rows* are visible and grants no writes at all, so the three are
not levels to be compared. Each endpoint names the roles it accepts rather than ordering them.

`@PreAuthorize` on the controller decides whether the operation is allowed; the tenant filter decides
which rows it can touch. The two are independent, which is why `Catalog.Read.All` reads across owners
without being able to write to any of them.

### Multi-tenancy

Discriminator-based multi-tenancy, driven entirely by Hibernate:

- **`@TenantId`** on the entity's `owner` field. Hibernate assigns it on insert, appends `owner = ?`
  to every select, update and delete — including `find()` by primary key — and refuses to let the
  application change it. `ProductService` and `ProductRepository` contain no tenancy code at all: a
  foreign id simply comes back empty and surfaces as `404`, never `403`, which would confirm the row
  exists.
- **`CurrentTenantIdentifierResolver`** answers "which tenant is this session". Spring Boot has no
  property for it, so the bean registers *itself* by also implementing `HibernatePropertiesCustomizer`.
- **A `@RequestScope` bean** holds the caller, built from the `SecurityContext`. One instance per
  request means nothing has to be unbound afterwards — the scope ends when the request does.
- **No request bound ⇒ system.** The resolver asks `RequestContextHolder` whether a request exists;
  when none does, the work is a scheduled task, a message listener or a test, and it resolves to a
  system tenant that `isRoot` reports as Hibernate's **root tenant** — filter off, every owner
  visible. Background code needs no token and never mentions tenants.
- **`Catalog.Read.All` is the same escape hatch pointed at a caller**: also reported as root, so the
  plain `GET /api/products/{id}` returns any owner's product with no branch in the controller.

### API and error handling

Four endpoints, each naming the roles it accepts:

| | | Roles |
|---|---|---|
| `POST` | `/api/products` | `Catalog.ReadWrite` |
| `POST` | `/api/products/import` | `Catalog.ReadWrite` |
| `GET` | `/api/products/{id}` | `Catalog.Read` · `Catalog.ReadWrite` · `Catalog.Read.All` |
| `PATCH` | `/api/products/{id}` | `Catalog.ReadWrite` |

Class-level `@RequestMapping` fixes the base path and media type. `POST` answers `201` with a
`Location` built from an injected `UriComponentsBuilder` and **no body** — clients follow the link.

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) problem documents. A
`@RestControllerAdvice` **extends `ResponseEntityExceptionHandler`**, so everything Spring MVC raises
before a controller is reached — unreadable JSON, wrong media type, unknown route, non-UUID path
variable — is already rendered as `application/problem+json`. The advice adds the rest, and overrides
`handleMethodArgumentNotValid` to attach a sorted machine-readable `errors` array.

### API documentation

springdoc derives the whole document from what is already there — the controller's mappings, the
`ProductDTO` record and its Bean Validation constraints, so `sku` arrives in the schema carrying its
`SKU-\d{6}` pattern without being described twice. Only the bearer scheme is declared by hand, since
nothing in the code says the API needs a token.

```
/v3/api-docs          the OpenAPI document
/swagger-ui.html      Swagger UI, with an Authorize box for pasting an Entra token
```

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

### Observability

**LGTM** is Grafana's stack — **L**oki for logs, **G**rafana over the top, **T**empo for traces and
**M**imir for metrics. The [`grafana/otel-lgtm`](https://github.com/grafana/docker-otel-lgtm) image
packages it as a single container, with Prometheus standing in for Mimir, behind an OpenTelemetry
collector and with the three already wired up as Grafana data sources. It runs beside PostgreSQL in
[`compose.yaml`](compose.yaml), and again as a container in the test suite.

Every signal leaves over **OTLP**, on one connection. No Loki appender pushing logs, no
`/actuator/prometheus` for Prometheus to scrape: the collector receives metrics, traces and logs and
fans them out, so the application knows one protocol and one address.

`spring-boot-starter-opentelemetry` supplies two of the three — `micrometer-registry-otlp` for
meters, Micrometer Tracing over the OpenTelemetry bridge for spans. Logs are the part Spring Boot
leaves open: it configures an `SdkLoggerProvider` and an exporter, but nothing hands Logback events
to them. `opentelemetry-logback-appender` is that half, declared in
[`logback-spring.xml`](src/main/resources/logback-spring.xml) and given the `OpenTelemetry` bean by
`OpenTelemetryLogbackInstaller`. Logback starts long before the application context does, so the
appender buffers what it receives until then and replays it — startup is not missing from Loki.

**The endpoints are declared, not discovered.** `spring-boot-docker-compose` recognises the image
and will wire all three exporters to it on its own, but that wiring is invisible — nothing in the
configuration says where telemetry goes, and the answer is a rule you have to know. So
[`compose.yaml`](compose.yaml) carries the `org.springframework.boot.ignore` label, which switches
the auto-wiring off, publishes 4318 on a fixed port, and
[`application-local.yml`](src/main/resources/application-local.yml) names the three endpoints:

```yaml
management.opentelemetry.tracing.export.otlp.endpoint: http://localhost:4318/v1/traces
management.opentelemetry.logging.export.otlp.endpoint: http://localhost:4318/v1/logs
management.otlp.metrics.export.url:                    http://localhost:4318/v1/metrics
```

One collector, one port, three paths — the paths are OTLP's, one per signal. Metrics is the odd
spelling because it goes through Micrometer's registry rather than the OpenTelemetry SDK, so it is
`url` under a different prefix instead of `endpoint` beside the other two.

The label is load-bearing rather than decorative: a connection detail contributed by Docker Compose
takes precedence over a property, so without it these three lines would be read, overridden and
never used — configuration that looks authoritative and is not. It is detected by the presence of
the key, not its value, and it governs only the wiring: `docker compose up --wait` still holds the
application back until the stack is healthy.

The base [`application.yml`](src/main/resources/application.yml) names no endpoint at all. Anywhere
other than a laptop the standard `OTEL_EXPORTER_OTLP_ENDPOINT` is mapped onto the same properties by
Spring Boot, so deploying against a real backend is one environment variable — and until something
supplies an endpoint nothing is exported, which is why the rest of the test suite needs no
collector. What it does set is `management.tracing.sampling.probability: 1.0`: every request traced,
which is what a template wants to demonstrate and a busy service would not survive.

**The tenant travels with the telemetry.** `TenantObservabilityFilter` runs nested inside the
security chain — Spring Boot orders that at `-100` and an unordered filter last — so by then the
token is decoded and the caller known. It puts the `oid` on the current span and in the MDC, and both
ends come back out queryable:

| | |
|---|---|
| `{ span.tenant = "alice" }` | TraceQL — every trace that caller produced |
| `{service_name="spring-web"} \| tenant="alice"` | LogQL — every line those requests logged |

It is added as a **high-cardinality** key value, which is the load-bearing word: Micrometer puts
those on the span only, while low-cardinality ones also become metric tags — and one time series per
tenant is how a metrics backend falls over. A trace can afford a distinct value per request; a meter
cannot.

Correlation comes free in every direction. The console pattern carries `[traceId-spanId]`; the
exported log records carry the same two as fields Loki indexes; and the request histogram carries
**exemplars**, single samples tagged with the trace they came from. A spike on a graph leads to the
trace that caused it, and that trace leads to the lines it logged — without anyone having to
correlate by timestamp.

### Schema and scheduling

Flyway owns the schema and `ddl-auto: validate` makes Hibernate check its mapping against it without
ever modifying it. `@Scheduled` demonstrates the system path: a task that reads across every owner
while containing no tenancy code.

Request handling runs on **virtual threads**, and `spring-boot-docker-compose` starts the database,
the mock issuer and the Grafana stack, then waits for each to report healthy before the app comes
up.

## Testing

Integration tests only, over HTTP against a real PostgreSQL — and, for the observability suite, a
real Grafana stack — started by Testcontainers via `@ServiceConnection`. The base classes split by
what a test needs, so nothing inherits what it does not use:

| | |
|---|---|
| `BaseIT` | context, container, clean schema per method, pinned clock |
| `BaseApiIT` | adds `RestTestClient`s and assertion-file resolution |

- **`RestTestClient`** (new in Spring Framework 7) issues requests; `clientFor(objectId, roles...)`
  mints a token and pins it as a default `Authorization` header, so a test still reads as
  `alice.get()` / `bob.patch()` and isolation is asserted by *who* makes the call.
- **[`mock-oauth2-server`](https://github.com/navikt/mock-oauth2-server)** runs a real authorisation
  server on localhost and the test profile points `issuer-uri` at it, so the application runs its
  real `JwtDecoder` — real JWKS, real signatures, real audience and expiry validation. Every test
  goes through it rather than injecting an `Authentication`: the base classes drive real HTTP, where
  the `jwt()` post-processor (MockMvc only) does not apply, and one mechanism beats two.
- **`LgtmStackContainer`** starts the same image the local stack uses, and `ObservabilityIT` asks
  Tempo, Prometheus and Loki whether the request it just made arrived. A telemetry pipeline is only
  worth asserting from the far end: the exporters are never inspected, the backends are queried in
  their own languages. `@SpringBootTest` switches metric export and tracing off, so
  `@AutoConfigureMetrics` and `@AutoConfigureTracing` ask for them back — which makes that context
  different from every other test's, and is why it owns its containers rather than sharing them.
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
Boot deliberately holds `@TestConfiguration` back from scanning — with one exception, a class nested
inside a test, which is how `ObservabilityIT` gets a Grafana container and no other test pays for
one.

## Running

### CSV product import

`POST /api/products/import` accepts a multipart file part named `file` and requires
`Catalog.ReadWrite`. Swagger UI provides a file upload control. Use UTF-8 CSV with this exact header:

```csv
sku,name,description,price,stockQuantity,category
SKU-100200,Mechanical Keyboard,"Compact, sturdy",129.90,42,ELECTRONICS
SKU-100201,Desk,,200.00,3,HOME
```

```bash
curl http://localhost:8080/api/products/import \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@products.csv;type=text/csv'
```

The response is `201` with `{"imported":2}`. CSV uploads are limited to 5 MB; larger files receive
`413 Content Too Large`.

### Local startup

Requires **Java 21** and a running **Docker** daemon.

```bash
mvn test              # integration tests against throw-away PostgreSQL and Grafana containers
mvn spring-boot:run   # app on :8080, health at /actuator/health, Grafana on :3000
```

The first run pulls `grafana/otel-lgtm`, which is a large image; everything after that is cached.

`mvn spring-boot:run` needs no Entra tenant. It activates the `local` profile, and
[`compose.yaml`](compose.yaml) starts a mock issuer alongside PostgreSQL — a real OIDC server with
discovery, JWKS and signatures, issuing Entra-shaped tokens. Security is not disabled or weakened:
the same filter chain runs, against the same decoder, and an unauthenticated request still gets
`401`. Only the issuer is local.

Four local users are configured. The login username supplies the mock `oid`, and so the owner:

| Username | Roles | |
|---|---|---|
| `alice`, `bob` | `Catalog.ReadWrite` | two tenants, each blind to the other |
| `reader` | `Catalog.Read` | reads its own; a write is `403` |
| `auditor` | `Catalog.Read.All` | reads every owner; a write is `403` |

Open <http://localhost:8080/swagger-ui.html> and click **Authorize**. The local mock login accepts
`alice`, `bob`, `reader`, or `auditor` as usernames and supplies the corresponding catalog roles.

### Watching it run

`compose.yaml` starts the LGTM stack alongside the database, and `application-local.yml` points the
three exporters at it. Grafana is on <http://localhost:3000> — anonymous, no login form, with Loki,
Tempo and Prometheus already connected.

| Explore ▸ | Query | |
|---|---|---|
| Tempo | `{ span.tenant = "alice" }` | every trace one caller produced |
| Loki | `{service_name="spring-web"} \| tenant="alice"` | every line those requests logged, with its `trace_id` |
| Prometheus | `http_server_requests_milliseconds_count` | the request timer — by `uri`, `method` and `status`, and deliberately not by tenant |

`ProductCountLogger` writes a line every ten seconds, so Loki has something in it before the first
request is ever made — and that line carries no tenant, because a scheduled task has none.

```bash
mvn spring-boot:run
```

### Browsing the documentation

With the application running, it documents itself:

| | |
|---|---|
| <http://localhost:8080/swagger-ui.html> | Swagger UI |
| <http://localhost:8080/v3/api-docs> | the raw OpenAPI document |
