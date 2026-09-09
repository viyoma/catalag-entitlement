# Design — Catalog & Entitlement modernisation

Space: `catalog-entitlement` · App: `APP-CATALOG` · Status: **proposed**
Implements `requirements.md`. Read `units.md` for the executable decomposition.

---

## D1. Target state

| Aspect | Current | Target | Requirement |
|---|---|---|---|
| Java | 17 | 21 | R4.1 |
| Framework | Dropwizard 2.1.10 | Dropwizard 4.0.7 | R4.3 |
| Namespace | `javax.*` (2 files) | `jakarta.*` | R4.2 |
| Resilience4j | 1.7.1 | 2.2.0 | R4.4 |
| jOOQ / Hazelcast / Kafka / PG driver | 3.18.7 / 5.3.6 / 3.6.1 / 42.7.1 | 3.19.6 / 5.4.0 / 3.9.2 / 42.7.12 | R4.4 |
| `dropwizard-jdbi3` | declared, unused | removed | R4.5 |
| Observability | Dropwizard-Metrics JSON on :8081, **zero** health checks | Micrometer + Prometheus, DB health check, per-reason counters | R5 |
| Tests | none | invariant-named suite | R6 |

## D2. Why Dropwizard 4 rather than Spring Boot

This is the design's load-bearing decision and it contradicts the demo narrative used so
far, so it is recorded in full rather than quietly resolved.

**The stated target has been "Java 17/Dropwizard → Java 21/Spring Boot 3."** That target is
not reachable through the factory's transform catalogue. The 17 available definitions include
`AWS/spring-boot-version-upgrade` — which upgrades a service *already* on Spring Boot — and
nothing that migrates *to* Spring Boot from Dropwizard. Nothing in the codebase is
Dropwizard-aware.

So a run instructed `AWS/java-version-upgrade` will never produce Spring Boot, no matter how
the design document describes the target. That is precisely what happened: PR #2 on
`atx-modernized` is a competent Java 17→21 + Dropwizard 2→4 + Jakarta upgrade, and it is
*correct output for the instruction it was given*. The mismatch was between the story and the
instruction, and it went unnoticed because no checked-in specification existed to compare
them against.

**Dropwizard 4.0.7 is a real modernisation, not a consolation prize.** It is the first
Dropwizard line on `jakarta.*`, which is the actual blocker for running on current Jetty and
current Java. It removes the EOL 2.x dependency, and it is achievable and repeatable by the
factory today.

**Spring Boot remains in scope as a separate, honestly-labelled unit** (U5) executed by the
harness rather than a canned transform, with a materially higher failure probability. It is
not claimed as factory automation.

## D3. Module design

Package layout is unchanged — the transform is a version and namespace migration, not a
restructure. Per-file intent:

| File | Change | Risk |
|---|---|---|
| `pom.xml` | version properties, remove jdbi3, add Micrometer + Prometheus, add test deps, compiler → 21 | low |
| `api/EntitlementResource.java` | `javax.ws.rs.*` → `jakarta.ws.rs.*` | low — 7 imports, done in PR #2 |
| `CatalogEntitlementConfiguration.java` | `javax.validation.*` → `jakarta.validation.*` | low |
| `CatalogEntitlementApplication.java` | Dropwizard 4 bootstrap; register health check + metrics | **medium** |
| `core/EntitlementService.java` | Resilience4j 1.x → 2.x API; add per-reason counters | **medium — highest risk in the change** |
| `db/EntitlementDao.java` | jOOQ 3.19 compatibility | low |
| `core/EntitlementDecision.java`, `core/Title.java` | none — plain records | none |
| `cache/`, `geo/`, `storage/`, `messaging/` | none | none |

**Why `EntitlementService` is the highest risk.** It holds every R1 invariant *and* needs the
Resilience4j 2.x migration *and* gains the R5.3 counters. Resilience4j 2.x changes package
names and builder signatures, so this file cannot be mechanically rewritten — and the
decoration order (R3.3) is easy to invert while "modernising" the call. An inverted order
still compiles, still passes a naive happy-path test, and opens the breaker roughly three
times sooner than intended under load. It is therefore the one file where a named ordering
assertion is mandatory (see U4).

## D4. Observability design (R5)

Adds Micrometer with a Prometheus registry, exposed on the existing admin connector (8081)
at `/prometheus`. Business traffic stays on 8080, so the scrape surface is not internet-facing.

Counters, named for signal derivation rather than for dashboards:

```
entitlement_decisions_total{outcome="allowed"}
entitlement_decisions_total{outcome="denied", reason="TITLE_NOT_IN_CATALOG"}
entitlement_decisions_total{outcome="denied", reason="REGION_BLOCKED"}
entitlement_decisions_total{outcome="denied", reason="DEVICE_NOT_ALLOWED"}
entitlement_decisions_total{outcome="denied", reason="NO_ACTIVE_ENTITLEMENT"}
entitlement_decision_duration_seconds        (histogram)
entitlement_db_circuit_state                 (0 closed / 1 open / 2 half-open)
```

`REGION_BLOCKED` is deliberately **not** labelled with the country. The reason string is
`REGION_BLOCKED:<country>` and using it raw as a label value makes country a high-cardinality
dimension — roughly 250 series that grows with every unresolvable-IP variant. The country is
already in the response and the Kafka event; it does not need to be in the metric.

`entitlement_db_circuit_state` is what makes R3 observable. Without it, an open breaker is
indistinguishable from a healthy service that happens to be denying — the same 403s either way.

## D5. What Day-2 consumes

The seam between this workstream and DevOps-agent onboarding. Each signal the agent can
derive, and the metric it derives it from:

| Observation | Derived signal | Tier |
|---|---|---|
| `NO_ACTIVE_ENTITLEMENT` rate step-change | entitlement data drift | low |
| `REGION_BLOCKED` rate spike | GeoIP database stale or unreadable | low |
| `entitlement_db_circuit_state` = open | database dependency degraded | **high** |
| p99 `decision_duration` regression after deploy | performance regression | low |
| Dependency CVE / EOL from build metadata | modernisation trigger | low |

Only the circuit-breaker signal is high-tier, because it is the only one whose remediation
touches a shared production database. The rest are safely auto-actionable — which is what
makes the flywheel demonstrable rather than theoretical.

## D6. Rejected alternatives

**Rewrite as Spring Boot from scratch.** Discards the invariants in R1 that exist only as
code today. With no test suite (R6.1), a rewrite has nothing to check itself against — the
regression would be found in production.

**Stay on Dropwizard 2.x and upgrade only Java.** Java 21 on Dropwizard 2.1.10 leaves the
service on `javax.*` and an EOL framework, and it fails R5 — Dropwizard 2's metrics story
has no Prometheus path without the same Micrometer work. It defers the problem at the cost
of the Day-2 story.

**Micrometer straight to CloudWatch, skipping Prometheus.** Couples the application to one
monitoring vendor. LGI runs Grafana and Dynatrace. A Prometheus endpoint is scrapable by all
three, which is what keeps the adapter boundary in the monitoring tools honest.
