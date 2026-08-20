# APP-CATALOG — Modernization (Legacy → Target)

Legacy **Java 17 / Dropwizard 4** on VMs → **Java 21 / Spring Boot 3** on **Amazon EKS**.
Business logic (the 5-step entitlement decision) is **unchanged**; the modernization is
structural (framework, data, platform) — exactly the class of change AWS Transform automates.

## Component mapping

| Concern | Legacy (current) | Target (modernized) | Change type |
|---|---|---|---|
| Web framework | Dropwizard/Jersey **JAX-RS** (`javax.ws.rs`) | **Spring Boot 3** `@RestController` (jakarta) | Framework migration |
| DI / wiring | Manual constructor wiring in `Application.run()` | Spring component scan + constructor injection | Framework migration |
| HTTP types | `javax.ws.rs.core.Response` | `org.springframework.http.ResponseEntity` | API rewrite |
| Data access | jOOQ + self-managed PostgreSQL | **Spring Data JDBC** + **Aurora PostgreSQL** | Data modernization |
| Cache | Hazelcast near-cache (in-JVM) | **Amazon ElastiCache (Redis)** | Managed service |
| Messaging | Raw Kafka producer | **Spring Kafka** on **Amazon MSK** (IAM auth) | Managed service |
| Resilience | Resilience4j (programmatic) | Resilience4j **retained**, declarative `@CircuitBreaker/@Retry` + `application.yml` | Reconfigure |
| Artwork store | NFS mount | **Amazon S3** (SDK v2) | Storage modernization |
| Config | `config.yml` (Dropwizard) | `application.yml` (Spring) + Secrets Manager via IRSA | Config migration |
| Observability | Ad-hoc metrics | **OpenTelemetry + Micrometer → CloudWatch** | Observability uplift |
| Compute | VM | **Amazon EKS** (Graviton), multi-AZ, HPA | Replatform |

## Preserved business rules (unchanged, traceable)
- **BR-ENT-01** default-deny: the modernized `hasActiveEntitlement` fallback returns `false`
  (fail-safe) when the entitlement store is unavailable — see `EntitlementService.entitlementUnavailable`.
- **BR-GEO-02** regional licensing, **BR-DEV-03** device limits, **BR-CONTENT-07** windowing,
  and the Kafka playback-authorization event per decision are all retained.

## What AWS Transform / AMF automates here
- `javax.* → jakarta.*` and JAX-RS → Spring MVC annotation rewrite
- Dropwizard bootstrap → `@SpringBootApplication`
- Programmatic Resilience4j → declarative annotations + `application.yml`
- Dependency/build migration (Dropwizard fat-jar → Spring Boot Maven plugin, Java 17 → 21)
- Human approves each stage; modernized code lands on branch `migration/APP-CATALOG`.

> This branch is the **target-state reference** for the demo — a faithful, reviewable diff of
> the modernization the factory drives, on the same repo where the automated transform lands.
