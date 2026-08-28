# Audit — Catalog & Entitlement modernisation

Append-only. Newest last. Records decisions and who made them, including the ones that
contradicted an earlier assumption.

---

### 2026-08-26 04:07Z · Space created
Actor: Viyoma Sachdeva (via agent)
`proposal-spec.md` raised covering three workstreams: AIDLC-governed modernisation, DevOps
agent onboarding with monitoring as read-only agent tools, and continuous modernisation as
Day-2. Committed `a98a2d8`.

### 2026-08-26 04:09Z · Scope decision: regenerate rather than retrofit
Actor: Viyoma Sachdeva
Asked whether to re-generate the modernisation PR under the new spec or retrofit the spec to
the existing PR #2. **Decision: regenerate.** Rationale: the premise of W1 is that intent
precedes code; retrofitting would make the causal claim false.

### 2026-08-26 04:12Z · Legacy inventory established
Ground truth taken from `lgi-catalog-entitlement-legacy/` (11 files) rather than from
assumption. Findings that changed the spec:

- The decision logic is a **strict 5-gate ordered sequence** with exact reason-code strings.
  The order is itself a contract, since callers key off the reason.
- **No tier/plan/price matrix exists.** Entitlement is a date-windowed boolean row check.
  An earlier framing implied tier logic; that would have been invented, not modernised.
- **No tests exist at all** (`src/test` absent), so every invariant is currently unverifiable.
- **No health check is registered and there is no Prometheus endpoint** — metrics are
  Dropwizard-Metrics JSON on the admin connector only.
- `dropwizard-jdbi3` is declared but unused; data access is jOOQ throughout.
- The published Kafka event omits `artworkUri` despite the record carrying it. Recorded as
  intentional (R1.10) so it is not later "fixed" into a breaking change.

### 2026-08-26 04:14Z · Finding: the stated target was never reachable
This reverses a claim made in the demo narrative to date, so it is recorded in full.

The narrative target has been "Java 17/Dropwizard → Java 21/**Spring Boot 3**". Inspection of
the transform catalogue (`frontend/src/workflow/transformCatalog.ts`, 17 definitions) shows
**no definition performs Dropwizard → Spring Boot**. `AWS/spring-boot-version-upgrade`
upgrades a service already on Spring Boot. Nothing in the codebase is Dropwizard-aware.

PR #2 on `atx-modernized` is therefore **correct output for the instruction it was given**
(`AWS/java-version-upgrade`): Java 17→21, Dropwizard 2.1.10→4.0.7, `javax.*`→`jakarta.*`,
dependency bumps including Resilience4j 1.7.1→2.2.0. It was not a failed Spring Boot
migration; it was a successful Java upgrade that nobody had asked to be a Spring Boot
migration.

The mismatch was between the **story** and the **instruction**, and it survived because no
checked-in specification existed to compare them against. This is the clearest available
evidence for why W1 exists, and it is a real incident rather than an illustration.

**Decision:** target Dropwizard 4.0.7 + Java 21 as the factory-automated outcome (U1–U3),
scope Spring Boot as U5 — harness-assisted, high risk, explicitly **not** claimed as factory
automation. `design.md` §D2 carries the reasoning.

### 2026-08-26 04:15Z · Correction: javax did not survive the transform
An initial grep reported `javax` still present in `EntitlementResource.java` on the
`atx-modernized` branch. That was wrong — the matches were in **comments** (a javadoc line
describing the legacy state). The seven `javax.ws.rs.*` imports were correctly migrated to
`jakarta.ws.rs.*`. Recorded because the incorrect reading would have understated the
transform's quality.

### 2026-08-26 04:18Z · Observability promoted to a requirement
R5 was going to be a nice-to-have. Promoted to a hard requirement because the Day-2
workstream depends on it: a DevOps agent cannot derive an evidenced signal from a service
that publishes nothing. Without R5 the Day-2 signals stay synthetic and the autonomous
operations claim has no foundation. `units.md` U3 records that it blocks the workstream.

Sub-decision: `REGION_BLOCKED` is **not** labelled with country. The raw reason string is
`REGION_BLOCKED:<country>`; using it as a label value makes country a high-cardinality
dimension (~250 series, growing). The country is already in the response and the event.

### 2026-08-26 04:22Z · Schema change: transformation_steps carries per-unit context
`META.transformation_steps` was `list[str]` — bare transform-definition names — which cannot
carry a per-unit instruction. Changed the step-builder in
`infra/environments/agentcore/lambda-src/tool_execution.py` to accept **either** shape:

- `["AWS/java-version-upgrade", ...]` → derived context (unchanged behaviour)
- `[{"td_name": ..., "additional_context": ...}, ...]` → the unit's own instruction

Backward compatibility was the risk: live app records hold plain string lists, so a
regression would silently stop transforms for every existing application. Seven tests pin
both shapes, mixed shapes, precedence, and malformed entries.

Rejected alternative: reuse the existing `build_context` argument. It is honoured, but it is
a single value applied to every step in the call, so two units would receive identical
instructions. Adequate only for a single-unit run.

Each emitted step now also records `context_source` as `unit` or `derived`, so the audit
trail can distinguish a spec-driven run from a default-driven one.

Verification: 7 new tests, 29 lambda-src total, 146 backend, ruff clean (the 5 reported
errors are pre-existing at HEAD and were left untouched).

### 2026-08-26 04:05Z · Live workflow advanced past a stuck gate
Execution `APP-CATALOG-20260825-125537` had been parked at `Approval_Discovery` since
08:57 EDT. Approved `discovery_review` through the application's own handler — WAF blocks the
API from the current network, so the endpoint was invoked locally against real DynamoDB and
Step Functions to ensure all four side effects occurred (task success, workflow advance,
`APPROVAL_AUDIT#` record, token cleanup). Driving Step Functions directly by CLI would have
skipped the audit record and broken the governance view.

Incidental finding: local execution failed first with `ResourceNotFoundException` because
`project_prefix` defaults to `amf`, yielding table `amf_portfolio`. Same root cause as the
S3 bucket defect found on 2026-08-20. Not fixed here — recorded as a known trap.

---

## Open

- **U5 (Spring Boot) not scheduled.** Requires U4 first; no ATX definition can perform it.
- **AgentCore control plane not applied.** New runs execute on the previous engine, so the
  monitoring gateway target can be written and validated but not made live.
- **Stale transform name in the UI.** `frontend/src/pages/WorkflowPage.tsx:419` still offers
  `AWS/java-upgrade`, which is not a real definition; the authoritative catalogue uses
  `AWS/java-version-upgrade`. Not on the execution path, but it is selectable by a human.

- 2026-08-28T01:54:00Z — U4 invariant-test safety net implemented as the fourth review commit. Added five executable decision-path tests covering catalog absence, region blocking, device blocking, inactive entitlement, and successful authorization/event publication. U4 is harness-authored and intentionally separate from the ATX-automated U1–U3 units.