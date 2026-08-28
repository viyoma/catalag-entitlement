# Requirements — Catalog & Entitlement modernisation

Space: `catalog-entitlement` · App: `APP-CATALOG` · Status: **proposed**
Derived from the legacy source at `lgi-catalog-entitlement-legacy/` (11 files, `com.lgi.catalog`).

---

## R0. Purpose

Modernise the Catalog & Entitlement service without changing what it decides.

The service authorises playback. It answers one question — *may this subscriber play this
title on this device from this location?* — and every requirement below exists to ensure the
answer is identical before and after modernisation.

---

## R1. Behavioural invariants (MUST NOT change)

These are derived from `core/EntitlementService.java` and are the contract the transform is
measured against. They are ordered: the service evaluates gates in sequence and
short-circuits on the first denial. **The order is itself an invariant** — reordering gates
changes which reason code a caller receives, and callers key off the reason.

| # | Gate | Source | Deny reason (exact string) |
|---|---|---|---|
| R1.1 | Title exists in catalogue | Hazelcast `IMap<String,Title>` | `TITLE_NOT_IN_CATALOG` |
| R1.2 | Country permitted for title | MaxMind GeoIP on `clientIp` | `REGION_BLOCKED:<country>` |
| R1.3 | Device permitted for title | `title.allowedDevices()` | `DEVICE_NOT_ALLOWED:<deviceType>` |
| R1.4 | Active entitlement row exists | PostgreSQL via jOOQ | `NO_ACTIVE_ENTITLEMENT` |
| R1.5 | Allow | — | `ALLOWED` |

**R1.6 — Empty device set means unrestricted.** `allowedDevices().isEmpty()` permits every
device. A modernised implementation that treats an empty set as "deny all" inverts the
behaviour for every title with no device restriction, which is the common case.

**R1.7 — Unresolvable IP denies as `ZZ`.** `GeoIpService.countryIso` returns the literal
`"ZZ"` on any lookup failure, so an unresolvable address is denied `REGION_BLOCKED:ZZ`
unless the title explicitly allows `ZZ`. This is fail-closed and MUST stay fail-closed.

**R1.8 — Entitlement is a date-windowed boolean, not a tier.** The check is `EXISTS` a row
in `entitlements` where `subscriber_id` and `product_id` match, `status = 'ACTIVE'`, and
`now()` falls within `[valid_from, valid_to]`. There is **no** tier, plan, or price-band
logic in this service. Any modernised implementation that introduces one is wrong.

**R1.9 — Kafka publish happens only on allow.** `publishAuthorization` is called on the
allow path only. Publishing on denials would flood `playback-authorizations` with rejected
attempts and corrupt any downstream consumer that treats the topic as an authorisation log.

**R1.10 — The published event omits `artworkUri`.** The hand-rolled JSON in
`PlaybackEventProducer` emits `titleId`, `subscriberId`, `allowed`, `reason`, `country`,
`decidedAt` — and deliberately not `artworkUri`, even though the record carries it.
Downstream consumers parse this shape. Adding the field is a breaking change to a contract
we cannot see the other side of, so it stays omitted **and this requirement records that the
omission is intentional rather than a bug someone should later "fix".**

## R2. Interface invariants (MUST NOT change)

**R2.1** — `GET /entitlement/{titleId}` with query parameters `subscriberId`, `clientIp`,
`deviceType`. This is the only endpoint; there are no write endpoints.

**R2.2** — HTTP `200` when `allowed == true`, HTTP `403` otherwise. The body is the
serialised decision in both cases — a denial is a well-formed response, not an error.

**R2.3** — Response field names and types unchanged: `titleId`:String, `subscriberId`:String,
`allowed`:boolean, `reason`:String, `country`:String, `artworkUri`:String,
`decidedAt`:Instant. `country` and `artworkUri` are null on the denial paths where the
legacy code leaves them null.

**R2.4** — The shared PostgreSQL schema is untouched. The `entitlements` table is shared with
`APP-METADATA`; the dependency graph shows the coupling on `postgresql 14`. A schema change
here breaks a second application, so no DDL is in scope.

## R3. Resilience invariants (MUST be preserved as behaviour)

Currently expressed with Resilience4j 1.7.1 in application code. The values are the
requirement; the mechanism may change.

**R3.1** — Circuit breaker on the entitlement DB call: failure-rate threshold **50%**,
wait-in-open **10s**, sliding window **20**.
**R3.2** — Retry on the same call: max attempts **3**, wait **200ms**.
**R3.3** — Decoration order is `Retry(CircuitBreaker(dbCall))` — retry outside breaker.
Inverting this changes failure semantics: the breaker would count each retry as a separate
failure and open roughly three times sooner than intended.

## R4. Deliberate changes (MUST change)

**R4.1** — Java 17 → **21**.
**R4.2** — `javax.*` → `jakarta.*`. Surface is exactly two files: `api/EntitlementResource.java`
(`javax.ws.rs.*`) and `CatalogEntitlementConfiguration.java` (`javax.validation.*`).
**R4.3** — Dropwizard 2.1.10 → **4.0.7**, which is the Jakarta-namespaced line. Dropwizard 3.x
is skipped; 4.x is the first release aligned with `jakarta.*`.
**R4.4** — Dependency currency: jOOQ 3.19.6, Hazelcast 5.4.0, Resilience4j **2.2.0** (major —
package names change), Kafka 3.9.2, PostgreSQL driver 42.7.12.
**R4.5** — Remove the `dropwizard-jdbi3` dependency. It is declared and never used; data
access is jOOQ throughout. Carrying it forward means maintaining a dependency the service
does not use.

## R5. New capability required by Day-2 operations

**R5.1 — The service MUST expose a scrapable metrics endpoint.** Today there is no
Prometheus or Micrometer registry; metrics exist only as Dropwizard-Metrics JSON on the
admin connector (port 8081), and **no application health check is registered at all** — so
`/healthcheck` reports framework defaults only.

This is a requirement rather than a nice-to-have because the Day-2 workstream depends on it.
A DevOps agent cannot derive an evidenced signal from a service that publishes nothing. If
modernisation ships without R5, Day-2 signals stay synthetic and the autonomous-operations
claim has no foundation.

**R5.2** — A real health check MUST be registered covering database reachability, so a
degraded dependency is visible rather than inferred from latency.

**R5.3** — The four decision outcomes MUST be counted by reason code. Signal derivation needs
to distinguish "denials rose because entitlements expired" (`NO_ACTIVE_ENTITLEMENT`) from
"denials rose because GeoIP is failing" (`REGION_BLOCKED:ZZ`). Without per-reason counters
those two look identical from outside, and they demand opposite responses.

## R6. Testing

**R6.1** — `src/test` does not exist. Tests MUST be created, because every invariant in R1
is currently unverifiable and the transform has nothing to prove itself against.

**R6.2** — Each invariant in R1 and R2 MUST have a test asserting it by name. This is what
makes a half-completed transform fail loudly: on 2026-08-20 a transform returned SUCCESS
with an empty `transformation_steps` list, and nothing contradicted it. A named failing
assertion would have.

**R6.3** — The five gates MUST be tested in order, including that an earlier gate
short-circuits a later one — e.g. a title absent from the catalogue returns
`TITLE_NOT_IN_CATALOG` and never reaches the database.

---

## Out of scope, recorded so it is not silently assumed

- Authentication. The endpoint has none today. Adding it changes the caller contract.
- Replacing Hazelcast, NFS artwork storage, or self-managed Kafka. Each is a separate
  workload with its own dependency graph.
- Schema migration (see R2.4).
- **Migration to Spring Boot.** See `design.md` §D5 — no ATX transform definition can
  perform Dropwizard → Spring Boot, so it cannot be claimed as a factory-automated outcome.
  It is scoped as a separate harness-assisted unit and explicitly flagged as higher risk.
