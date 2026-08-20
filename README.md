# LGI Catalog & Entitlement Service — CURRENT STATE (legacy)

Sample "before" codebase for the **AWS Modernisation Factory / AWS Transform** demo.

It answers one question at runtime:
> *"Is this subscriber allowed to watch this title, in this region, on this device?"*

This repo is intentionally built on the legacy stack from the LGI estate so AWS
Transform has real, representative code to modernize live.

## Legacy stack (what Transform ingests)

| Concern        | Current (this repo)                        | AWS Transform target                     |
|----------------|--------------------------------------------|------------------------------------------|
| Language       | Java 17                                    | Java 21                                  |
| Framework      | Dropwizard 2.x, JAX-RS (`javax.ws.rs`)     | Spring Boot 3.x, `@RestController` (jakarta) |
| Data access    | jOOQ over self-managed PostgreSQL          | Spring Data → Amazon Aurora PostgreSQL   |
| Cache          | Hazelcast IMap (simulated Hollow catalog)  | Amazon ElastiCache for Redis             |
| Object storage | NFS filesystem mount                       | Amazon S3                                |
| Messaging      | Raw Kafka client, hand-rolled JSON         | Amazon MSK, CloudEvents envelope         |
| Resilience     | Resilience4j (programmatic config)         | Resilience4j (Spring Boot starter) — retained |
| Geo            | MaxMind GeoIP (`.mmdb` on NFS)             | MaxMind (`.mmdb` from S3) — retained     |
| Packaging      | fat JAR, run on a VM                        | Container on Amazon EKS, Helm + ArgoCD   |
| Observability  | Prometheus/Kraken metrics                  | OpenTelemetry → CloudWatch / Grafana / Loki |
| Operations     | human on-call (paged)                      | autonomous ops agent (self-heal/scale/rollback) |

## Layout

```
src/main/java/com/lgi/catalog/
├── CatalogEntitlementApplication.java   # Dropwizard entrypoint, hand-wired deps
├── CatalogEntitlementConfiguration.java # config.yml binding
├── api/EntitlementResource.java         # JAX-RS endpoint  GET /entitlement/{titleId}
├── core/
│   ├── EntitlementService.java          # decision logic + Resilience4j
│   ├── Title.java                       # catalog title (record)
│   └── EntitlementDecision.java         # decision + Kafka payload (record)
├── cache/HollowCatalogCache.java        # Hazelcast IMap
├── db/EntitlementDao.java               # jOOQ query
├── geo/GeoIpService.java                # MaxMind lookup
├── messaging/PlaybackEventProducer.java # raw Kafka producer
└── storage/ArtworkStore.java            # NFS filesystem access
```

## Build & run

```bash
mvn clean package
java -jar target/catalog-entitlement-service.jar server config.yml
```

Endpoint:
```
GET http://localhost:8080/entitlement/{titleId}?subscriberId=...&clientIp=...&deviceType=...
```

## Demo flow

1. Show this legacy service (framework, javax.*, jOOQ, NFS, raw Kafka).
2. Run AWS Transform → Java 21 + Spring Boot 3.x, Spring Data, Redis, S3, MSK/CloudEvents.
3. Show the containerized service on Amazon EKS (Helm + ArgoCD).
4. Hand operations to the autonomous agent (lights-out Ops).

> NOTE: `demo` scaffold — external endpoints in `config.yml` (DB/Kafka/NFS hosts)
> are illustrative placeholders. It compiles and packages; wire to live infra
> only in a controlled demo environment.
