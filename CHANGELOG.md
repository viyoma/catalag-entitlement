# APP-CATALOG Modernization Changelog

This changelog maps each AIDLC delivery unit to one reviewable commit. Every entry explains the technical change, customer impact, delivery mode, and verification evidence.

## U3 — Observability uplift

- **Why:** The service lacked sufficient operational evidence for decision outcomes, request latency, database reachability, and resilience state.
- **What changed:**
  - Added Micrometer with a Prometheus registry.
  - Exposed `/prometheus` on the admin connector only, not the customer-facing application connector.
  - Registered a PostgreSQL database health check.
  - Added allowed/denied decision counters with bounded reason-family labels.
  - Added an entitlement-decision latency histogram.
  - Added a circuit-breaker state gauge for closed, open, and half-open states.
- **User impact:** Entitlement outcomes remain unchanged. Operators gain health, performance, decision, and resilience visibility for Day-2 support.
- **Delivery mode:** AWS Transform automation using AIDLC U3 context.
- **Verification:** Java 21 package verification plus inspection of metric names, labels, health registration, and admin-port exposure.

## U2 — Dependency modernisation

- **Why:** The service dependency graph contained aging versions and a Resilience4j major-version compatibility boundary.
- **What changed:**
  - Upgraded jOOQ 3.18.7 to 3.19.6.
  - Upgraded Hazelcast 5.3.6 to 5.4.0.
  - Upgraded Kafka clients 3.6.1 to 3.9.2.
  - Upgraded PostgreSQL driver 42.7.1 to 42.7.12.
  - Upgraded Resilience4j 1.7.1 to 2.2.0.
  - Preserved the circuit-breaker thresholds, retry timing, and retry-around-circuit-breaker decoration order.
- **User impact:** No entitlement behavior changed. Supported library versions reduce maintenance and security exposure while retaining existing failure-handling semantics.
- **Delivery mode:** AWS Transform automation using AIDLC U2 context.
- **Verification:** Java 21 compilation, dependency resolution, and Maven package verification.

## U1 — Runtime and framework uplift

- **Why:** Java 17, Dropwizard 2 and `javax.*` were below the approved modernization baseline.
- **What changed:**
  - Upgraded Java 17 to Java 21.
  - Upgraded Dropwizard 2.1.10 to 4.0.7.
  - Migrated JAX-RS and validation imports from `javax.*` to `jakarta.*`.
  - Removed the unused `dropwizard-jdbi3` dependency and added `dropwizard-db`.
  - Added Java 21 Maven and Docker build packaging.
  - Checked in the AIDLC proposal, requirements, design, units, and audit evidence.
- **User impact:** No entitlement decision logic or API outcome changed. The service moves onto a supported runtime and framework baseline.
- **Delivery mode:** AWS Transform automation using AIDLC U1 context.
- **Verification:** Java 21 compilation and Maven package verification.
