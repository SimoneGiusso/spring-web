# spring-web

A template for a **multi-tenant REST API** on Spring Boot. The domain — a product catalog — is
deliberately thin. The point is everything around it: each row belongs to a user, and that isolation
is enforced by the persistence layer instead of by checks scattered through application code.

**Java 21 · Spring Boot 4.1 · Spring Framework 7 · Hibernate 7.4 · PostgreSQL 17**

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
| Testcontainers | a throw-away PostgreSQL for the test suite |
| mock-oauth2-server | a real authorisation server for the test suite, so the real decoder runs |
| Actuator | health and info endpoints |

## How it is built

### Authentication

OAuth2 resource server against **Microsoft Entra ID**. Callers are service principals using the
client-credentials flow — no humans, no browser, no sessions — so every request carries its own
`Authorization: Bearer <jwt>` and the chain is stateless with CSRF disabled.

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

Two claims carry everything: **`oid`**, the service principal's object id, becomes the principal and
goes straight into the `@TenantId` column with no mapping table; **`roles`** become authorities and
are checked by `@PreAuthorize`. `authority-prefix: ""` is load-bearing — remove it and every role
silently becomes `SCOPE_`-prefixed and every request 403s, which a test pins.

Spring Security raises 401 and 403 inside the filter chain, before Spring MVC, where the
`@RestControllerAdvice` cannot see them, so an `AuthenticationEntryPoint` and an
`AccessDeniedHandler` render them as the same problem documents as every other error. A
`@PreAuthorize` denial is the mirror case — it is thrown *inside* MVC and never reaches the
`AccessDeniedHandler` — so the advice maps `AuthorizationDeniedException` to the identical document.

### The token

A decoded v2.0 app-only access token. `src/test/resources/entra/decoded-token.json` holds one and
the suite mints its tokens to match it, so the fixture is what the configuration is checked against:

```json
{
  "aud": "8f2a1c34-5b6d-4e7f-9a0b-1c2d3e4f5a6b",
  "iss": "https://login.microsoftonline.com/6c1e0b7a-2d3f-4a5b-8c9d-0e1f2a3b4c5d/v2.0",
  "iat": 1788595200,
  "nbf": 1788595200,
  "exp": 1788598799,
  "aio": "E2ZgYPjBv8Rk6bSVn1mM7oyLZ9dHAA==",
  "azp": "b41d9e6f-0a83-4c25-91d7-5e8a4f60b3c2",
  "azpacr": "1",
  "idtyp": "app",
  "oid": "3f9b2e10-7c4d-4a1b-9e8f-2d5c6b7a8e90",
  "rh": "0.AR8A2sK1qP3mF0eZbT1xUvQ9c0Zg1nAaBcdEfGhIjKlMnOpQrSt.",
  "roles": [
    "Catalog.ReadWrite"
  ],
  "sub": "3f9b2e10-7c4d-4a1b-9e8f-2d5c6b7a8e90",
  "tid": "6c1e0b7a-2d3f-4a5b-8c9d-0e1f2a3b4c5d",
  "uti": "5xQ0aL3nEkm7Rb9YtCvPAA",
  "ver": "2.0"
}
```

| Claim | Meaning | Role here |
|---|---|---|
| `aud` | Who the token is for. In **v2.0 always the bare client id** of the API — a v1.0 token would carry `api://<guid>` instead, and configuring the wrong one is the classic mistake this fixture exists to catch. | validated |
| `iss` | The issuing authority, ending in `/v2.0` for v2 tokens. Its GUID is the tenant. | validated |
| `exp` · `nbf` · `iat` | Expiry, not-before and issue time, as Unix timestamps. | validated |
| `oid` | Immutable object id of the caller's **service principal** in this tenant. Stable across applications, so it is the identity worth keying data on. | **the tenant** — goes straight into `products.owner` |
| `roles` | App roles the caller was granted. The client-credentials flow uses these *in place of* `scp`, which appears only in user tokens. | **the authorities** `@PreAuthorize` checks |
| `sub` | Subject. Pairwise and unique per application; for an app-only token it equals `oid`. | — |
| `azp` | Application id of the *calling* app registration. Distinct from `oid`, which is that app's service principal object id. | — |
| `azpacr` | How the client authenticated: `0` public client, `1` client secret, `2` certificate. | — |
| `tid` | Tenant the token was issued in; matches the GUID in `iss`. | — |
| `idtyp` | `app` for app-only tokens. An **optional claim** — it must be enabled on the app registration to appear at all. | — |
| `ver` | Token version, `2.0`. | — |
| `uti` | Per-token identifier, the Entra equivalent of `jti`. Useful in sign-in logs. | — |
| `aio` · `rh` | Opaque, internal to Entra. Microsoft documents these as not for resource consumption. | ignored |

Only `aud`, `iss`, the timestamps, `oid` and `roles` matter to this application; everything else is
context Entra includes. Spring validates the first three, and the claim mapping turns the last two
into the principal and its authorities.

### Authorisation

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

- **`RestTestClient`** (new in Spring Framework 7) issues requests; `clientFor(objectId, roles...)`
  mints a token and pins it as a default `Authorization` header, so a test still reads as
  `alice.get()` / `bob.patch()` and isolation is asserted by *who* makes the call.
- **[`mock-oauth2-server`](https://github.com/navikt/mock-oauth2-server)** runs a real authorisation
  server on localhost and the test profile points `issuer-uri` at it, so the application runs its
  real `JwtDecoder` — real JWKS, real signatures, real audience and expiry validation. Every test
  goes through it rather than injecting an `Authentication`: the base classes drive real HTTP, where
  the `jwt()` post-processor (MockMvc only) does not apply, and one mechanism beats two.
- **`entra/decoded-token.json`** holds the decoded payload of a token captured from the tenant by
  `scripts/capture-entra-token.sh`. The suite mints tokens for *its* audience, and
  `EntraTokenFixtureTest` asserts the token really is v2 — bare-GUID `aud`, `roles` not `scp`,
  `sub` equal to `oid`. Without it the mock would only ever agree with whatever the configuration
  already assumed. **The committed file is synthetic**: it has the shape and claim set of a real
  token but none of its values came from a tenant, so run the capture script before trusting it.
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

Running the application needs an Entra app registration: set `ENTRA_TENANT_ID` and
`ENTRA_API_CLIENT_ID`, and expose `Catalog.Read`, `Catalog.ReadWrite` and `Catalog.Read.All` as app
roles. The test suite needs neither — it runs its own issuer.

```bash
# a service principal's token; oid decides which products are visible, roles what may be done
TOKEN=$(curl -s -X POST "https://login.microsoftonline.com/$ENTRA_TENANT_ID/oauth2/v2.0/token" \
  -d grant_type=client_credentials -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \
  -d "scope=$ENTRA_API_CLIENT_ID/.default" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

curl localhost:8080/api/products/{id} -H "Authorization: Bearer $TOKEN"
curl localhost:8080/api/products/{id}                       # 401 — problem document, no token
```
