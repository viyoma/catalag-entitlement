# Units — Catalog & Entitlement modernisation

Space: `catalog-entitlement` · App: `APP-CATALOG` · Status: **proposed**
Implements `design.md`. **This file is executable input, not prose.**

---

## How this file drives the transform

Each unit's `additional_context` is the string the factory passes to the ATX CLI as
`--configuration "additionalPlanContext=<text>"`. The path is:

```
units.md  →  META.transformation_steps[]  →  start_code_transformation
          →  WORKBENCH_STEPS env var  →  workbench.py step.additional_context
          →  atx custom def exec -n <td> --configuration "additionalPlanContext=..."
```

**One schema change is required.** `META.transformation_steps` is `list[str]` today
(`backend/src/api/v1/workflow.py:818`, consumed at
`infra/environments/agentcore/lambda-src/tool_execution.py:85`) and a list of bare strings
cannot carry per-unit instructions. It must become `list[dict]` of
`{td_name, additional_context, target_version}`.

The worker needs no change — `workbench.py:97` already reads `step.get("additional_context")`
per step. The ECS contract needs no change. The ATX command needs no change. Only the field
type and the step-builder loop at `tool_execution.py:107-127`, which today calls
`_plan_context(td)` to synthesise a canned sentence and would instead read the unit's own.

**Do not use the existing `build_context` argument for this.** It is honoured by
`_plan_context`, but it is a single value applied to every step in the call, so it cannot
express different instructions per unit. It is adequate only when a run contains exactly one
unit.

---

## U1 — Java 17 → 21 and Jakarta namespace

- **td_name:** `AWS/java-version-upgrade`
- **target_version:** `21`
- **Requirements:** R4.1, R4.2, R4.3, R4.4
- **Files:** `pom.xml`, `api/EntitlementResource.java`, `CatalogEntitlementConfiguration.java`
- **Proven:** yes — this is what PR #2 on `atx-modernized` already produced
- **Risk:** low

```
additional_context = "Upgrade this Dropwizard service from Java 17 to Java 21. Migrate the
javax.* namespace to jakarta.* — the affected imports are javax.ws.rs.* in
api/EntitlementResource.java and javax.validation.* in CatalogEntitlementConfiguration.java.
Upgrade Dropwizard from 2.1.10 to 4.0.7, which is the first Dropwizard line using the
jakarta namespace; do not target Dropwizard 3.x. Remove the io.dropwizard:dropwizard-jdbi3
dependency, which is declared but never used because all data access is jOOQ. Do not alter
any decision logic in core/EntitlementService.java."
```

**Verify:** no `javax.` imports remain in any `.java` file (comments may still reference it);
`pom.xml` declares Java 21 and Dropwizard 4.0.7; `dropwizard-jdbi3` absent; project compiles.

## U2 — Dependency currency

- **td_name:** `AWS/java-version-upgrade`
- **Requirements:** R4.4
- **Files:** `pom.xml`, `db/EntitlementDao.java`, `core/EntitlementService.java`
- **Risk:** medium — Resilience4j 2.x is a major bump with renamed packages

```
additional_context = "Upgrade dependencies to current versions: jOOQ 3.18.7 to 3.19.6,
Hazelcast 5.3.6 to 5.4.0, Kafka clients 3.6.1 to 3.9.2, PostgreSQL driver 42.7.1 to 42.7.12,
and Resilience4j 1.7.1 to 2.2.0. Resilience4j 2.x renames packages and changes builder
signatures, so update core/EntitlementService.java accordingly. Preserve the exact resilience
configuration: circuit breaker with failureRateThreshold 50, waitDurationInOpenState 10
seconds, slidingWindowSize 20; retry with maxAttempts 3 and waitDuration 200ms. Preserve the
decoration order exactly as Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(
breaker, dbCall)) — the retry must wrap the circuit breaker, not the reverse, because
inverting it makes the breaker count each retry as a separate failure."
```

**Verify:** all versions match; the four resilience values are unchanged; decoration order
asserted by the U4 test, not by reading the diff.

## U3 — Observability for Day-2

- **td_name:** `AWS/java-version-upgrade` (additive change, no dedicated definition exists)
- **Requirements:** R5.1, R5.2, R5.3
- **Files:** `pom.xml`, `CatalogEntitlementApplication.java`, `core/EntitlementService.java`
- **Risk:** medium — new code rather than a mechanical migration
- **Blocks:** the entire Day-2 workstream. Without this unit there is nothing to scrape and
  signals stay synthetic.

```
additional_context = "Add observability to this Dropwizard 4 service. Add Micrometer with a
Prometheus registry and expose it on the existing admin connector (port 8081) at /prometheus;
do not expose it on the application connector (port 8080). Register a health check named
'database' that verifies PostgreSQL reachability — the service currently registers no health
checks at all. Add a counter entitlement_decisions_total with an 'outcome' label of allowed or
denied, and for denials a 'reason' label carrying the reason code family only:
TITLE_NOT_IN_CATALOG, REGION_BLOCKED, DEVICE_NOT_ALLOWED, or NO_ACTIVE_ENTITLEMENT. Do not
include the country in the reason label — the raw reason string is REGION_BLOCKED:<country>
and using it directly would make country a high-cardinality metric dimension. Add a histogram
entitlement_decision_duration_seconds around the decide() call, and a gauge
entitlement_db_circuit_state reporting 0 for closed, 1 for open, 2 for half-open. Do not
change any decision outcome or reason string."
```

**Verify:** `/prometheus` on 8081 returns all seven series; nothing added to 8080; the
`database` health check appears in `/healthcheck`; no reason label contains a country code.

## U4 — Invariant test suite

- **td_name:** none — authored by the harness, not a transform definition
- **Requirements:** R6.1, R6.2, R6.3
- **Files:** `src/test/java/com/lgi/catalog/` (new tree — none exists)
- **Risk:** low to write, **high in value**

This unit is what makes the other units verifiable. It is deliberately ordered after the
transform units and before any gate approval, because its purpose is to *contradict a false
success claim*. On 2026-08-20 a transform reported SUCCESS with an empty step list and
nothing disagreed with it.

Required tests, one per invariant, named for the requirement:

| Test | Asserts |
|---|---|
| `titleNotInCatalogue_denies_andNeverReachesDatabase` | R1.1 + R6.3 short-circuit |
| `countryNotAllowed_denies_withCountryInReason` | R1.2 |
| `unresolvableIp_deniesAsZZ` | R1.7 fail-closed |
| `deviceNotAllowed_denies` | R1.3 |
| `emptyDeviceSet_allowsAnyDevice` | R1.6 — the inversion trap |
| `noActiveEntitlement_denies` | R1.4 |
| `expiredEntitlement_denies` | R1.8 date window |
| `allowPath_publishesToKafkaExactlyOnce` | R1.9 |
| `deniedPath_publishesNothing` | R1.9 negative |
| `publishedEvent_omitsArtworkUri` | R1.10 intentional omission |
| `retryWrapsCircuitBreaker_notReverse` | R3.3 — the ordering trap |
| `response_200OnAllow_403OnDeny_bodyInBoth` | R2.2 |
| `responseSchema_fieldNamesAndTypesUnchanged` | R2.3 |

**Verify:** every test present and passing. A missing test is a failed unit, not a gap to
revisit — an unverifiable unit is not a unit.

## U5 — Spring Boot migration (NOT factory-automated)

- **td_name:** none — **no ATX definition can perform Dropwizard → Spring Boot**
- **Requirements:** the original narrative target; see `design.md` §D2
- **Risk:** **high**
- **Status:** scoped, not scheduled

Recorded as a unit so the gap is visible rather than implied. The transform catalogue offers
`AWS/spring-boot-version-upgrade`, which upgrades a service already on Spring Boot, and no
path from Dropwizard. Executing U5 means harness-authored migration: Jersey resources →
Spring MVC controllers, Dropwizard YAML → Spring profiles, hand-wired `run()` → Spring DI,
Dropwizard health checks → Actuator.

**Depends on U4.** Attempting a framework migration before the invariant suite exists means
changing every layer at once with nothing to verify against. With U4 in place the migration
is checkable; without it, it is a rewrite hoping for the best.

**Do not claim U5 as factory automation in any demo.** The honest framing is that the factory
automates U1–U3, evidences and verifies via U4, and that U5 is agent-assisted work a human
reviews closely.

---

## Execution order

```
U1 ──→ U2 ──→ U3 ──→ U4 ──→ [gate: human reviews spec vs diff] ──→ U5 (optional, high risk)
```

U1 before U2 because Resilience4j 2.x expects the Jakarta baseline. U3 after U2 so Micrometer
is added once, against final dependency versions. U4 last among the automated units because
it must test the finished state — and before the gate, so the reviewer sees assertions rather
than assurances.

## What the reviewer is asked at the gate

Not "does this diff look right?" but: **does the diff implement `requirements.md`, and does
the U4 suite prove it?** Those are answerable questions. The first is what the gate asked
before this spec existed, and it is why a half-completed transform was approved as SUCCESS.
